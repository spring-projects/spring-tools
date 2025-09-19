/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.FileSystem.Classpath;
import org.eclipse.jdt.internal.core.BasicCompilationUnit;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.INameEnvironmentWithProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.UncheckedExecutionException;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public final class CompilationUnitCache implements DocumentContentProvider {

	private static final Logger logger = LoggerFactory.getLogger(CompilationUnitCache.class);

	private static final long CU_ACCESS_EXPIRATION = 1;
	private final JavaProjectFinder projectFinder;
	private final ProjectObserver projectObserver;
	
	private final ProjectObserver.Listener projectListener;
	private final SimpleTextDocumentService documentService;

	private final Cache<URI, CompletableFuture<CompilationUnit>> uriToCu;
	private final Cache<URI, Set<URI>> projectToDocs;
	private final Cache<URI, Tuple2<List<Classpath>, INameEnvironmentWithProgress>> lookupEnvCache;
	private final Cache<URI, AnnotationHierarchies> annotationHierarchies;
	
	private final ReentrantReadWriteLock environmentCacheLock = new ReentrantReadWriteLock(true);
	private CompletableFuture<Void> debounceClassFileChanges = CompletableFuture.completedFuture(null);
	
	private final Executor createCuExecutorThreadPool = Executors.newCachedThreadPool();

	public CompilationUnitCache(JavaProjectFinder projectFinder, SimpleLanguageServer server, ProjectObserver projectObserver) {
		this.projectFinder = projectFinder;
		this.projectObserver = projectObserver;
		
		// PT 154618835 - Avoid retaining the CU in the cache as it consumes memory if it hasn't been
		// accessed after some time
		this.uriToCu = CacheBuilder.newBuilder()
				.expireAfterWrite(CU_ACCESS_EXPIRATION, TimeUnit.MINUTES)
				.removalListener(new RemovalListener<URI, CompletableFuture<CompilationUnit>>() {

					@Override
					public void onRemoval(RemovalNotification<URI, CompletableFuture<CompilationUnit>> notification) {
						URI uri = notification.getKey();
						CompletableFuture<CompilationUnit> future = notification.getValue();
						
						if (future != null) {
							if (!future.isCancelled()) {
								logger.debug("cancel jdt cu cache for: " + uri);
								future.cancel(true);
							}
						}
					}
				})
				.build();
		
		this.projectToDocs = CacheBuilder.newBuilder().build();
		this.lookupEnvCache = CacheBuilder.newBuilder().removalListener(new RemovalListener<URI, Tuple2<List<Classpath>, INameEnvironmentWithProgress>>() {
			@Override
			public void onRemoval(RemovalNotification<URI, Tuple2<List<Classpath>, INameEnvironmentWithProgress>> notification) {
				logger.info("Removing and cleaning up name env for project {}", notification.getKey());
				notification.getValue().getT2().cleanup();
			}
			
		}).build();
		
		this.annotationHierarchies = CacheBuilder.newBuilder().build();

		this.documentService = server == null ? null : server.getTextDocumentService();

		// IMPORTANT ===> these notifications arrive within the lsp message loop, so reactions to them have to be fast
		// and not be blocked by waiting for anything
		if (this.documentService != null) {
			this.documentService.onDidChangeContent(doc -> invalidateCuForJavaFile(doc.getDocument().getId().getUri()));
			this.documentService.onDidClose(doc -> invalidateCuForJavaFile(doc.getId().getUri()));
		}

		if (this.projectFinder != null) {
			for (IJavaProject project : this.projectFinder.all()) {
				logger.debug("CU Cache: initial lookup env creation for project <{}>", project.getElementName());
				loadLookupEnvTuple(project);
			}
		}

		this.projectListener = new ProjectObserver.Listener() {
			
			@Override
			public void deleted(IJavaProject project) {
				logger.debug("CU Cache: deleted project {}", project.getElementName());
				invalidateProject(project);
			}
			
			@Override
			public void created(IJavaProject project) {
				logger.debug("CU Cache: created project {}", project.getElementName());
				invalidateProject(project);
			}
			
			@Override
			public void changed(IJavaProject project) {
				logger.debug("CU Cache: changed project {}", project.getElementName());
				invalidateProject(project);
			}
		};

		if (this.projectObserver != null) {
			this.projectObserver.addListener(this.projectListener);
		}
		
		if (server != null) {
			ServerUtils.listenToClassFileCreateAndChange(server.getWorkspaceService().getFileObserver(), projectFinder, jp -> {
				if (!debounceClassFileChanges.isDone()) {
					debounceClassFileChanges.cancel(false);
				}
				debounceClassFileChanges = CompletableFuture.runAsync(() -> invalidateProject(jp), CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
			});
		}
		
	}

	public void dispose() {
		if (this.projectObserver != null) {
			this.projectObserver.removeListener(this.projectListener);
		}
	}

	/**
	 * Never research shows at the AST is thread-safe when used in read-only mode:
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=58314
	 * 
	 * This means that the previous implemented synchronization around the requestor
	 * working on the AST is not necessary as long as the requestor operates in read-only
	 * mode on the AST nodes.
	 * 
	 * Warning: Callers should take care to do all AST processing inside of the requestor callback and
	 * not pass of AST nodes to helper functions that work aynchronously or store AST nodes or ITypeBindings
	 * for later use. The JDT ASTs are not thread safe!
	 */
	@Deprecated
	public <T> T withCompilationUnit(TextDocument document, Function<CompilationUnit, T> requestor) {
		IJavaProject project = this.projectFinder != null ? projectFinder.find(document.getId()).orElse(null) : null;

		URI uri = URI.create(document.getUri());
		return withCompilationUnit(project, uri, requestor);
	}

	/**
	 * Never research shows at the AST is thread-safe when used in read-only mode:
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=58314
	 * 
	 * This means that the previous implemented synchronization around the requestor
	 * working on the AST is not necessary as long as the requestor operates in read-only
	 * mode on the AST nodes.
	 * 
	 * Warning: Callers should take care to do all AST processing inside of the requestor callback and
	 * not pass of AST nodes to helper functions that work aynchronously or store AST nodes or ITypeBindings
	 * for later use. The JDT ASTs are not thread safe!
	 */
	public <T> T withCompilationUnit(IJavaProject project, URI uri, Function<CompilationUnit, T> requestor) {
		logger.info("CU Cache: work item submitted for doc {}", uri.toASCIIString());

		if (project != null) {
			try {
				CompilationUnit cu = null;
				try {
					cu = requestCU(project, uri).get();
				} catch (UncheckedExecutionException e1) {
					// ignore errors from rewrite parser. There could be many parser exceptions due to
					// user incrementally typing code's text
					return null;
				} catch (InvalidCacheLoadException | CancellationException e) {
					// ignore
				} catch (Exception e) {
					logger.error("", e);
					return requestor.apply(null);
				}
	
				if (cu != null) {
					ReadLock lock = environmentCacheLock.readLock();
					lock.lock();
					try {
						logger.info("CU Cache: start work on AST for {}", uri.toString());
						return requestor.apply(cu);
					} finally {
						logger.info("CU Cache: end work on AST for {}", uri.toString());
						lock.unlock();
					}

				}
			}
			catch (CancellationException e) {
				throw e;
			}
			catch (Exception e) {
				logger.error("", e);
			}
		}

		return requestor.apply(null);
	}
	
	private synchronized CompletableFuture<CompilationUnit> requestCU(IJavaProject project, URI uri) {
		CompletableFuture<CompilationUnit> cuFuture = uriToCu.getIfPresent(uri);
		if (cuFuture == null) {
			cuFuture = CompletableFuture.supplyAsync(() -> {
				ReadLock lock = environmentCacheLock.readLock();
				lock.lock();
				logger.info("Started parsing CU for " + uri);
				
				try {
					Tuple2<List<Classpath>, INameEnvironmentWithProgress> lookupEnvTuple = loadLookupEnvTuple(project);
					String uriStr = uri.toASCIIString();
					String unitName = uriStr.substring(uriStr.lastIndexOf("/") + 1); // skip over '/'
					CompilationUnit cUnit = parse2(fetchContent(uri).toCharArray(), uriStr, unitName, lookupEnvTuple.getT1(), lookupEnvTuple.getT2(),
							annotationHierarchies.get(project.getLocationUri(), AnnotationHierarchies::new));
					logger.debug("CU Cache: created new AST for {}", uri.toASCIIString());
					logger.info("Parsed successfully CU for " + uri);
					return cUnit;
				} catch (Throwable t) {
					// Complete future exceptionally
					throw new CompletionException(t);
				} finally {
					logger.info("Finished parsing CU for {}", uri);
					lock.unlock();
				}
			}, createCuExecutorThreadPool);
			// Cache the future
			uriToCu.put(uri, cuFuture);
			// If CU future completed exceptionally invalidate the cache entry
			cuFuture
				.thenAccept(cu -> {
					synchronized(CompilationUnitCache.this) {
						try {
							projectToDocs.get(project.getLocationUri(), () -> new HashSet<>()).add(uri);
						} catch (ExecutionException e) {
							// shouldn't happen
						}
					}
				})
				.exceptionally(t -> {
					if (!(t instanceof CancellationException)) {
						logger.error("", t);
					}
					synchronized(CompilationUnitCache.this) {
						uriToCu.invalidate(uri);
					}
					return null;
				});
		}
		return cuFuture;
	}

	public static CompilationUnit parse2(char[] source, String docURI, String unitName, IJavaProject project) throws Exception {
		List<Classpath> classpaths = createClasspath(getClasspathEntries(project));
		return parse2(source, docURI, unitName, classpaths, null, null);
	}
	
	private static CompilationUnit parse2(char[] source, String docURI, String unitName, List<Classpath> classpaths, INameEnvironmentWithProgress environment, AnnotationHierarchies annotations) throws Exception {
		Map<String, String> options = JavaCore.getOptions();
		String apiLevel = JavaCore.VERSION_21;
		JavaCore.setComplianceOptions(apiLevel, options);
		if (environment == null) {
			environment = CUResolver.createLookupEnvironment(classpaths.toArray(new Classpath[classpaths.size()]));
		}
		if (annotations == null) {
			annotations = new AnnotationHierarchies();
		}
		
		BasicCompilationUnit sourceUnit = new BasicCompilationUnit(source, null, unitName, (IJavaElement) null);
		
		int flags = 0;
		boolean needToResolveBindings = true;
		flags |= ICompilationUnit.ENABLE_STATEMENTS_RECOVERY;
		flags |= ICompilationUnit.ENABLE_BINDINGS_RECOVERY;
		CompilationUnitDeclaration unit = null;
		try {
			unit = CUResolver.resolve(sourceUnit, classpaths, options, flags, environment);
		} catch (Exception e) {
			flags &= ~ICompilationUnit.ENABLE_BINDINGS_RECOVERY;
			unit = CUResolver.parse(sourceUnit, options, flags);
			needToResolveBindings = false;
		}
		
		CompilationUnit cu = CUResolver.convert(unit, source, AST.JLS24, options, needToResolveBindings, DefaultWorkingCopyOwner.PRIMARY, flags);

		AnnotationHierarchies.set(cu, annotations);
		return cu;
	}
	
	private static List<Classpath> createClasspath(String[] classpathEntries) {
		ASTParser parser = ASTParser.newParser(AST.JLS24);
		String[] sourceEntries = new String[] {};
		parser.setEnvironment(classpathEntries, sourceEntries, null, false);
		return CUResolver.getClasspath(parser);
	}
	
	private Tuple2<List<Classpath>, INameEnvironmentWithProgress> loadLookupEnvTuple(IJavaProject project) {
		try {
			return lookupEnvCache.get(project.getLocationUri(), () -> {
				logger.info("Creating name env for project '{}'", project.getElementName());
				List<Classpath> classpaths = createClasspath(getClasspathEntries(project));
				INameEnvironmentWithProgress environment = CUResolver.createLookupEnvironment(classpaths.toArray(new Classpath[classpaths.size()]));
				return Tuples.of(classpaths, environment);
			});
		} catch (ExecutionException e) {
			logger.error("{}", e);
			return null;
		}
	}
	
	private static String[] getClasspathEntries(IJavaProject project) throws Exception {
		if (project == null) {
			return new String[0];
		} else {
			IClasspath classpath = project.getClasspath();
			Stream<File> classpathEntries = IClasspathUtil.getAllBinaryRoots(classpath).stream();
			return classpathEntries
					.filter(file -> file.exists())
					.map(file -> file.getAbsolutePath()).toArray(String[]::new);
		}
	}

	private synchronized void invalidateCuForJavaFile(String uriStr) {
		logger.info("Invalidate AST for {}", uriStr);

		URI uri = URI.create(uriStr);
		uriToCu.invalidate(uri);
	}

	private synchronized void invalidateProject(IJavaProject project) {
		Set<URI> docUris = projectToDocs.getIfPresent(project.getLocationUri());
		if (docUris != null) {
			uriToCu.invalidateAll(docUris);
			projectToDocs.invalidate(project.getLocationUri());
		}
		WriteLock lock = environmentCacheLock.writeLock();
		lock.lock();
		try {
			URI uri = project.getLocationUri();
			lookupEnvCache.invalidate(uri);
			annotationHierarchies.invalidate(uri);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String fetchContent(URI uri) throws Exception {
		if (documentService != null) {
			TextDocument document = documentService.getLatestSnapshot(uri.toASCIIString());
			if (document != null) {
				return document.get();
			}
		}
		return IOUtils.toString(uri, StandardCharsets.UTF_8);
	}

}
