/*******************************************************************************
 * Copyright (c) 2018, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.value.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.ide.vscode.commons.java.ClasspathIndex;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.VM;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.commons.util.IOUtil;

import com.google.common.io.Files;

public class MockProjects {

	private final Map<String, MockProject> projectsByName = new HashMap<String, MockProjects.MockProject>();

	public final MockProjectObserver observer = new MockProjectObserver();
	public final JavaProjectFinder finder = new JavaProjectFinder() {
		@Override
		public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
			synchronized (projectsByName) {
				try {
					File file = new File(new URI(doc.getUri()));
					for (MockProject project : projectsByName.values()) {
						if (project.contains(file)) {
							return Optional.of(project);
						}
					}
				} catch (Exception e) {
				}

				return Optional.empty();
			}
		}

		@Override
		public Collection<? extends IJavaProject> all() {
			return projectsByName.values();
		}
	};

	public MockFileObserver fileObserver = new MockFileObserver();

	public class MockProject implements IJavaProject {

		final private File root;
		final private String name;
//		final private String javaVersion;
		final private List<File> sourceFolders = new ArrayList<File>();
		private File defaultOutputFolder;

		final private IClasspath classpath = new IClasspath() {

			@Override
			public String getName() {
				return name;
			}

			@Override
			public Collection<CPE> getClasspathEntries() throws Exception {
				List<CPE> cp = new ArrayList<>();
				for (File sf : sourceFolders) {
					CPE cpe = new CPE(Classpath.ENTRY_KIND_SOURCE, sf.getAbsolutePath());
					cpe.setOutputFolder(defaultOutputFolder.getAbsolutePath());
					cp.add(cpe);
				}
				return cp;
			}

			@Override
			public VM getVM() {
//				return javaVersion;
				return null;
			}
		};

		public MockProject(String name) {
			synchronized (projectsByName) {
				assertFalse(projectsByName.containsKey(name));
				this.name = name;
//				this.javaVersion = "";
				this.root = Files.createTempDir();
				createSourceFolder("src/main/java");
				createSourceFolder("src/main/resources");
				createOutputFolder("target/classes");
				projectsByName.put(name, this);
			}
			synchronized (observer.listeners) {
				for (Listener l : observer.listeners) {
					l.created(this);
				}
			}
		}

		public boolean contains(File file) {
			return file.toPath().startsWith(root.toPath());
		}

		private void createOutputFolder(String projectRelativePath) {
			assertNull(this.defaultOutputFolder, "Output folder already created");
			File outFolder = new File(root, projectRelativePath);
			outFolder.mkdirs();
			this.defaultOutputFolder = outFolder;
		}

		public void createSourceFolder(String projectRelativePath) {
			File sourceFolder = new File(root, projectRelativePath);
			sourceFolder.mkdirs();
			sourceFolders.add(sourceFolder);
			synchronized (observer.listeners) {
				for (Listener l : observer.listeners) {
					l.changed(this);
				}
			}
		}

		@Override
		public IClasspath getClasspath() {
			return classpath;
		}

		@Override
		public ClasspathIndex getIndex() {
			//TODO: the fact we have to implement this probably means something is a bit off with the
			// framework api, because this info should not really depend on anything but a project's classpath.
			// So why should every type of project need to implement its own mechanic for indexing classpath?
			throw new IllegalStateException("Not implemented");
		}

		@Override
		public URI getLocationUri() {
			return root.toURI();
		}

		@Override
		public boolean exists() {
			return root.isDirectory();
		}

		public File ensureFileNoEvents(String projectRelativePath, String contents) throws Exception {
			return ensureFile(false, projectRelativePath, contents);
		}

		private File ensureFile(boolean fireEvents, String projectRelativePath, String contents) throws Exception {
			File target = new File(root, projectRelativePath);
			boolean existed = target.exists();
			IOUtil.pipe(new ByteArrayInputStream(contents.getBytes("UTF8")), target);
			if (fireEvents) {
				if (existed) {
					fileObserver.fileChanged(target);
				} else {
					fileObserver.fileCreated(target);
				}
			}
			return target;
		}

		public void ensureFile(String projectRelativePath, String contents) throws Exception {
			ensureFile(true, projectRelativePath, contents);
		}

		public String uri(String projectRelativePath) {
			return new File(root, projectRelativePath).toURI().toASCIIString();
		}

		@Override
		public IProjectBuild getProjectBuild() {
			return null;
		}
	}

	public class MockProjectObserver implements ProjectObserver {

		public final LinkedHashSet<Listener> listeners = new LinkedHashSet<>();

		@Override
		public void addListener(Listener l) {
			listeners.add(l);
		}

		@Override
		public void removeListener(Listener l) {
			listeners.remove(l);
		}

	}

	private static class FileListener {
		
		final PathMatcher matcher;
		final Consumer<String[]> handler;
		
		FileListener(List<String> globPatterns, Consumer<String[]> listener) {
			super();
			this.matcher = buildPathMatcher(globPatterns);
			this.handler = listener;
		}
		
		private PathMatcher buildPathMatcher(List<String> globPatterns) {
			if (globPatterns.size()==0) {
				return path -> true;
			} else if (globPatterns.size()==1) {
				return FileSystems.getDefault().getPathMatcher("glob:"+globPatterns.get(0));
			} else {
				PathMatcher[] matchers = new PathMatcher[globPatterns.size()];
				for (int i = 0; i < matchers.length; i++) {
					matchers[i] = FileSystems.getDefault().getPathMatcher("glob:"+globPatterns.get(i));
				}
				return (path) -> {
					for (int i = 0; i < matchers.length; i++) {
						if (matchers[i].matches(path)) {
							return true;
						}
					}
					return false;
				};
			}
		}
	}

	public class MockFileObserver implements FileObserver {

		final AtomicLong idGen = new AtomicLong();

		final Map<String,FileListener> create_listeners = new HashMap<>();
		final Map<String,FileListener> change_listeners = new HashMap<>();
		final Map<String,FileListener> delete_listeners = new HashMap<>();

		private String add(Map<String, FileListener> listeners, List<String> globPatterns, Consumer<String[]> handler) {
			String id = ""+idGen.incrementAndGet();
			synchronized (listeners) {
				listeners.put(id, new FileListener(globPatterns, handler));
			}
			return id;
		}

		public void fileChanged(File target) {
			notify(change_listeners, target);
		}

		public void fileCreated(File target) {
			notify(create_listeners, target);
		}

		private void notify(Map<String, FileListener> listeners, File target) {
			Path path = target.toPath();
			synchronized (listeners) {
				for (FileListener l : listeners.values()) {
					if (l.matcher.matches(path)) {
						l.handler.accept(new String[] {target.toURI().toASCIIString()});
					}
				}
			}
		}

		@Override
		public String onFilesCreated(List<String> globPattern, Consumer<String[]> handler) {
			return add(create_listeners, globPattern, handler);
		}


		@Override
		public String onFilesChanged(List<String> globPattern, Consumer<String[]> handler) {
			return add(change_listeners, globPattern, handler);
		}

		@Override
		public String onFilesDeleted(List<String> globPattern, Consumer<String[]> handler) {
			return add(delete_listeners, globPattern, handler);
		}

		@Override
		public boolean unsubscribe(String subscriptionId) {
			return
					remove(create_listeners, subscriptionId) ||
					remove(change_listeners, subscriptionId) ||
					remove(delete_listeners, subscriptionId);
		}

		private boolean remove(Map<String, FileListener> listeners, String subscriptionId) {
			synchronized (listeners) {
				return listeners.remove(subscriptionId) != null;
			}
		}
	}

	public MockProject create(String name) {
		return new MockProject(name);
	}

}
