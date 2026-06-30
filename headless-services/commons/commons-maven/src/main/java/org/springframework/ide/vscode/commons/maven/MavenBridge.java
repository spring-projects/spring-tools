/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.maven;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.properties.internal.EnvironmentUtils;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.settings.io.SettingsWriter;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

/**
 * Trimmed down version of M2E MavenImpl class
 * 
 * @author Alex Boyko
 *
 */
class MavenBridge {
	private static final Logger log = LoggerFactory.getLogger(MavenBridge.class);

	/**
	 * Id of maven core class realm
	 */
	public static final String MAVEN_CORE_REALM_ID = "plexus.core"; //$NON-NLS-1$

	private DefaultPlexusContainer plexus;

	private final IMavenConfiguration mavenConfiguration;

	/**
	 * Cached parsed settings.xml instance
	 */
	private Settings settings;

	/** File length of cached user settings */
	private long settings_length;

	/** Last modified timestamp of cached user settings */
	private long settings_timestamp;

	public MavenBridge(IMavenConfiguration mavenConfiguration) {
		this.mavenConfiguration = mavenConfiguration;
	}

	/* package */@SuppressWarnings("deprecation")
	MavenExecutionRequest createExecutionRequest() throws MavenException {
		MavenExecutionRequest request = new DefaultMavenExecutionRequest();

		// this causes problems with unexpected "stale project configuration"
		// error markers
		// need to think how to manage ${maven.build.timestamp} properly inside
		// workspace
		// request.setStartTime( new Date() );

		if (mavenConfiguration.getGlobalSettingsFile() != null) {
			request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
		}

		File userSettingsFile =  SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE;

		if (mavenConfiguration.getUserSettingsFile() != null) {
			userSettingsFile = new File(mavenConfiguration.getUserSettingsFile());
		}
		request.setUserSettingsFile(userSettingsFile);

		try {
			lookup(MavenExecutionRequestPopulator.class).populateFromSettings(request, getSettings());
		} catch (MavenExecutionRequestPopulationException ex) {
			throw new MavenException(ex);
		}

		ArtifactRepository localRepository = getLocalRepository();
		request.setLocalRepository(localRepository);
		request.setLocalRepositoryPath(localRepository.getBasedir());
		// request.setOffline(mavenConfiguration.isOffline());

		// request.getUserProperties().put("m2e.version",
		// MavenPluginActivator.getVersion()); //$NON-NLS-1$
		// request.getUserProperties().put(ConfigurationProperties.USER_AGENT,
		// MavenPluginActivator.getUserAgent());

		EnvironmentUtils.addEnvVars(request.getSystemProperties());
		copyProperties(request.getSystemProperties(), System.getProperties());

		request.setCacheNotFound(true);
		request.setCacheTransferError(true);

		// request.setGlobalChecksumPolicy(mavenConfiguration.getGlobalChecksumPolicy());
		// the right way to disable snapshot update
		// request.setUpdateSnapshots(false);
		return request;
	}

	public String getLocalRepositoryPath() {
		String path = null;
		try {
			Settings settings = getSettings();
			path = settings.getLocalRepository();
		} catch (MavenException ex) {
			// fall through
		}
		if (path == null) {
			path = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
		}
		return path;
	}

	@SuppressWarnings("deprecation")
	public MavenSession createSession(MavenExecutionRequest request, MavenProject project) throws MavenException {
		RepositorySystemSession repoSession = createRepositorySession(request);
		MavenExecutionResult result = new DefaultMavenExecutionResult();
		MavenSession mavenSession = new MavenSession(plexus, repoSession, request, result);
		if (project != null) {
			mavenSession.setProjects(Collections.singletonList(project));
		}
		return mavenSession;
	}

	/* package */DefaultRepositorySystemSession createRepositorySession(MavenExecutionRequest request)
			throws MavenException {
		return (DefaultRepositorySystemSession) ((DefaultMaven) lookup(Maven.class)).newRepositorySession(request);
	}

	public void releaseMojo(Object mojo, MojoExecution mojoExecution) throws MavenException {
		lookup(MavenPluginManager.class).releaseMojo(mojo, mojoExecution);
	}

	public ArtifactRepository getLocalRepository() throws MavenException {
		try {
			String localRepositoryPath = getLocalRepositoryPath();
			if (localRepositoryPath != null) {
				return lookup(RepositorySystem.class).createLocalRepository(new File(localRepositoryPath));
			}
			return lookup(RepositorySystem.class).createLocalRepository(RepositorySystem.defaultUserLocalRepository);
		} catch (InvalidRepositoryException ex) {
			// can't happen
			throw new IllegalStateException(ex);
		}
	}

	public Settings getSettings() throws MavenException {
		return getSettings(false);
	}

	public synchronized Settings getSettings(final boolean force_reload) throws MavenException {
		// MUST NOT use createRequest!

		File userSettingsFile = SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE;
		if (mavenConfiguration.getUserSettingsFile() != null) {
			userSettingsFile = new File(mavenConfiguration.getUserSettingsFile());
		}

		boolean reload = force_reload || settings == null;

		if (!reload && userSettingsFile != null) {
			reload = userSettingsFile.lastModified() != settings_timestamp
					|| userSettingsFile.length() != settings_length;
		}

		if (reload) {
			// TODO: Can't that delegate to buildSettings()?
			SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
			// 440696 guard against ConcurrentModificationException
			Properties systemProperties = new Properties();
			copyProperties(systemProperties, System.getProperties());
			request.setSystemProperties(systemProperties);
			if (mavenConfiguration.getGlobalSettingsFile() != null) {
				request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
			}
			if (userSettingsFile != null) {
				request.setUserSettingsFile(userSettingsFile);
			}
			try {
				settings = lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
			} catch (SettingsBuildingException ex) {
				String msg = "Could not read settings.xml, assuming default values";
				log.error(msg, ex);
				/*
				 * NOTE: This method provides input for various other core
				 * functions, just bailing out would make m2e highly unusuable.
				 * Instead, we fail gracefully and just ignore the broken
				 * settings, using defaults.
				 */
				settings = new Settings();
			}

			if (userSettingsFile != null) {
				settings_length = userSettingsFile.length();
				settings_timestamp = userSettingsFile.lastModified();
			}
		}
		return settings;
	}

	public Settings buildSettings(String globalSettings, String userSettings) throws MavenException {
		SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
		request.setGlobalSettingsFile(globalSettings != null ? new File(globalSettings) : null);
		request.setUserSettingsFile(userSettings != null ? new File(userSettings)
				: SettingsXmlConfigurationProcessor.DEFAULT_USER_SETTINGS_FILE);
		try {
			return lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
		} catch (SettingsBuildingException ex) {
			throw new MavenException(ex);
		}
	}

	public void writeSettings(Settings settings, OutputStream out) throws MavenException {
		try {
			lookup(SettingsWriter.class).write(out, null, settings);
		} catch (IOException ex) {
			throw new MavenException(ex);
		}
	}

	public Server decryptPassword(Server server) throws MavenException {
		SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
		SettingsDecryptionResult result = lookup(SettingsDecrypter.class).decrypt(request);
		for (SettingsProblem problem : result.getProblems()) {
			log.warn(problem.getMessage(), problem.getException());
		}
		return result.getServer();
	}

	public Model readModel(InputStream in) throws MavenException {
		try {
			return lookup(ModelReader.class).read(in, null);
		} catch (IOException e) {
			throw new MavenException(e);
		}
	}

	public Model readModel(File pomFile) throws MavenException {
		try {
			BufferedInputStream is = new BufferedInputStream(new FileInputStream(pomFile));
			try {
				return readModel(is);
			} finally {
				IOUtil.close(is);
			}
		} catch (IOException e) {
			throw new MavenException(e);
		}
	}

	public void writeModel(Model model, OutputStream out) throws MavenException {
		try {
			lookup(ModelWriter.class).write(out, null, model);
		} catch (IOException ex) {
			throw new MavenException(ex);
		}
	}

	public MavenProject readProject(final File pomFile, MavenExecutionRequest request, boolean resolveDependencies) throws MavenException {
		try {
			lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
			ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
			configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
			configuration.setRepositorySession(createRepositorySession(request));
			configuration.setResolveDependencies(resolveDependencies);
			configuration.setProcessPlugins(false);
			return lookup(ProjectBuilder.class).build(pomFile, configuration).getProject();
		} catch (ProjectBuildingException ex) {
			throw new MavenException(ex);
		} catch (MavenExecutionRequestPopulationException ex) {
			throw new MavenException(ex);
		}
	}
	
	public MavenExecutionResult compileAndGenerateJavadoc(File pom) throws MavenException {
		try {
			MavenExecutionRequest request = createExecutionRequest();
			lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
			request.setPom(pom);
			ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
			configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
			configuration.setRepositorySession(createRepositorySession(request));
			configuration.setResolveDependencies(true);
			request.setGoals(Arrays.asList(new String[] { "compile", "javadoc:javadoc" }));
			Properties userProperties = (Properties) request.getUserProperties().clone();
			userProperties.put("show", "private");
			request.setUserProperties(userProperties);
			return lookup(Maven.class).execute(request);
		} catch (MavenExecutionRequestPopulationException e) {
			throw new MavenException(e);
		}
	}

	public MavenExecutionResult readMavenProject(File pomFile, ProjectBuildingRequest configuration)
			throws MavenException {
		long start = System.currentTimeMillis();

		log.debug("Reading Maven project: {}", pomFile.getAbsoluteFile()); //$NON-NLS-1$
		MavenExecutionResult result = new DefaultMavenExecutionResult();
		try {
			configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
			configuration.setProcessPlugins(false);
			ProjectBuildingResult projectBuildingResult = lookup(ProjectBuilder.class).build(pomFile, configuration);
			result.setProject(projectBuildingResult.getProject());
			result.setDependencyResolutionResult(projectBuildingResult.getDependencyResolutionResult());
		} catch (ProjectBuildingException ex) {
			if (ex.getResults() != null && ex.getResults().size() == 1) {
				ProjectBuildingResult projectBuildingResult = ex.getResults().get(0);
				result.setProject(projectBuildingResult.getProject());
				result.setDependencyResolutionResult(projectBuildingResult.getDependencyResolutionResult());
			}
			result.addException(ex);
		} catch (RuntimeException e) {
			result.addException(e);
		} finally {
			log.debug("Read Maven project: {} in {} ms", pomFile.getAbsoluteFile(), System.currentTimeMillis() - start); //$NON-NLS-1$
		}
		return result;
	}

	/* package */MavenProject resolveParentProject(RepositorySystemSession repositorySession, MavenProject child,
			ProjectBuildingRequest configuration) throws MavenException {
		configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
		configuration.setProcessPlugins(false);
		configuration.setRepositorySession(repositorySession);

		try {
			configuration.setRemoteRepositories(child.getRemoteArtifactRepositories());

			File parentFile = child.getParentFile();
			if (parentFile != null) {
				return lookup(ProjectBuilder.class).build(parentFile, configuration).getProject();
			}

			Artifact parentArtifact = child.getParentArtifact();
			if (parentArtifact != null) {
				MavenProject parent = lookup(ProjectBuilder.class).build(parentArtifact, configuration).getProject();
				parentFile = parentArtifact.getFile(); // file is resolved as
														// side-effect of the
														// prior call
				// compensate for apparent bug in maven 3.0.4 which does not set
				// parent.file and parent.artifact.file
				if (parent.getFile() == null) {
					parent.setFile(parentFile);
				}
				if (parent.getArtifact().getFile() == null) {
					parent.getArtifact().setFile(parentFile);
				}
				return parent;
			}
		} catch (ProjectBuildingException ex) {
			log.error("Could not read parent project", ex);
		}

		return null;
	}

	/**
	 * This is a temporary implementation that only works for artifacts resolved
	 * using #resolve.
	 */
	public boolean isUnavailable(String groupId, String artifactId, String version, String type, String classifier,
			List<ArtifactRepository> remoteRepositories) throws MavenException {
		Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version,
				type, classifier);

		ArtifactRepository localRepository = getLocalRepository();

		File artifactFile = new File(localRepository.getBasedir(), localRepository.pathOf(artifact));

		if (artifactFile.canRead()) {
			// artifact is available locally
			return false;
		}

		if (remoteRepositories == null || remoteRepositories.isEmpty()) {
			// no remote repositories
			return true;
		}

		// now is the hard part
		Properties lastUpdated = loadLastUpdated(localRepository, artifact);

		for (ArtifactRepository repository : remoteRepositories) {
			String timestamp = lastUpdated.getProperty(getLastUpdatedKey(repository, artifact));
			if (timestamp == null) {
				// availability of the artifact from this repository has not
				// been checked yet
				return false;
			}
		}

		// artifact is not available locally and all remote repositories have
		// been checked in the past
		return true;
	}

	private String getLastUpdatedKey(ArtifactRepository repository, Artifact artifact) {
		StringBuilder key = new StringBuilder();

		// repository part
		key.append(repository.getId());
		if (repository.getAuthentication() != null) {
			key.append('|').append(repository.getAuthentication().getUsername());
		}
		key.append('|').append(repository.getUrl());

		// artifact part
		key.append('|').append(artifact.getClassifier());

		return key.toString();
	}

	private Properties loadLastUpdated(ArtifactRepository localRepository, Artifact artifact) throws MavenException {
		Properties lastUpdated = new Properties();
		File lastUpdatedFile = getLastUpdatedFile(localRepository, artifact);
		try {
			BufferedInputStream is = new BufferedInputStream(new FileInputStream(lastUpdatedFile));
			try {
				lastUpdated.load(is);
			} finally {
				IOUtil.close(is);
			}
		} catch (FileNotFoundException ex) {
			// that's okay
		} catch (IOException ex) {
			throw new MavenException(ex);
		}
		return lastUpdated;
	}

	private File getLastUpdatedFile(ArtifactRepository localRepository, Artifact artifact) {
		return new File(localRepository.getBasedir(), basePathOf(localRepository, artifact) + "/" //$NON-NLS-1$
				+ "m2e-lastUpdated.properties"); //$NON-NLS-1$
	}

	private static final char PATH_SEPARATOR = '/';

	private static final char GROUP_SEPARATOR = '.';

	private String basePathOf(ArtifactRepository repository, Artifact artifact) {
		StringBuilder path = new StringBuilder(128);

		path.append(formatAsDirectory(artifact.getGroupId())).append(PATH_SEPARATOR);
		path.append(artifact.getArtifactId()).append(PATH_SEPARATOR);
		path.append(artifact.getBaseVersion()).append(PATH_SEPARATOR);

		return path.toString();
	}

	private String formatAsDirectory(String directory) {
		return directory.replace(GROUP_SEPARATOR, PATH_SEPARATOR);
	}

	public ArtifactRepository createArtifactRepository(String id, String url) throws MavenException {
		Repository repository = new Repository();
		repository.setId(id);
		repository.setUrl(url);
		repository.setLayout("default"); //$NON-NLS-1$

		ArtifactRepository repo;
		try {
			repo = lookup(RepositorySystem.class).buildArtifactRepository(repository);
			ArrayList<ArtifactRepository> repos = new ArrayList<ArtifactRepository>(Arrays.asList(repo));
			injectSettings(repos);
		} catch (InvalidRepositoryException ex) {
			throw new MavenException(ex);
		}
		return repo;
	}

	public List<ArtifactRepository> getArtifactRepositories() throws MavenException {
		return getArtifactRepositories(true);
	}

	public List<ArtifactRepository> getArtifactRepositories(boolean injectSettings) throws MavenException {
		ArrayList<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
		for (Profile profile : getActiveProfiles()) {
			addArtifactRepositories(repositories, profile.getRepositories());
		}

		addDefaultRepository(repositories);

		if (injectSettings) {
			injectSettings(repositories);
		}

		return removeDuplicateRepositories(repositories);
	}

	private List<ArtifactRepository> removeDuplicateRepositories(ArrayList<ArtifactRepository> repositories) {
		ArrayList<ArtifactRepository> result = new ArrayList<ArtifactRepository>();

		HashSet<String> keys = new HashSet<String>();
		for (ArtifactRepository repository : repositories) {
			StringBuilder key = new StringBuilder();
			if (repository.getId() != null) {
				key.append(repository.getId());
			}
			key.append(':').append(repository.getUrl()).append(':');
			if (repository.getAuthentication() != null && repository.getAuthentication().getUsername() != null) {
				key.append(repository.getAuthentication().getUsername());
			}
			if (keys.add(key.toString())) {
				result.add(repository);
			}
		}
		return result;
	}

	private void injectSettings(ArrayList<ArtifactRepository> repositories) throws MavenException {
		Settings settings = getSettings();
		RepositorySystem repositorySystem = lookup(RepositorySystem.class);
		repositorySystem.injectMirror(repositories, getMirrors());
		repositorySystem.injectProxy(repositories, settings.getProxies());
		repositorySystem.injectAuthentication(repositories, settings.getServers());
	}

	private void addDefaultRepository(ArrayList<ArtifactRepository> repositories) throws MavenException {
		for (ArtifactRepository repository : repositories) {
			if (RepositorySystem.DEFAULT_REMOTE_REPO_ID.equals(repository.getId())) {
				return;
			}
		}
		try {
			repositories.add(0, lookup(RepositorySystem.class).createDefaultRemoteRepository());
		} catch (InvalidRepositoryException ex) {
			log.error("Unexpected exception", ex);
		}
	}

	private void addArtifactRepositories(ArrayList<ArtifactRepository> artifactRepositories,
			List<Repository> repositories) throws MavenException {
		for (Repository repository : repositories) {
			try {
				ArtifactRepository artifactRepository = lookup(RepositorySystem.class)
						.buildArtifactRepository(repository);
				artifactRepositories.add(artifactRepository);
			} catch (InvalidRepositoryException ex) {
				throw new MavenException(ex);
			}
		}
	}

	private List<Profile> getActiveProfiles() throws MavenException {
		Settings settings = getSettings();
		List<String> activeProfilesIds = settings.getActiveProfiles();
		ArrayList<Profile> activeProfiles = new ArrayList<Profile>();
		for (org.apache.maven.settings.Profile settingsProfile : settings.getProfiles()) {
			if ((settingsProfile.getActivation() != null && settingsProfile.getActivation().isActiveByDefault())
					|| activeProfilesIds.contains(settingsProfile.getId())) {
				Profile profile = SettingsUtils.convertFromSettingsProfile(settingsProfile);
				activeProfiles.add(profile);
			}
		}
		return activeProfiles;
	}

	public List<ArtifactRepository> getPluginArtifactRepositories() throws MavenException {
		return getPluginArtifactRepositories(true);
	}

	public List<ArtifactRepository> getPluginArtifactRepositories(boolean injectSettings) throws MavenException {
		ArrayList<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
		for (Profile profile : getActiveProfiles()) {
			addArtifactRepositories(repositories, profile.getPluginRepositories());
		}
		addDefaultRepository(repositories);

		if (injectSettings) {
			injectSettings(repositories);
		}

		return removeDuplicateRepositories(repositories);
	}

	public Mirror getMirror(ArtifactRepository repo) throws MavenException {
		MavenExecutionRequest request = createExecutionRequest();
		populateDefaults(request);
		return lookup(RepositorySystem.class).getMirror(repo, request.getMirrors());
	};

	public void populateDefaults(MavenExecutionRequest request) throws MavenException {
		try {
			lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
		} catch (MavenExecutionRequestPopulationException ex) {
			throw new MavenException(ex);
		}
	}

	public List<Mirror> getMirrors() throws MavenException {
		MavenExecutionRequest request = createExecutionRequest();
		populateDefaults(request);
		return request.getMirrors();
	}

	public PlexusContainer getPlexusContainer() throws MavenException {
		try {
			return getPlexusContainer0();
		} catch (PlexusContainerException ex) {
			throw new MavenException(ex);
		}
	}

	private synchronized PlexusContainer getPlexusContainer0() throws PlexusContainerException {
		if (plexus == null) {
			plexus = newPlexusContainer();
//			plexus.setLoggerManager(new LoggerManager(mavenConfiguration));
		}
		return plexus;
	}

	public List<MavenProject> getSortedProjects(List<MavenProject> projects) throws MavenException {
		try {
			ProjectSorter rm = new ProjectSorter(projects);
			return rm.getSortedProjects();
		} catch (CycleDetectedException ex) {
			throw new MavenException(ex);
		} catch (DuplicateProjectException ex) {
			throw new MavenException(ex);
		}
	}

	public String resolvePluginVersion(String groupId, String artifactId, MavenSession session) throws MavenException {
		Plugin plugin = new Plugin();
		plugin.setGroupId(groupId);
		plugin.setArtifactId(artifactId);
		PluginVersionRequest request = new DefaultPluginVersionRequest(plugin, session);
		try {
			return lookup(PluginVersionResolver.class).resolve(request).getVersion();
		} catch (PluginVersionResolutionException ex) {
			throw new MavenException(ex);
		}
	}

	/* package */ <T> T lookup(Class<T> clazz) throws MavenException {
		try {
			return getPlexusContainer().lookup(clazz);
		} catch (ComponentLookupException ex) {
			throw new MavenException(ex);
		}
	}

	/**
	 * @throws MavenException
	 * @since 1.5
	 */
	public <T> T lookupComponent(Class<T> clazz) throws MavenException {
		try {
			return getPlexusContainer0().lookup(clazz);
		} catch (ComponentLookupException ex) {
			throw new MavenException(ex);
		} catch (PlexusContainerException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static DefaultPlexusContainer newPlexusContainer() throws PlexusContainerException {
		final ClassWorld classWorld = new ClassWorld(MAVEN_CORE_REALM_ID, ClassWorld.class.getClassLoader());
		final ClassRealm realm;
		try {
			realm = classWorld.getRealm(MAVEN_CORE_REALM_ID);
		} catch (NoSuchRealmException e) {
			throw new PlexusContainerException("Could not lookup required class realm", e);
		}
		final ContainerConfiguration mavenCoreCC = new DefaultContainerConfiguration() //
				.setClassWorld(classWorld) //
				.setRealm(realm) //
				.setClassPathScanning(PlexusConstants.SCANNING_INDEX) //
				.setAutoWiring(true) //
				.setName("mavenCore"); //$NON-NLS-1$

		final Module logginModule = new AbstractModule() {
			protected void configure() {
				bind(ILoggerFactory.class).toInstance(LoggerFactory.getILoggerFactory());
			}
		};
		final Module coreExportsModule = new AbstractModule() {
			protected void configure() {
				ClassRealm realm = mavenCoreCC.getRealm();
				CoreExtensionEntry entry = CoreExtensionEntry.discoverFrom(realm);
				CoreExports exports = new CoreExports(entry);
				bind(CoreExports.class).toInstance(exports);
			}
		};
		return new DefaultPlexusContainer(mavenCoreCC, logginModule, coreExportsModule);
	}

	public synchronized void disposeContainer() {
		if (plexus != null) {
			plexus.dispose();
		}
	}

	public ClassLoader getProjectRealm(MavenProject project) {
		ClassLoader classLoader = project.getClassRealm();
		if (classLoader == null) {
			classLoader = plexus.getContainerRealm();
		}
		return classLoader;
	}

	public void interpolateModel(MavenProject project, Model model) throws MavenException {
		ModelBuildingRequest request = new DefaultModelBuildingRequest();
		request.setUserProperties(project.getProperties());
		ModelProblemCollector problems = new ModelProblemCollector() {
			@Override
			public void add(ModelProblemCollectorRequest req) {
			}
		};
		lookup(ModelInterpolator.class).interpolateModel(model, project.getBasedir(), request, problems);
	}

	/**
	 * Thread-safe properties copy implementation.
	 * <p>
	 * {@link Properties#entrySet()} iterator is not thread safe and fails with
	 * {@link ConcurrentModificationException} if the source properties "is
	 * structurally modified at any time after the iterator is created". The
	 * solution is to use thread-safe {@link Properties#stringPropertyNames()}
	 * enumerate and copy properties.
	 * 
	 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=440696
	 * @since 1.6
	 */
	public static void copyProperties(Properties to, Properties from) {
		for (String key : from.stringPropertyNames()) {
			String value = from.getProperty(key);
			if (value != null) {
				to.put(key, value);
			}
		}
	}
	
	public Artifact resolve(String groupId, String artifactId, String version, String type, String classifier,
			List<ArtifactRepository> remoteRepositories, MavenExecutionRequest request) throws MavenException {
		Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version,
				type, classifier);

		return resolve(artifact, remoteRepositories, request);
	}

	public Artifact resolve(final Artifact artifact, List<ArtifactRepository> remoteRepositories,
			MavenExecutionRequest executionRequest) throws MavenException {
		if (remoteRepositories == null) {
			try {
				remoteRepositories = getArtifactRepositories();
			} catch (MavenException e) {
				// we've tried
				remoteRepositories = Collections.emptyList();
			}
		}
		final List<ArtifactRepository> _remoteRepositories = remoteRepositories;

		org.eclipse.aether.RepositorySystem repoSystem = lookup(org.eclipse.aether.RepositorySystem.class);

		ArtifactRequest request = new ArtifactRequest();
		request.setArtifact(RepositoryUtils.toArtifact(artifact));
		request.setRepositories(RepositoryUtils.toRepos(_remoteRepositories));

		ArtifactResult result;
		try {
			result = repoSystem.resolveArtifact(createRepositorySession(executionRequest), request);
		} catch (ArtifactResolutionException ex) {
			result = ex.getResults().get(0);
		}

		setLastUpdated(executionRequest.getLocalRepository(), _remoteRepositories, artifact);

		if (result.isResolved()) {
			artifact.selectVersion(result.getArtifact().getVersion());
			artifact.setFile(result.getArtifact().getFile());
			artifact.setResolved(true);
		} else {
			throw new MavenException(result.getExceptions().toArray(new Exception[result.getExceptions().size()]));
		}

		return artifact;
	}

	/* package */void setLastUpdated(ArtifactRepository localRepository, List<ArtifactRepository> remoteRepositories,
			Artifact artifact) throws MavenException {

		Properties lastUpdated = loadLastUpdated(localRepository, artifact);

		String timestamp = Long.toString(System.currentTimeMillis());

		for (ArtifactRepository repository : remoteRepositories) {
			lastUpdated.setProperty(getLastUpdatedKey(repository, artifact), timestamp);
		}

		File lastUpdatedFile = getLastUpdatedFile(localRepository, artifact);
		try {
			lastUpdatedFile.getParentFile().mkdirs();
			BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(lastUpdatedFile));
			try {
				lastUpdated.store(os, null);
			} finally {
				IOUtil.close(os);
			}
		} catch (IOException ex) {
			throw new MavenException(ex);
		}
	}

}
