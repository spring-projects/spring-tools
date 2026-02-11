/*******************************************************************************
 * Copyright (c) 2018, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.jdt.ls;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.ClasspathData;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IJavadocProvider;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.java.JavaProject;
import org.springframework.ide.vscode.commons.java.JdtLsJavaProject;
import org.springframework.ide.vscode.commons.javadoc.JdtLsJavadocProvider;
import org.springframework.ide.vscode.commons.languageserver.java.ls.ClasspathListener;
import org.springframework.ide.vscode.commons.languageserver.util.ServerCapabilityInitializer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.commons.util.UriUtil;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

public class JdtLsProjectCache implements InitializableJavaProjectsService, ServerCapabilityInitializer {
		
	private static final Logger log = LoggerFactory.getLogger(JdtLsProjectCache.class);

	private static final String CMD_SPRING_BOOT_ENABLE_CLASSPATH_LISTENING = "sts.vscode-spring-boot.enableClasspathListening";
	private static final String VSCODE_JAVA_INTERNAL_PROJECT_NAME = "jdt.ls-java-project";
	
	private static final Duration INITIALIZE_TIMEOUT = Duration.ofSeconds(10);
	private static final Object JDT_SCHEME = "jdt";

	private final boolean IS_JANDEX_INDEX;

	private SimpleLanguageServer server;
	private Map<String, IJavaProject> table = new HashMap<String, IJavaProject>();
	private List<Listener> listeners = new ArrayList<>();
	
	final private ClasspathListener CLASSPATH_LISTENER = new JstLsClasspathListener();
	
	final private Disposable.Swap DISPOSABLE = Disposables.swap();
	
	private Mono<Disposable> classpathListenerRequest;
	
	private boolean classpathListenerEnabled;
	
	private boolean initialClasspathLisetnerEnable;
	
	private CompletableFuture<Boolean> classpathListeningSupport = new CompletableFuture<>();
	
	public JdtLsProjectCache(SimpleLanguageServer server, boolean isJandexIndex) {
		this.server = server;
		this.IS_JANDEX_INDEX = isJandexIndex;
		this.initialClasspathLisetnerEnable = true;
		this.server
			.onInitialized(initialize())
			.doOnSuccess((disposable) -> {
				server.onShutdown(() -> {
					disposable.dispose();
				});
				classpathListeningSupport.complete(true);
			})
			.doOnError(error -> {
				log.error("JDT-based JavaProject service not available!", error);
				enableClasspathListener(false);
				classpathListeningSupport.complete(false);
			})
			.toFuture();
	}

	private FileObserver getFileObserver() {
		return server.getWorkspaceService().getFileObserver();
	}

	@Override
	public void addListener(Listener listener) {
		synchronized (listeners) {
			listeners.add(listener);
			log.debug("added listener - now listeners registered: " + listeners.size());
		}
	}

	@Override
	public void removeListener(Listener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
			log.debug("removed listener - now listeners registered: " + listeners.size());
		}
	}

	private void notifyCreated(IJavaProject newProject) {
		logEvent("Created", newProject);

		synchronized (listeners) {
			log.debug("listeners registered: " + listeners.size());

			for (Listener listener : listeners) {
				try {
					listener.created(newProject);
				}
				catch (Exception e) {
					log.debug("listener caused exception: " + e);
				}
			}
		}
	}

	private void notifyDelete(IJavaProject deleted) {
		logEvent("Deleted", deleted);
		synchronized (listeners) {
			for (Listener listener : listeners) {
				listener.deleted(deleted);
			}
		}
		if (deleted instanceof Disposable) {
			((Disposable)deleted).dispose();
		}
	}

	private void notifyChanged(IJavaProject newProject) {
		logEvent("Changed", newProject);
		synchronized (listeners) {
			for (Listener listener : listeners) {
				listener.changed(newProject);
			}
		}
	}
	
	private void notifyProjectObserverSupported() {
		log.info("Project Observer is " + (classpathListenerEnabled ? "" : "not ") + "supported");
		synchronized (listeners) {
			for (Listener listener : listeners) {
				listener.supported();
			}
		}
	}

	private void logEvent(String type, IJavaProject project) {
		try {
			log.debug("Project {}: {}", type, project.getLocationUri());
			log.debug("Classpath has {} entries", project.getClasspath().getClasspathEntries().size());
			if (log.isDebugEnabled()) {
				//Avoid expensive call to countSourceAttachements if possible.
				log.debug("Classpath has {} source attachements",  countSourceAttachments(project.getClasspath().getClasspathEntries()));
			}
		} catch (Exception e) {
		}
	}

	private static int countSourceAttachments(Collection<CPE> classpathEntries) {
		int count = 0;
		for (CPE cpe : classpathEntries) {
			URL sourceJar = cpe.getSourceContainerUrl();
			if (sourceJar!=null) {
				try {
					if (new File(sourceJar.toURI()).exists()) {
						count++;
					}
				} catch (URISyntaxException e) {
				}
			}
		}
		return count;
	}

	@Override
	public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
		// JDT URI has project
		URI docUri = URI.create(doc.getUri());
		if (JDT_SCHEME.equals(docUri.getScheme()) && "contents".equals(docUri.getAuthority())) {
			return findProjectForJDtUri(docUri);
		}
		String uri = UriUtil.normalize(doc.getUri());
		log.debug("find {} ", uri);
		synchronized (table) {
			String foundUri = null;
			IJavaProject foundProject = null;
			for (Entry<String, IJavaProject> e : table.entrySet()) {
				String projectUri = e.getKey();
				log.debug("projectUri = '{}'", projectUri);
				if (UriUtil.contains(projectUri, uri)) {
					if (foundUri==null) {
						log.debug("found {} for {}", e.getValue(), uri);
						foundUri = projectUri;
						foundProject = e.getValue();
					} else if (UriUtil.contains(foundUri, projectUri)) {
						log.debug("found more nested {} for {}", e.getValue(), uri);
						foundUri = projectUri;
						foundProject = e.getValue();
					} else {
						log.debug("found {} for {} but keeping {}", e.getValue(), uri, foundProject);
					}
				}
			}
			return Optional.ofNullable(foundProject);
		}
	}

	private Optional<IJavaProject> findProjectForJDtUri(URI uri) {
		String query = uri.getQuery();
		try {
			String decodedQuery = URLDecoder.decode(query, "UTF-8");
			int lastIdx = decodedQuery.indexOf("/\\/");
			if (lastIdx > 0) {
				String projectName = decodedQuery.substring(1, lastIdx);
				synchronized (table) {
					for (IJavaProject project : table.values()) {
						if (project.getElementName().equals(projectName)) {
							return Optional.of(project);
						}
					}
				}
			}
		} catch (UnsupportedEncodingException e) {
			log.error("{}", e);
		}
		return Optional.empty();
	}

	@Override
	public IJavadocProvider javadocProvider(URI projectUri, CPE classpathEntry) {
		return new JdtLsJavadocProvider(server.getClient(), projectUri);
	}

	@Override
	public Mono<Disposable> initialize() {
		return Mono.defer(() -> {
			log.info("INIT CLASSPATH LISTENER enableClasspath=" + initialClasspathLisetnerEnable);
			enableClasspathListener(initialClasspathLisetnerEnable);
			return Mono.just(DISPOSABLE);
		});
	}
	
	private synchronized void enableClasspathListener(boolean enabled) {
		log.info("Enable classpath listener enabled = " + enabled + " current enablement = " + classpathListenerEnabled);
		if (classpathListenerEnabled != enabled) {
			if (enabled) {
				log.info("Adding classpath listener enabled=" + enabled);
				classpathListenerEnabled = true;
				notifyProjectObserverSupported();
				classpathListenerRequest = server.addClasspathListener(CLASSPATH_LISTENER).timeout(INITIALIZE_TIMEOUT)
						.doOnSubscribe(x -> log.debug("addClasspathListener ..."))
						.doOnSuccess(x -> log.debug("addClasspathListener DONE"))
						.doOnError(t -> {
							log.error("Unexpected error registering classpath listener with JDT.", t);
							enableClasspathListener(false);
						});
				final Mono<Disposable> oldClasspathSubscription = classpathListenerRequest;
				classpathListenerRequest.subscribe(d -> {
					if (oldClasspathSubscription != classpathListenerRequest) {
						d.dispose();
					} else {
						DISPOSABLE.update(d);
					}
				});
			} else {
				log.info("Removing classpath listener enabled=" + enabled);
				DISPOSABLE.update(Disposables.single());
				classpathListenerRequest = null;
				classpathListenerEnabled = false;
				notifyProjectObserverSupported();
			}
		}
	}
	
	@Override
	public Collection<? extends IJavaProject> all() {
		return ImmutableList.copyOf(table.values());
	}

	@Override
	public boolean isSupported() {
		return classpathListenerEnabled;
	}
	
	@Override
	public void initialize(InitializeParams p, ServerCapabilities cap) {
		server.onCommand(CMD_SPRING_BOOT_ENABLE_CLASSPATH_LISTENING, params -> 
			classpathListeningSupport.thenApply(supported -> {
				if (!supported) {
					throw new IllegalStateException("Classpath listening not supported.");
				} else {
					if (params.getArguments().get(0) instanceof JsonPrimitive) {
						boolean classpathListeningEnabled = ((JsonPrimitive)params.getArguments().get(0)).getAsBoolean();
						log.info("CMD - Enable classpath listening: " + classpathListeningEnabled);
						enableClasspathListener(classpathListeningEnabled);
					}
				}
				return supported;
			})
		);

		log.debug("REGISTER ENABLE CLASSPATH CMD");
		JsonObject o = (JsonObject) p.getInitializationOptions();
		if (o != null) {
			JsonPrimitive enable = o.getAsJsonPrimitive("enableJdtClasspath");
			if (enable != null) {
				log.debug("READING INIT VALUE for classpathEnabled=" + enable.getAsBoolean());
				initialClasspathLisetnerEnable = enable.getAsBoolean();
			}
		}
		log.debug("INIT VALUE for classpathEnabled=" + initialClasspathLisetnerEnable);
		cap.getExecuteCommandProvider().getCommands().add(CMD_SPRING_BOOT_ENABLE_CLASSPATH_LISTENING);
	}
	
	private class JstLsClasspathListener implements ClasspathListener {
		
		/*
		 * Synchronize to make non-reentrant such that events handled in predictable order 
		 */
		@Override
		public synchronized void changed(Event event) {
			// ignore events for vscode-java specific internal project, it is not a user-facing project
			if (event.name != null && event.name.contains(VSCODE_JAVA_INTERNAL_PROJECT_NAME)) {
				return;
			}
			
			log.debug("claspath event received {}", event);
			server.doOnInitialized(() -> {
				try {
					String uri = UriUtil.normalize(event.projectUri);
					log.debug("uri = {}", uri);
					if (event.deleted) {
						log.debug("event.deleted = true");
						IJavaProject deleted;
						synchronized (table) {
							deleted = table.remove(uri);
						}
						// Notify outside of the lock 
						if (deleted!=null) {
							log.debug("removed from table = true");
							notifyDelete(deleted);
						} else {
							log.warn("Deleted project not removed because uri {} not found in {}", uri, table.keySet());
						}
					} else {
						log.debug("deleted = false");
						URI projectUri = new URI(uri);
						ClasspathData classpath = new ClasspathData(event.name, event.classpath.getEntries(), event.classpath.getJre());
						IJavaProject oldProject, newProject;
						synchronized(table) {
							oldProject = table.get(uri);
							if (oldProject != null && classpath.equals(oldProject.getClasspath())) {
								// nothing has changed
								return;
							}
							IProjectBuild projectBuild = from(event.projectBuild);
							newProject = IS_JANDEX_INDEX
									? new JavaProject(getFileObserver(), projectUri, classpath,
											JdtLsProjectCache.this, projectBuild, event.javaCoreOptions)
									: new JdtLsJavaProject(server.getClient(), projectUri, classpath, JdtLsProjectCache.this, projectBuild, event.javaCoreOptions);
							table.put(uri, newProject);
						}
						// Notify outside of the lock 
						if (oldProject != null) {
							notifyChanged(newProject);
						} else {
							notifyCreated(newProject);
						}
					}
				} catch (Exception e) {
					log.error("", e);
				}
			});
		}
	}
	
	private static IProjectBuild from(ProjectBuild projectBuild) {
		return projectBuild == null ? null : IProjectBuild.create(projectBuild.type(), projectBuild.buildFile() == null ? null : URI.create(projectBuild.buildFile()));
	}

}
