/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.maven;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.visitor.CloningDependencyVisitor;
import org.eclipse.aether.util.graph.visitor.FilteringDependencyVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.JavaUtils;
import org.springframework.ide.vscode.commons.protocol.java.Gav;

/**
 * Maven Core functionality
 *
 * @author Alex Boyko
 *
 */
public class MavenCore {

	private static final String CLASSIFIER_SOURCES = "sources";
	private static final String CLASSIFIER_JAVADOC = "javadoc";
	private static final String CLASSIFIER_TESTS = "tests";
	private static final String CLASSIFIER_TESTSOURCES = "test-sources";

	private static final String JAVA_HOME = "java.home";
	private static final String JAVA_RUNTIME_VERSION = "java.runtime.version";
	private static final String JAVA_BOOT_CLASS_PATH = "sun.boot.class.path";
	public static final String CLASSPATH_TXT = "classpath.txt";
	public static final String POM_XML = "pom.xml";

	private static MavenCore defaultInstance = null;

	private static Logger log = LoggerFactory.getLogger(MavenCore.class);

	private MavenBridge maven;

	public static MavenCore getDefault() {
		if (defaultInstance == null) {
			defaultInstance = new MavenCore(IMavenConfiguration.DEFAULT);
		}
		return defaultInstance;
	}

	public MavenCore(IMavenConfiguration config) {
		this.maven = new MavenBridge(config);
	}

	/**
	 * Reads maven classpath text file
	 *
	 * @param classPathFilePath
	 * @return set of classpath entries
	 * @throws IOException
	 */
	public static Stream<Path> readClassPathFile(Path classPathFilePath) throws IOException {
		InputStream in = Files.newInputStream(classPathFilePath);
		String text = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining());
		Path dir = classPathFilePath.getParent();
		return Arrays.stream(text.split(File.pathSeparator)).map(dir::resolve);
	}

	/**
	 * Creates Maven Project descriptor based on the pom file.
	 *
	 * @param pom The pom file
	 * @return Maven project instance
	 * @throws MavenException
	 */
	public MavenProject readProject(File pom, boolean resolveDependencies) throws MavenException {
		return maven.readProject(pom, maven.createExecutionRequest(), resolveDependencies);
	}

	public MavenExecutionResult build(File pom) throws MavenException {
		return maven.compileAndGenerateJavadoc(pom);
	}

	/**
	 * Taken from M2E same named method from MavenModelManager
	 *
	 * @param repositorySystem
	 * @param repositorySession
	 * @param mavenProject
	 * @param scope
	 * @return
	 */
	private DependencyNode readDependencyTree(org.eclipse.aether.RepositorySystem repositorySystem,
			RepositorySystemSession repositorySession, MavenProject mavenProject, String scope) {
		DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySession);

		ConflictResolver transformer = new ConflictResolver(new NearestVersionSelector(), new JavaScopeSelector(),
				new SimpleOptionalitySelector(), new JavaScopeDeriver());
		session.setDependencyGraphTransformer(transformer);
		session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, Boolean.toString(true));
		session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

		ArtifactTypeRegistry stereotypes = session.getArtifactTypeRegistry();

		CollectRequest request = new CollectRequest();
		request.setRequestContext("project"); //$NON-NLS-1$
		request.setRepositories(mavenProject.getRemoteProjectRepositories());

		for (org.apache.maven.model.Dependency dependency : mavenProject.getDependencies()) {
			request.addDependency(RepositoryUtils.toDependency(dependency, stereotypes));
		}

		DependencyManagement depMngt = mavenProject.getDependencyManagement();
		if (depMngt != null) {
			for (org.apache.maven.model.Dependency dependency : depMngt.getDependencies()) {
				request.addManagedDependency(RepositoryUtils.toDependency(dependency, stereotypes));
			}
		}

		DependencyNode node;
		try {
			node = repositorySystem.collectDependencies(session, request).getRoot();
		} catch (DependencyCollectionException ex) {
			node = ex.getResult().getRoot();
		}

		Collection<String> scopes = new HashSet<String>();
		Collections.addAll(scopes, Artifact.SCOPE_SYSTEM, Artifact.SCOPE_COMPILE, Artifact.SCOPE_PROVIDED,
				Artifact.SCOPE_RUNTIME, Artifact.SCOPE_TEST);
		if (Artifact.SCOPE_COMPILE.equals(scope)) {
			scopes.remove(Artifact.SCOPE_COMPILE);
			scopes.remove(Artifact.SCOPE_SYSTEM);
			scopes.remove(Artifact.SCOPE_PROVIDED);
		} else if (Artifact.SCOPE_RUNTIME.equals(scope)) {
			scopes.remove(Artifact.SCOPE_COMPILE);
			scopes.remove(Artifact.SCOPE_RUNTIME);
		} else if (Artifact.SCOPE_COMPILE_PLUS_RUNTIME.equals(scope)) {
			scopes.remove(Artifact.SCOPE_COMPILE);
			scopes.remove(Artifact.SCOPE_SYSTEM);
			scopes.remove(Artifact.SCOPE_PROVIDED);
			scopes.remove(Artifact.SCOPE_RUNTIME);
		} else {
			scopes.clear();
		}

		CloningDependencyVisitor cloner = new CloningDependencyVisitor();
		node.accept(new FilteringDependencyVisitor(cloner, new ScopeDependencyFilter(null, scopes)));
		node = cloner.getRootNode();

		return node;
	}

	/**
	 * Calculates dependency graph for a Maven project provided the scope.
	 *
	 * @param project Maven Project descriptor
	 * @param scope Dependency scope
	 * @return Set of all dependencies including transient ones
	 * @throws MavenException
	 */
	public Set<Artifact> resolveDependencies(MavenProject project, String scope) throws MavenException {
		MavenExecutionRequest request = maven.createExecutionRequest();
		DefaultRepositorySystemSession session = maven.createRepositorySession(request);

		DependencyNode graph = readDependencyTree(maven.lookupComponent(org.eclipse.aether.RepositorySystem.class), session, project, scope);
		if (graph != null) {

			ArrayList<DependencyNode> dependencyNodes = new ArrayList<>();
			graph.accept(new DependencyVisitor() {
				@Override
				public boolean visitEnter(DependencyNode node) {
					if (node.getDependency() != null) {
						dependencyNodes.add(node);
					}
					return true;
				}

				@Override
				public boolean visitLeave(DependencyNode dependencynode) {
					return true;
				}
			});

			LinkedHashSet<Artifact> artifacts = new LinkedHashSet<>();
			RepositoryUtils.toArtifacts(artifacts, dependencyNodes,
					Collections.singletonList(project.getArtifact().getId()), null);

			return artifacts.parallelStream().map(artifact -> {
				if (!artifact.isResolved()) {
					try {
						artifact = maven.resolve(artifact, project.getRemoteArtifactRepositories(), request);
					} catch (MavenException e) {
						log.error("", e);
						// Maven 2.x quirk: an artifact always points at the local repo,
						// regardless whether resolved or not
						LocalRepositoryManager lrm = session.getLocalRepositoryManager();
						String path = lrm.getPathForLocalArtifact(RepositoryUtils.toArtifact(artifact));
						artifact.setFile(new File(lrm.getRepository().getBasedir(), path));
					}
				}
				return artifact;
			}).collect(Collectors.toSet());
		}

		return Collections.emptySet();
	}

	public File localRepositoryFolder() throws MavenException {
		MavenExecutionRequest request = maven.createExecutionRequest();
		DefaultRepositorySystemSession session = maven.createRepositorySession(request);
		LocalRepositoryManager lrm = session.getLocalRepositoryManager();
		return lrm.getRepository().getBasedir();
	}

	public Artifact getSources(Artifact artifact, List<ArtifactRepository> repositories) throws MavenException {
		return maven.resolve(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), CLASSIFIER_SOURCES, repositories, maven.createExecutionRequest());
	}

	public Artifact getJavadoc(Artifact artifact, List<ArtifactRepository> repositories) throws MavenException {
		return maven.resolve(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), CLASSIFIER_JAVADOC, repositories, maven.createExecutionRequest());
	}

	public Artifact getTests(Artifact artifact, List<ArtifactRepository> repositories) throws MavenException {
		return maven.resolve(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), CLASSIFIER_TESTS, repositories, maven.createExecutionRequest());
	}

	public Artifact getTestSources(Artifact artifact, List<ArtifactRepository> repositories) throws MavenException {
		return maven.resolve(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), CLASSIFIER_TESTSOURCES, repositories, maven.createExecutionRequest());
	}

	public Stream<Path> getJreLibs() throws MavenException {
		return JavaUtils.jreLibs(this::getJavaRuntimeMinorVersion, () -> {
				try {
					return Optional.of(Paths.get(getJavaHome()));
				} catch (MavenException e) {
					log.error("Cannot determine java home", e);
					return Optional.empty();
				}
			},
			() -> {
				try {
					return (String) maven.createExecutionRequest().getSystemProperties().get(JAVA_BOOT_CLASS_PATH);
				} catch (MavenException e) {
					log.error("Cannot determine boot classpath", e);
					return null;
				}
			}
		);
	}

	public String getJavaRuntimeVersion() throws MavenException {
		return maven.createExecutionRequest().getSystemProperties().getProperty(JAVA_RUNTIME_VERSION);
	}
	
	public String getJavaHome() throws MavenException {
		return maven.createExecutionRequest().getSystemProperties().getProperty(JAVA_HOME);
	}
 
	public String getJavaRuntimeMinorVersion() {
		try {
			return JavaUtils.getJavaRuntimeMinorVersion(getJavaRuntimeVersion());
		} catch (MavenException e) {
			log.error("Cannot determine Java runtime version. Defaulting to version 8", e);
		}
		return null;
	}
	
	public MavenProject findPeerProject(MavenProject currentProject, Artifact dependency) {
		if (currentProject.getGroupId().equals(dependency.getGroupId())) {
			try {
				Path parentFolder = currentProject.getBasedir().toPath().getParent();
				if (Files.isRegularFile(parentFolder.resolve(MavenCore.POM_XML))) {
					MavenProject parent = readProject(parentFolder.resolve(MavenCore.POM_XML).toFile(), false);
					for (String module : parent.getModules()) {
						Path path = parentFolder.resolve(module);
						if (Files.isDirectory(path)
								&& !currentProject.getBasedir().equals(path.toFile())
								&& Files.isRegularFile(path.resolve(MavenCore.POM_XML))) {
							MavenProject peerProject = readProject(path.resolve(MavenCore.POM_XML).toFile(), false);
							if (dependency.equals(peerProject.getArtifact())) {
								return peerProject;
							}
						}
					}
				}
			} catch (MavenException e) {
				log.error("{}", e);
			}
		}
		return null;
	}
	
	public Gav computeGav(File pom) {
		try {
			MavenProject p = readProject(pom, false);
			return new Gav(p.getGroupId(), p.getArtifactId(), p.getVersion());
		} catch (Exception e) {
			log.error("", e);
			return null;
		}
	}
	


}
