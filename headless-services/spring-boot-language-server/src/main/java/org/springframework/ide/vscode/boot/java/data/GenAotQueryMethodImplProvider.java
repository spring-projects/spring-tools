/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.BadLocationException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public class GenAotQueryMethodImplProvider implements IJavaLocationLinksProvider {
	
	private static Logger log = LoggerFactory.getLogger(GenAotQueryMethodImplProvider.class);
	
	public static final String CMD_NAVIGATE_TO_IMPL = "sts/boot/open-data-query-method-aot-definition";
	
	private final CompilationUnitCache cuCache;
	private final SimpleTextDocumentService docService;
	private final JavaProjectFinder projectFinder;
	
	public GenAotQueryMethodImplProvider(SimpleLanguageServer server, CompilationUnitCache cuCache, JavaProjectFinder projectFinder) {
		this.cuCache = cuCache;
		this.docService = server.getTextDocumentService();
		this.projectFinder = projectFinder;
		registerCommands(server);
	}

	@Override
	public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project,
			TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		if (n instanceof SimpleName && n.getParent() instanceof MethodDeclaration md) {
			Version version = SpringProjectUtil.getDependencyVersion(project, "spring-data-jpa");
			if (version != null && version.getMajor() >= 4) {
				IMethodBinding methodBinding = md.resolveBinding();
				if (methodBinding != null && methodBinding.getDeclaringClass() != null
						&& methodBinding.getDeclaringClass().isInterface()
						&& methodBinding.getDeclaringClass() != null
						&& ASTUtils.isAnyTypeInHierarchy(methodBinding.getDeclaringClass(),
								List.of(Constants.REPOSITORY_TYPE))) {
					
					try {
						Range originRange = docService.getLatestSnapshot(docId.getUri()).toRange(md.getName().getStartPosition(), md.getName().getLength());
						GoToImplParams params = new GoToImplParams(docId, methodBinding.getDeclaringClass().getQualifiedName(), methodBinding.getName(), Arrays.stream(methodBinding.getParameterTypes()).map(b -> b.getQualifiedName()).toArray(String[]::new), originRange);
						return findImplLocations(project, params);
					} catch (BadLocationException e) {
						log.error("", e);
					}
					
				}
			}
		}
		return List.of();
	}
	
	private List<LocationLink> findImplLocations(IJavaProject project, GoToImplParams implParams) {
		String genRepoFqn = implParams.repoFqName() + "Impl__AotRepository";
		Path relativeGenSourcePath = Paths.get("%s.java".formatted(genRepoFqn.replace('.', '/')));
		List<LocationLink> defs = findInSourceFolder(project, relativeGenSourcePath, genRepoFqn, implParams);
		return defs.isEmpty() ? findInBuildFolder(project, relativeGenSourcePath, genRepoFqn, implParams) : defs;
	}
	
	private List<LocationLink> getLocationInGenFile(IJavaProject project, Path genRepoSourcePath, String genRepoFqn, GoToImplParams params) {
		if (Files.exists(genRepoSourcePath)) {
			URI genUri = genRepoSourcePath.toUri();
			return cuCache.withCompilationUnit(project, genUri, genCu -> {
				List<LocationLink> defs = new ArrayList<>(1);
				genCu.accept(new ASTVisitor() {

					@Override
					public boolean visit(MethodDeclaration node) {
						IMethodBinding genBinding = node.resolveBinding();
						if (genBinding != null 
								&& genBinding.getName().equals(params.queryMethodName()) 
								&& Arrays.equals(Arrays.stream(genBinding.getParameterTypes()).map(b -> b.getQualifiedName()).toArray(), params.paramTypes)
								&& genRepoFqn.equals(genBinding.getDeclaringClass().getQualifiedName())) {
							LocationLink ll = new LocationLink();
							ll.setTargetUri(genUri.toASCIIString());
							ll.setOriginSelectionRange(params.originSelection());
							SimpleName genName = node.getName();
							int startLine = genCu.getLineNumber(genName.getStartPosition());
							// LSP line are 0-based hence -1 from line number when building LSP Range/Position
							Position targetStartPosition = new Position(startLine - 1, genName.getStartPosition() - genCu.getPosition(startLine, 0));
							int endLine = genCu.getLineNumber(genName.getStartPosition() + genName.getLength());
							Position targetEndPosition = new Position(endLine - 1, genName.getStartPosition() + genName.getLength() - genCu.getPosition(endLine, 0));
							Range targetRange = new Range(targetStartPosition, targetEndPosition);
							ll.setTargetRange(targetRange);
							ll.setTargetSelectionRange(targetRange);
							defs.add(ll);
						}
						return false;
					}
					
				});
				return defs;
			});
		}
		return List.of();
	}
	
	private List<LocationLink> findInSourceFolder(IJavaProject project, Path relativeGenSourcePath, String genRepoFqn, GoToImplParams params) {
		for (File f : IClasspathUtil.getSourceFolders(project.getClasspath()).collect(Collectors.toSet())) {
			Path genRepoSourcePath = f.toPath().resolve(relativeGenSourcePath);
			if (Files.exists(genRepoSourcePath)) {
				return getLocationInGenFile(project, genRepoSourcePath, genRepoFqn, params);
			}
		}
		return List.of();
	}
	
	private List<LocationLink> findInBuildFolder(IJavaProject project, Path relativeGenSourcePath, String genRepoFqn, GoToImplParams params) {
		Path buildDirRelativePath = null;
		Path projectPath = Paths.get(project.getLocationUri());
		Set<Path> outputFolders = IClasspathUtil.getOutputFolders(project.getClasspath()).map(f -> f.toPath()).collect(Collectors.toSet());
		for (Path f : outputFolders) {
			 Path p = projectPath.relativize(f);
			if (buildDirRelativePath == null) {
				buildDirRelativePath =  p;
			} else {
				 int i = 0;
				 for (; i < buildDirRelativePath.getNameCount() && i < p.getNameCount() && buildDirRelativePath.getName(i).equals(p.getName(i)); i++) {
					 // nothing;
				 }
				 if (i ==  0) {
					 buildDirRelativePath = Paths.get("");
					 break;
				 } else {
					 buildDirRelativePath = buildDirRelativePath.subpath(0, i);
				 }
			}
		}
		AtomicReference<Path> genSourceFilePathRef = new AtomicReference<>();
		try {
			Files.walkFileTree(projectPath.resolve(buildDirRelativePath), new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (genSourceFilePathRef.get() == null && !outputFolders.stream().anyMatch(dir::startsWith)) {
						Path genPath = dir.resolve(relativeGenSourcePath);
						if (Files.exists(genPath)) {
							genSourceFilePathRef.set(genPath);
						} else {
							return FileVisitResult.CONTINUE;
						}
					}
					return FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						return FileVisitResult.SKIP_SIBLINGS;
					}
					return super.visitFile(file, attrs);
				}

			});
		} catch (IOException e) {
			log.error("", e);
		}
		return genSourceFilePathRef.get() == null ? List.of() : getLocationInGenFile(project, genSourceFilePathRef.get(), genRepoFqn, params);
	}
	
	private void registerCommands(SimpleLanguageServer server) {
		server.onCommand(CMD_NAVIGATE_TO_IMPL, params -> {
			return CompletableFuture.supplyAsync(() -> {
				GoToImplParams implParams = new Gson().fromJson((JsonElement) params.getArguments().get(0), GoToImplParams.class);
				Optional<IJavaProject> project = projectFinder.find(implParams.docId());
				if (project.isEmpty()) {
					return List.<LocationLink>of();
				}
				return findImplLocations(project.get(), implParams);
			}).thenCompose(links -> {
				if (links.isEmpty()) {
					return CompletableFuture.completedFuture(null);
				} else {
					ShowDocumentParams showDocParams = new ShowDocumentParams(links.get(0).getTargetUri());
					showDocParams.setSelection(links.get(0).getTargetRange());
					return server.getClient().showDocument(showDocParams);
				}
			});
		});
	}
	
	public record GoToImplParams(TextDocumentIdentifier docId, String repoFqName, String queryMethodName, String[] paramTypes, Range originSelection) {} 
	
}