/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.springsource.ide.eclipse.commons.livexp.ui.ProjectLocationSection.getDefaultProjectLocation;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.test.util.CopyFromFolder;
import org.springframework.ide.eclipse.boot.util.RetryUtil;
import org.springframework.ide.eclipse.boot.wizard.NewSpringBootWizardModel;
import org.springframework.ide.eclipse.boot.wizard.RadioGroup;
import org.springframework.ide.eclipse.boot.wizard.RadioInfo;
import org.springframework.ide.eclipse.boot.wizard.content.BuildType;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportConfiguration;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategies;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategy;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersInitializrService;
import org.springsource.ide.eclipse.commons.core.IRunnableWithProgressAndResult;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class BootProjectTestHarness {

	private static final boolean DEBUG = true;

	private String[] supportedBootVersions = null;
	private String latestReleaseVersion = null;

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}


	public static final long BOOT_PROJECT_CREATION_TIMEOUT = 2*60*1000; // long, may download maven dependencies

	private IWorkspace workspace;

	public BootProjectTestHarness(IWorkspace workspace) {
		this.workspace = workspace;
	}

	@FunctionalInterface
	public interface WizardConfigurer {

		void apply(NewSpringBootWizardModel wizard);

		WizardConfigurer NULL = new WizardConfigurer(){
			@Override
			public void apply(NewSpringBootWizardModel wizard) {/*do nothing*/}
		};
	}

	public static WizardConfigurer withImportStrategy(final String id) {
		final ImportStrategy is = ImportStrategies.withId(id);
		Assert.isNotNull(is, "Import strategy not found: "+id+ " in "+ImportStrategies.all());
		return new WizardConfigurer() {
			@Override
			public void apply(NewSpringBootWizardModel wizard) {
				wizard.setImportStrategy(is);
			}
		};
	}

	public static WizardConfigurer withPackaging(final String packagingTypeName) {
		return (wizard) -> {
			RadioGroup packagingRadio = wizard.getRadioGroups().getGroup("packaging");
			assertNotNull("Couldn't find 'packaging' radiogroup in the wizard model", packagingRadio);
			for (RadioInfo r : packagingRadio.getRadios()) {
				if (r.getValue().equals(packagingTypeName)) {
					packagingRadio.getSelection().selection.setValue(r);
					return;
				}
			}
			fail("Couldn't find packaging type '"+packagingTypeName+"' in the wizard model");
		};
	}

	public static WizardConfigurer withLanguage(final String language) {
		return (wizard) -> {
			RadioGroup languageRadio = wizard.getRadioGroups().getGroup("language");
			assertNotNull("Couldn't find 'language' radiogroup in the wizard model", languageRadio);
			RadioInfo l = languageRadio.getRadio(language);
			assertNotNull("Couldn't find language '"+language+"' in the wizard model", l);
			languageRadio.setValue(l);
		};
	}

	public static WizardConfigurer withStarters(final String... ids) {
		if (ids.length > 0) {
			return new WizardConfigurer() {
				@Override
				public void apply(NewSpringBootWizardModel wizard) {
					for (String id : ids) {
						wizard.addDependency(id);
					}
				}
			};
		}
		return WizardConfigurer.NULL;
	}

	public static WizardConfigurer setPackage(final String pkgName) {
		return new WizardConfigurer() {
			@Override
			public void apply(NewSpringBootWizardModel wizard) {
				wizard.getStringInput("packageName").setValue(pkgName);
			}
		};
	}

	/**
	 * @return A wizard configurer that ensures the selected 'boot version' is exactly
	 * a given version of boot.
	 */
	public static WizardConfigurer bootVersion(final String wantedVersion) throws Exception {
		return new WizardConfigurer() {
			@Override
			public void apply(NewSpringBootWizardModel wizard) {
				RadioGroup bootVersionRadio = wizard.getBootVersion();
				for (RadioInfo option : bootVersionRadio.getRadios()) {
					if (option.getValue().equals(wantedVersion)) {
						bootVersionRadio.setValue(option);
						return;
					}
				}
				fail("The wanted bootVersion '"+wantedVersion+"'is not found in the wizard");
			}
		};
	}

	public static WizardConfigurer withJavaVersion(String version) {
		return new WizardConfigurer() {
			@Override
			public void apply(NewSpringBootWizardModel wizard) {
				List<String> availableVersions = new ArrayList<>();
				RadioGroup javaVersionRadio = wizard.getJavaVersion();
				RadioInfo[] radios = javaVersionRadio.getRadios();
				for (RadioInfo option : radios) {
					if (option.getValue().equals(version)) {
						javaVersionRadio.setValue(option);
						return;
					}
					availableVersions.add(option.getValue());
				}
				fail("Java version '"+version+"' not found in "+availableVersions);
			}
		};

	}

	public static WizardConfigurer latestBootReleaseVersion() throws Exception {
		return new WizardConfigurer() {
			@Override
			public void apply(NewSpringBootWizardModel wizard) {
				RadioGroup bootVersionRadio = wizard.getBootVersion();
				RadioInfo[] radios = bootVersionRadio.getRadios();
				for (RadioInfo option : radios) {
					if (AddStartersInitializrService.isRelease(option.getValue())) {
						bootVersionRadio.setValue(option);
						return;
					}
				}

				fail("No boot versions found in the wizard. Unable to set the latest release version");
			}
		};
	}

	/**
	 * @return A wizard configurer that ensures the selected 'boot version' is at least
	 * a given version of boot.
	 */
	public static WizardConfigurer bootVersionAtLeast(final String wantedVersion) throws Exception {
		final VersionRange WANTED_RANGE = new VersionRange(wantedVersion);
		return new WizardConfigurer() {
			@Override
			public void apply(NewSpringBootWizardModel wizard) {
				RadioGroup bootVersionRadio = wizard.getBootVersion();
				RadioInfo selected = bootVersionRadio.getValue();
				Version selectedVersion = getVersion(selected);
				if (WANTED_RANGE.includes(selectedVersion)) {
					//existing selection is fine
				} else {
					//try to select the latest available version and verify it meets the requirement
					bootVersionRadio.setValue(selected =  getLatestVersion(bootVersionRadio, WANTED_RANGE));
					selectedVersion = getVersion(selected);
					Assert.isTrue(WANTED_RANGE.includes(selectedVersion));
				}
			}

			private RadioInfo getLatestVersion(RadioGroup bootVersionRadio, VersionRange versionRange) {
				RadioInfo[] infos = bootVersionRadio.getRadios();
				Arrays.sort(infos, new Comparator<RadioInfo>() {
					@Override
					public int compare(RadioInfo o1, RadioInfo o2) {
						Version v1 = getVersion(o1);
						Version v2 = getVersion(o2);
						return v2.compareTo(v1);
					}
				});

				for (int i = 0; i < infos.length; i++) {
					if (versionRange == null || versionRange.includes(getVersion(infos[i]))) {
						return infos[i];
					}
				}

				return null;
			}

			private Version getVersion(RadioInfo info) {
				String versionString = info.getValue();
				Version v = new Version(versionString);
				if ("BUILD-SNAPSHOT".equals(v.getQualifier())) {
					// Caveat "M1" will be treated as 'later' than "BUILD-SNAPSHOT" so that is wrong.
					return new Version(v.getMajor(), v.getMinor(), v.getMicro(), "SNAPSHOT"); //Comes after "MX" but before "RELEASE"
				}
				return v;
			}
		};
	}

	public IProject createBootWebProject(final String projectName, final WizardConfigurer... extraConfs) throws Exception {
		return createBootProject(projectName, merge(extraConfs, withStarters("web")));
	}

	private WizardConfigurer[] merge(WizardConfigurer[] confs, WizardConfigurer... moreConfs) {
		WizardConfigurer[] merged = new WizardConfigurer[confs.length + moreConfs.length];
		System.arraycopy(confs, 0, merged, 0, confs.length);
		System.arraycopy(moreConfs, 0, merged, confs.length, moreConfs.length);
		return merged;
	}

	public IProject createBootProject(final String projectName, final WizardConfigurer... _extraConfs) throws Exception {
		List<WizardConfigurer> extraConfs = new ArrayList<>(Arrays.asList(_extraConfs));
		IVMInstall2 jvm = (IVMInstall2) JavaRuntime.getDefaultVMInstall();
		String version = jvm.getJavaVersion();
		if (version.startsWith("1.8.")) {
			System.out.println("Warning! Workspace JRE is Java 8. Downgrading test project");
			extraConfs.add(withJavaVersion("1.8"));
		}
		try {
			RetryUtil.retryWhen("createBootProject("+projectName+")", 3, RetryUtil.errorWithMsg("Read timed out"), () -> {
				final Job job = new Job("Create boot project '"+projectName+"'") {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							//No point doing a retry if we will just fail because project already exists!
							IProject p = getProject(projectName);
							if (p.exists()) {
								p.delete(true, true, new NullProgressMonitor());
							}
							NewSpringBootWizardModel wizard = new NewSpringBootWizardModel(new MockPrefsStore());
							wizard.allowUIThread(true);
							wizard.getProjectName().setValue(projectName);
							wizard.getArtifactId().setValue(projectName);
							//Note: unlike most of the rest of the wizard's behavior, the 'use default location'
							//  checkbox and its effect is not part of the model but part of the GUI code (this is
							//  wrong, really, but that's how it is, so we have to explictly set the project
							//  location in the model.
							wizard.getLocation().setValue(getDefaultProjectLocation(projectName));
							for (WizardConfigurer extraConf : extraConfs) {
								extraConf.apply(wizard);
							}

							RadioInfo[] radios =  wizard.getBootVersion().getRadios();
							List<String> supportedBootVersions = new ArrayList<>();
							String latestRelease = null;
							for (RadioInfo info : radios) {
								supportedBootVersions.add(info.getValue());
								if (latestRelease == null && AddStartersInitializrService.isRelease(info.getValue())) {
									latestRelease = info.getValue();
								}
							}
							BootProjectTestHarness.this.latestReleaseVersion = latestRelease;
							BootProjectTestHarness.this.supportedBootVersions = supportedBootVersions.toArray(new String[0]);

							wizard.performFinish(new NullProgressMonitor()/*new SysOutProgressMonitor()*/);
							return Status.OK_STATUS;
						} catch (Throwable e) {
							return ExceptionUtil.status(e);
						}
					}
				};
				//job.setRule(workspace.getRuleFactory().buildRule());
				job.schedule();

				waitForImportJob(getProject(projectName), job);

			});
			return getProject(projectName);
		} finally {
			CodeSet.afterCreateHook = null;
		}
	}

	public static void waitForImportJob(final IProject project, final Job job) throws Exception {
		new ACondition("Wait for import of "+project.getName(), BOOT_PROJECT_CREATION_TIMEOUT) {
			@Override
			public boolean test() throws Exception {
				assertOk(job.getResult());
				if (project.hasNature("org.eclipse.m2e.core.maven2Nature")) {
					updateMavenProjectDependencies(project);
				}
				StsTestUtil.assertNoErrors(project);
				return true;
			}
		};
	}

	public IProject getProject(String projectName) {
		return workspace.getRoot().getProject(projectName);
	}

	public static void updateMavenProjectDependencies(IProject project) throws InterruptedException {
//		debug("updateMavenProjectDependencies("+project.getName()+") ...");
		boolean refreshFromLocal = true;
		boolean cleanProjects = true;
		boolean updateConfig = true;
		boolean offline = false;
		boolean forceUpdateDeps = true;

		UpdateMavenProjectJob job = createUpdateMavenProjectJob(project, offline, forceUpdateDeps, updateConfig, cleanProjects, refreshFromLocal);

		job.schedule();
		job.join();
//		debug("updateMavenProjectDependencies("+project.getName()+") DONE");
	}

	/**
	 * Helper method to create the job to update maven projects.
	 *
	 * Due to m2e 2.0 changing the signature of the constructor (parameter from IProject[] to Collection<IProject>)
	 * we need to call the constructor via reflection to allow this code to work with m2e 1.x and m2e 2.x at the same time
	 */
	private static UpdateMavenProjectJob createUpdateMavenProjectJob(IProject project, boolean offline,
			boolean forceUpdateDeps, boolean updateConfig, boolean cleanProjects, boolean refreshFromLocal) {
		Object args = new IProject[] {project};

		try {
			// check for m2e 1.x version (public UpdateMavenProjectJob(IProject[] projects, ...)
			Constructor<UpdateMavenProjectJob> constructor = UpdateMavenProjectJob.class.getConstructor(
					IProject[].class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
			return constructor.newInstance(args, offline, forceUpdateDeps, updateConfig, cleanProjects, refreshFromLocal);
		} catch (Exception e) {
		}

		try {
			// check for m2e 2.x version (public UpdateMavenProjectJob(Collection<IProject> projects, ...)
			Constructor<UpdateMavenProjectJob> constructor = UpdateMavenProjectJob.class.getConstructor(
					Collection.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
			return constructor.newInstance(Collections.singleton(project), offline, forceUpdateDeps, updateConfig, cleanProjects, refreshFromLocal);
		} catch (Exception e) {
		}

		return null;
	}

	public static IProject createPredefinedMavenProject(final String projectName, final String bundleName)
				throws CoreException, Exception {
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			if (project.exists()) {
				return project;
			}
			StsTestUtil.setAutoBuilding(false);
			ImportConfiguration importConf = new ImportConfiguration() {

				@Override
				public String getProjectName() {
					return projectName;
				}

				@Override
				public String getLocation() {
					return ResourcesPlugin.getWorkspace().getRoot().getLocation().append(projectName).toString();
				}

				@Override
				public CodeSet getCodeSet() {
					File sourceWorkspace = new File(StsTestUtil.getSourceWorkspacePath(bundleName));
					File sourceProject = new File(sourceWorkspace, projectName);
					return new CopyFromFolder(projectName, sourceProject);
				}
			};
			final IRunnableWithProgressAndResult<IProject> importOp = BuildType.MAVEN.getDefaultStrategy().createOperation(importConf);
			Job runner = new Job("Import "+projectName) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						importOp.run(monitor);
					} catch (Throwable e) {
						return ExceptionUtil.status(e);
					}
					return Status.OK_STATUS;
				}
			};
			runner.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
			runner.schedule();

			waitForImportJob(project, runner);
	//		BootProjectTestHarness.assertNoErrors(project);
			return project;
		}

	public static void buildMavenProject(IProject p) throws Exception {
		ISpringBootProject bp = SpringBootCore.create(p);
		updateMavenProjectDependencies(bp.getProject());
		bp.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
	}

	public static void assertOk(IStatus result) throws Exception {
		if (result == null || !result.isOK()) {
			throw ExceptionUtil.coreException(result);
		}
	}

	/**
	 * Create the most basic project possible. It has no natures, no builders, not nothing.
	 * This project is suitable as a test fixture for a test that only needs a project to
	 * exist and nothing more.
	 */
	public IProject createProject(String projectName) throws Exception {
		IProject project = workspace.getRoot().getProject(projectName);
		project.create(new NullProgressMonitor());
		project.open(new NullProgressMonitor());
		return project;
	}

	public IProject rename(IProject project, String newName) throws Exception {
		IProjectDescription description = project.getDescription();
		description.setName(newName);
		project.move(description, true, new NullProgressMonitor());
		return workspace.getRoot().getProject(newName);
	}

	public String[] getInitializrSupportedBootVersions() {
		if (supportedBootVersions == null) {
			fail("Must first create a project before initializr supported versions are resolved.");
		}
		return supportedBootVersions;
	}

	public String getLatestBootReleaseVersion() {
		if (latestReleaseVersion == null) {
			fail("Must first create a project before latest boot version from initializr is resolved.");
		}
		return latestReleaseVersion;
	}
}
