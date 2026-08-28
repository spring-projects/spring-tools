/*******************************************************************************
 * Copyright (c) 2013, 2016 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.internal;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.ARTIFACT_ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.CLASSIFIER;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCIES;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.DEPENDENCY_MANAGEMENT;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.GROUP_ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.NAME;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.OPTIONAL;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.SCOPE;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.TYPE;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.URL;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.VERSION;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.childEquals;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.createElement;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.createElementWithText;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChild;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChilds;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.format;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.getChild;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.getTextValue;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.performOnDOMDocument;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.RefreshTab;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.m2e.actions.MavenLaunchConstants;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.Operation;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.OperationTuple;
import org.eclipse.m2e.core.ui.internal.editing.PomHelper;
import org.springframework.ide.eclipse.boot.core.Bom;
import org.springframework.ide.eclipse.boot.core.IMavenCoordinates;
import org.springframework.ide.eclipse.boot.core.MavenCoordinates;
import org.springframework.ide.eclipse.boot.core.MavenId;
import org.springframework.ide.eclipse.boot.core.Repo;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.core.SpringBootStarter;
import org.springframework.ide.eclipse.boot.core.SpringBootStarters;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrService;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrUrl;
import org.springframework.ide.eclipse.boot.util.DependencyDelta;
import org.springframework.ide.eclipse.boot.util.DumpOutput;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.ui.launch.LaunchUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class MavenSpringBootProject extends SpringBootProject {

	private static final String JAVA_VERSION = "java.version";

	/**
	 * Debug flag, may be flipped on temporarily by test code to spy on the output of maven
	 * execution.
	 */
	public static boolean DUMP_MAVEN_OUTPUT = false;

	private static final String MVN_LAUNCH_MODE = "run";

	private static final String REPOSITORIES = "repositories";
	private static final String REPOSITORY = "repository";
	private static final String SNAPSHOTS = "snapshots";

	private static final String ENABLED = "enabled";

	public MavenSpringBootProject(IProject project, InitializrService initializr) {
		super(project, initializr);
	}

	private MavenProject getMavenProject() throws CoreException {
		IMavenProjectFacade mpf = getMavenProjectFacade();
		if (mpf != null) {
			return mpf.getMavenProject(new NullProgressMonitor());
		}
		return null;
	}

	private IMavenProjectFacade getMavenProjectFacade() {
		IMavenProjectRegistry pr = MavenPlugin.getMavenProjectRegistry();
		IMavenProjectFacade mpf = pr.getProject(project);
		return mpf;
	}

	private IFile getPomFile() {
		return project.getFile(new Path("pom.xml"));
	}

	@Override
	public List<IMavenCoordinates> getDependencies() throws CoreException {
		MavenProject mp = getMavenProject();
		if (mp != null) {
			return toMavenCoordinates(mp.getDependencies());
		}
		return Collections.emptyList();
	}

	private List<IMavenCoordinates> toMavenCoordinates(List<Dependency> dependencies) {
		ArrayList<IMavenCoordinates> converted = new ArrayList<>(dependencies.size());
		for (Dependency d : dependencies) {
			converted.add(new MavenCoordinates(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getVersion()));
		}
		return converted;
	}

	/**
	 * Determine the 'managed' version, if any, associate with a given dependency.
	 * @return Version string or null.
	 */
	private String getManagedVersion(IMavenCoordinates dep) {
		try {
			MavenProject mp = getMavenProject();
			if (mp != null) {
				DependencyManagement managedDeps = mp.getDependencyManagement();
				if (managedDeps != null) {
					List<Dependency> deps = managedDeps.getDependencies();
					if (deps != null && !deps.isEmpty()) {
						for (Dependency d : deps) {
							if ("jar".equals(d.getType())) {
								if (dep.getArtifactId().equals(d.getArtifactId()) && dep.getGroupId().equals(d.getGroupId())) {
									return d.getVersion();
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	@Override
	public void addMavenDependency(final IMavenCoordinates dep, final boolean preferManagedVersion) throws CoreException {
		addMavenDependency(dep, preferManagedVersion, false);
	}

	@Override
	public void addMavenDependency(
			final IMavenCoordinates dep,
			final boolean preferManagedVersion, final boolean optional
	) throws CoreException {
		try {
			IFile file = getPomFile();
			performOnDOMDocument(new OperationTuple(file, new Operation() {
				@Override
				public void process(Document document) {
					Element depsEl = getChild(
							document.getDocumentElement(), DEPENDENCIES);
					if (depsEl == null) {
						//TODO: handle this case
					} else {
						String version = dep.getVersion();
						String managedVersion = getManagedVersion(dep);
						if (managedVersion != null) {
							//Decide whether we can/should inherit the managed version or override it.
							if (preferManagedVersion || managedVersion.equals(version)) {
								version = null;
							}
						} else {
							//No managed version. We have to include a version in xml added to the pom.
						}
						Element xmlDep = PomHelper.createDependency(depsEl,
								dep.getGroupId(),
								dep.getArtifactId(),
								version
						);
						if (optional) {
							createElementWithText(xmlDep, OPTIONAL, "true");
							format(xmlDep);
						}
					}
				}
			}));
		} catch (Throwable e) {
			throw ExceptionUtil.coreException(e);
		}
	}

	@Override
	public void modifyDependencies(DependencyDelta delta) throws CoreException {
		try {
			IFile file = getPomFile();
			SpringBootStarters knownStarters = getStarterInfos();
			performOnDOMDocument(new OperationTuple(file, new Operation() {
				@Override
				public void process(Document pom) {
					Element depsEl = getChild(
							pom.getDocumentElement(), DEPENDENCIES);
					List<Element> children = findChilds(depsEl, DEPENDENCY);
					//Remove dependencies the delta says should be removed:
					for (Element c : children) {
						String aid = getTextValue(findChild(c, ARTIFACT_ID));
						String gid = getTextValue(findChild(c, GROUP_ID));
						if (aid != null && gid != null) { //ignore invalid entries that don't have gid or aid
							MavenId id = new MavenId(gid, aid);
							if (delta.isRemoved(id)) {
								depsEl.removeChild(c);
							}
						}
					}

					//Add dependencies delta says should be added
					for (Entry<MavenId, Optional<String>> added : delta.added.entrySet()) {
						MavenId mid = added.getKey();
						String scope = added.getValue().orElse(null);
						SpringBootStarter starter = knownStarters.getStarter(mid);
						createDependency(depsEl, new MavenCoordinates(mid.getGroupId(), mid.getArtifactId()), scope);
						if (starter != null) {
							createBomIfNeeded(pom, starter.getBom());
							createRepoIfNeeded(pom, starter.getRepo());
						}
					}

					//Add boms delta says should be added
					try {
						for (MavenId bomMavenId : delta.addedBoms) {
							Bom bom = getStarterInfos().getBom(bomMavenId);
							if (bom != null) {
								createBomIfNeeded(pom, bom);
							}
						}
					} catch (Exception e) {
						// troubles reading bom infos from initializer?
						Log.log(e);
					}

					//TODO: remove boms delta says should be removed?
				}
			}));
		} catch (Throwable e) {
			throw ExceptionUtil.coreException(e);
		}
	}

	@Override
	public void removeMavenDependency(final MavenId mavenId) {
		IFile file = getPomFile();
		try {
			performOnDOMDocument(new OperationTuple(file, new Operation() {
				@Override
				public void process(Document pom) {
					Element depsEl = getChild(
							pom.getDocumentElement(), DEPENDENCIES);
					if (depsEl != null) {
						Element dep = findChild(depsEl, DEPENDENCY,
								childEquals(GROUP_ID, mavenId.getGroupId()),
								childEquals(ARTIFACT_ID, mavenId.getArtifactId())
						);
						if (dep != null) {
							depsEl.removeChild(dep);
						}
					}
				}
			}));
		} catch (Exception e) {
			Log.log(e);
		}
	}

	@Override
	public void updateProjectConfiguration() {
		//We wrap the UpdateMavenProjectJob in another job to avoid a race condition
		//that causes a deadlock. The race condition is avoided by have the
		//waitForWorkspaceLock wrapper which ensures that pending workspace jobs
		//are finished before triggering maven project update.
		//See: https://github.com/spring-projects/sts4/issues/780
		Job waitForWorkspaceLock = new Job("Wait for workspace") {
			{
				setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
			}

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				Job job = createUpdateMavenProjectJob(getProject());
				if (job != null) {
					job.schedule(2000); //Without 2000ms delay, we tend to get deadlocks.
					// TODO:              ^^^^ debug this and find a better solution (suspect this actually bug in m2e,
					//                        but it rarely happens when a user invokes the operation from the UI.
					//                        It happens here presumably because some updates happen in quick succession
					//                        and this causes some sort of a race condition causing a deadlock situation.
					return Status.OK_STATUS;
				}
				else {
					return Status.error("internal error creating update job for maven project");
				}
			}

		};

		waitForWorkspaceLock.schedule();
 	}

	@Override
	public String getBootVersion() {
		try {
			MavenProject mp = getMavenProject();
			if (mp != null) {
				return getBootVersion(mp.getDependencies());
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return SpringBootCore.getDefaultBootVersion();
	}

	private String getBootVersion(List<Dependency> dependencies) {
		for (Dependency dep : dependencies) {
			if (dep.getArtifactId().startsWith("spring-boot") && dep.getGroupId().equals("org.springframework.boot")) {
				return dep.getVersion();
			}
		}
		return SpringBootCore.getDefaultBootVersion();
	}

	/**
	 * Helper method to create the job to update maven projects.
	 *
	 * Due to m2e 2.0 changing the signature of the constructor (parameter from IProject[] to Collection<IProject>)
	 * we need to call the constructor via reflection to allow this code to work with m2e 1.x and m2e 2.x at the same time
	 */
	private UpdateMavenProjectJob createUpdateMavenProjectJob(IProject project) {
		Object args = new IProject[] {project};

		try {
			// check for m2e 1.x version (public UpdateMavenProjectJob(IProject[] projects)
			Constructor<UpdateMavenProjectJob> constructor = UpdateMavenProjectJob.class.getConstructor(IProject[].class);
			return constructor.newInstance(args);
		} catch (Exception e) {
		}

		try {
			// check for m2e 2.x version (public UpdateMavenProjectJob(Collection<IProject> projects)
			Constructor<UpdateMavenProjectJob> constructor = UpdateMavenProjectJob.class.getConstructor(Collection.class);
			return constructor.newInstance(Collections.singleton(project));
		} catch (Exception e) {
		}

		return null;
	}

	private void createRepoIfNeeded(Document pom, Repo repo) {
		if (repo != null) {
			addReposIfNeeded(pom, Collections.singletonList(repo));
		}
	}

	private void createBomIfNeeded(Document pom, Bom bom) {
		if (bom != null) {
			Element bomList = ensureDependencyMgmtSection(pom);
			Element existing = PomEdits.findChild(bomList, DEPENDENCY,
					childEquals(GROUP_ID, bom.getGroupId()),
					childEquals(ARTIFACT_ID, bom.getArtifactId())
			);
			if (existing == null) {
				createBom(bomList, bom);
				addReposIfNeeded(pom, bom.getRepos());
			}
		}
	}

	private Element ensureDependencyMgmtSection(Document pom) {
		/* Ensure that this exists in the pom:
	<dependencyManagement>
		<dependencies> <---- RETURNED
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-starter-parent</artifactId>
				<version>Brixton.M3</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
		 */
		boolean needFormatting = false;
		Element doc = pom.getDocumentElement();
		Element depman = findChild(doc, DEPENDENCY_MANAGEMENT);
		if (depman == null) {
			depman = createElement(doc, DEPENDENCY_MANAGEMENT);
			needFormatting = true;
		}
		Element deplist = findChild(depman, DEPENDENCIES);
		if (deplist == null) {
			deplist = createElement(depman, DEPENDENCIES);
		}
		if (needFormatting) {
			format(depman);
		}
		return deplist;
	}

	private static Element createBom(Element parentList, Bom bom) {
		/*
	<dependencyManagement>
		<dependencies> <---- parentList
			<dependency> <---- create and return
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-starter-parent</artifactId>
				<version>Brixton.M3</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
		 */

		String groupId = bom.getGroupId();
		String artifactId = bom.getArtifactId();
		String version = bom.getVersion();
		String classifier = bom.getClassifier();
		String type = "pom";
		String scope = "import";

		Element dep = createElement(parentList, DEPENDENCY);

		if (groupId != null) {
			createElementWithText(dep, GROUP_ID, groupId);
		}
		createElementWithText(dep, ARTIFACT_ID, artifactId);
		if (version != null) {
			createElementWithText(dep, VERSION, version);
		}
		createElementWithText(dep, TYPE, type);
		if (scope != null && !scope.equals("compile")) {
			createElementWithText(dep, SCOPE, scope);
		}
		if (classifier != null) {
			createElementWithText(dep, CLASSIFIER, classifier);
		}
		format(dep);
		return dep;
	}

	/**
	 * creates and adds new dependency to the parent. formats the result.
	 */
	private Element createDependency(Element parentList, IMavenCoordinates info, String scope) {
		Element dep = createElement(parentList, DEPENDENCY);
		String groupId = info.getGroupId();
		String artifactId = info.getArtifactId();
		String version = info.getVersion();
		String classifier = info.getClassifier();

		if (groupId != null) {
			createElementWithText(dep, GROUP_ID, groupId);
		}
		createElementWithText(dep, ARTIFACT_ID, artifactId);
		if (version != null) {
			createElementWithText(dep, VERSION, version);
		}
		if (classifier != null) {
			createElementWithText(dep, CLASSIFIER, classifier);
		}
		if (scope != null && !scope.equals("compile")) {
			createElementWithText(dep, SCOPE, scope);
		}
		format(dep);
		return dep;
	}

	private void addReposIfNeeded(Document pom, List<Repo> repos) {
		//Example:
		//	<repositories>
		//		<repository>
		//			<id>spring-snapshots</id>
		//			<name>Spring Snapshots</name>
		//			<url>https://repo.spring.io/snapshot</url>
		//			<snapshots>
		//				<enabled>true</enabled>
		//			</snapshots>
		//		</repository>
		//		<repository>
		//			<id>spring-milestones</id>
		//			<name>Spring Milestones</name>
		//			<url>https://repo.spring.io/milestone</url>
		//			<snapshots>
		//				<enabled>false</enabled>
		//			</snapshots>
		//		</repository>
		//	</repositories>

		if (repos != null && !repos.isEmpty()) {
			Element doc = pom.getDocumentElement();
			Element repoList = findChild(doc, REPOSITORIES);
			if (repoList == null) {
				repoList = createElement(doc, REPOSITORIES);
				format(repoList);
			}
			for (Repo repo : repos) {
				String id = repo.getId();
				Element repoEl = findChild(repoList, REPOSITORY, childEquals(ID, id));
				if (repoEl == null) {
					repoEl = createElement(repoList, REPOSITORY);
					createElementWithTextMaybe(repoEl, ID, id);
					createElementWithTextMaybe(repoEl, NAME, repo.getName());
					createElementWithTextMaybe(repoEl, URL, repo.getUrl());
					Boolean isSnapshot = repo.getSnapshotEnabled();
					if (isSnapshot != null) {
						Element snapshot = createElement(repoEl, SNAPSHOTS);
						createElementWithText(snapshot, ENABLED, isSnapshot.toString());
					}
					format(repoEl);
				}
			}
		}
	}

	private void createElementWithTextMaybe(Element parent, String name, String text) {
		if (StringUtils.isNotBlank(text)) {
			createElementWithText(parent, name, text);
		}
	}

	@Override
	public String getDependencyFileName() {
		return "pom.xml";
	}

	@Override
	public String getPackaging() throws CoreException {
		MavenProject mp = getMavenProject();
		if (mp != null) {
			return mp.getPackaging();
		}
		return null;
	}

	@Override
	public File executePackagingScript(IProgressMonitor monitor) throws CoreException {
		monitor.beginTask("Building War file", 100);
		try {
			ILaunchConfiguration launchConf = createLaunchConfiguration(project, "package");
			ILaunch launch = launchConf.launch(MVN_LAUNCH_MODE, SubMonitor.convert(monitor, 10), true, true);
			if (DUMP_MAVEN_OUTPUT) {
				launch.getProcesses()[0].getStreamsProxy().getOutputStreamMonitor().addListener(new DumpOutput("%mvn-out"));
				launch.getProcesses()[0].getStreamsProxy().getErrorStreamMonitor().addListener(new DumpOutput("%mvn-err"));
			}

			LaunchUtils.whenTerminated(launch).get();
			int exitValue = launch.getProcesses()[0].getExitValue();
			if (exitValue != 0) {
				throw ExceptionUtil.coreException("Non-zero exit-code("+exitValue+") from maven war packaging. Check maven console for errors!");
			}
			return findWarFile();
		} catch (ExecutionException | InterruptedException e) {
			throw ExceptionUtil.coreException(e);
		} finally {
			monitor.done();
		}
	}

	private File findWarFile() throws CoreException {
		File warFile = getWarFile();
		if (warFile == null) {
			throw ExceptionUtil.coreException("Couldn't determine where to find the war file after 'mvn package'");
		} else if (!warFile.isFile()) {
			throw ExceptionUtil.coreException("Couldn't find file to deploy at '"+warFile+"' after running 'mvn package'");
		}
		return warFile;
	}

	private File getWarFile() throws CoreException {
		MavenProject mpf = getMavenProject();
		if (mpf != null) {
			String buildDir = mpf.getBuild().getDirectory();
			String fName = mpf.getBuild().getFinalName();
			String type = mpf.getPackaging();
			if (buildDir != null && fName != null && type != null) {
				return new File(new File(buildDir), fName+"."+type);
			}
		}
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////////////
	/// m2e cruft... copied from hither and tither.

	private ILaunchConfiguration createLaunchConfiguration(IContainer basedir, String goal) {
		try {
			ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType launchConfigurationType = launchManager
					.getLaunchConfigurationType(MavenLaunchConstants.LAUNCH_CONFIGURATION_TYPE_ID);

			String rawConfigName = basedir.getName()+"-build-war";
			String safeConfigName = launchManager.generateLaunchConfigurationName(rawConfigName);

			ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType.newInstance(null, safeConfigName);
			workingCopy.setAttribute(MavenLaunchConstants.ATTR_POM_DIR, basedir.getLocation().toOSString());
			workingCopy.setAttribute(MavenLaunchConstants.ATTR_GOALS, goal);
			workingCopy.setAttribute(MavenLaunchConstants.ATTR_SKIP_TESTS, true);
			workingCopy.setAttribute(IDebugUIConstants.ATTR_PRIVATE, true);
			workingCopy.setAttribute(RefreshTab.ATTR_REFRESH_SCOPE, "${project}"); //$NON-NLS-1$
			workingCopy.setAttribute(RefreshTab.ATTR_REFRESH_RECURSIVE, true);

			setProjectConfiguration(workingCopy, basedir);

			IPath path = getJREContainerPath(basedir);
			if (path != null) {
				workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, path.toPortableString());
			}

			// TODO when launching Maven with debugger consider to add the following property
			// -Dmaven.surefire.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -Xnoagent -Djava.compiler=NONE"

			return workingCopy;
		} catch(CoreException ex) {
			Log.log(ex);
		}
		return null;
	}

	// TODO ideally it should use MavenProject, but it is faster to scan IJavaProjects
	private IPath getJREContainerPath(IContainer basedir) throws CoreException {
		IProject project = basedir.getProject();
		if (project != null && project.hasNature(JavaCore.NATURE_ID)) {
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] entries = javaProject.getRawClasspath();
			for(int i = 0; i < entries.length; i++ ) {
				IClasspathEntry entry = entries[i];
				if (JavaRuntime.JRE_CONTAINER.equals(entry.getPath().segment(0))) {
					return entry.getPath();
				}
			}
		}
		return null;
	}

	private void setProjectConfiguration(ILaunchConfigurationWorkingCopy workingCopy, IContainer basedir) {
		IMavenProjectRegistry projectManager = MavenPlugin.getMavenProjectRegistry();
		IFile pomFile = basedir.getFile(new Path(IMavenConstants.POM_FILE_NAME));
		IMavenProjectFacade projectFacade = projectManager.create(pomFile, false, new NullProgressMonitor());
		if (projectFacade != null) {
			ResolverConfiguration configuration = projectFacade.getResolverConfiguration();

			String selectedProfiles = configuration.getSelectedProfiles();
			if (selectedProfiles != null && selectedProfiles.length() > 0) {
				workingCopy.setAttribute(MavenLaunchConstants.ATTR_PROFILES, selectedProfiles);
			}
		}
	}

	@Override
	protected Map<String, Object> pomGenerationParameters(
			List<org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency> initialDependencies)
			throws Exception {
		Map<String, Object> parameters = super.pomGenerationParameters(initialDependencies);
		parameters.put("type", InitializrUrl.MAVEN_PROJECT);
		parameters.put("language", "java");
		MavenProject mavenProject = getMavenProject();
		if (mavenProject != null) {
			parameters.put("description", mavenProject.getDescription());
			parameters.put("groupId", mavenProject.getGroupId());
			parameters.put("artifactId", mavenProject.getArtifactId());
			parameters.put("version", mavenProject.getVersion());
			parameters.put("packaging", mavenProject.getPackaging());
			parameters.put("javaVerion", mavenProject.getProperties().get(JAVA_VERSION));
		}
		return parameters;
	}

	@Override
	public String buildType() {
		return InitializrUrl.MAVEN_PROJECT;
	}

	@Override
	public String artifactId() throws CoreException {
		MavenProject mavenProject = getMavenProject();
		return mavenProject == null ? null : mavenProject.getArtifactId();
	}

	@Override
	public String groupId() throws CoreException {
		MavenProject mavenProject = getMavenProject();
		return mavenProject == null ? null : mavenProject.getGroupId();
	}

	@Override
	public String javaVersion() throws CoreException {
		MavenProject mavenProject = getMavenProject();
		return mavenProject == null ? null : (String) mavenProject.getProperties().get(JAVA_VERSION);
	}

	@Override
	public String version() throws CoreException {
		MavenProject mavenProject = getMavenProject();
		return mavenProject == null ? null : mavenProject.getVersion();
	}

	@Override
	public String description() throws CoreException {
		MavenProject mavenProject = getMavenProject();
		return mavenProject == null ? null : mavenProject.getDescription();
	}

}
