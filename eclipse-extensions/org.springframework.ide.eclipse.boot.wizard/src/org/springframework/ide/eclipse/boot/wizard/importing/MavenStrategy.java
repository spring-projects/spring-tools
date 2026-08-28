/*******************************************************************************
 *  Copyright (c) 2013, 2023 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.importing;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Model;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.osgi.util.NLS;
import org.springframework.ide.eclipse.boot.wizard.BootWizardActivator;
import org.springframework.ide.eclipse.boot.wizard.content.BuildType;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet;
import org.springsource.ide.eclipse.commons.core.IRunnableWithProgressAndResult;

/**
 * Importer strategy implementation for importing CodeSets into the workspace and set them
 * up to use Maven Tooling.
 *
 * @author Kris De Volder
 */
public class MavenStrategy extends ImportStrategy {

	public MavenStrategy(BuildType buildType, String name, String notInstalledMessage) {
		super(buildType, name, notInstalledMessage);
		Assert.isNotNull(Platform.getBundle("org.eclipse.m2e.core"), "M2E is not installed");
	}

	/**
	 * Implements the import by means of 'NewGradleProjectOperation'
	 */
	private static class MavenCodeSetImport implements IRunnableWithProgressAndResult<IProject> {

		//TODO: This import startegy doesn't even read projectName. The name actually comes from the
		//   maven pom file. Actually makes sense for inport to determine projectName from project
		//   content. So maybe projectName should not be in an ImportConfig at all!

		private final String projectName;
		private final File location;
		private final CodeSet codeset;

		public MavenCodeSetImport(ImportConfiguration conf) {
			this.projectName = conf.getProjectName();
			this.location = new File(conf.getLocation());
			this.codeset = conf.getCodeSet();
		}

		@Override
		public IProject run(IProgressMonitor mon) throws InvocationTargetException, InterruptedException {
			mon.beginTask("Create maven project "+projectName, 5);
			Job.getJobManager().beginRule(getRule(), new SubProgressMonitor(mon, 1));
			try {
				//1: 1 copy codeset data
				codeset.createAt(location);
				mon.worked(1);

				//2..4: materialize eclipse project from pom.xml
				File pomFile = new File(location, "pom.xml");
				Assert.isTrue(pomFile.isFile(), "No pom file found: "+pomFile);
				Assert.isTrue(pomFile.length() > 0, "Pom file contains no data: "+pomFile);
				return createEclipseProjectFromExistingMavenProject(pomFile, new SubProgressMonitor(mon, 3));
			} catch (InterruptedException e) {
				throw e;
			} catch (InvocationTargetException e) {
				throw e;
			} catch (Throwable e) {
				throw new InvocationTargetException(e);
			}
			finally {
				Job.getJobManager().endRule(getRule());
				mon.done();
			}
		}

		private ISchedulingRule getRule() {
			return ResourcesPlugin.getWorkspace().getRoot();
		}
	}

	@Override
	public MavenCodeSetImport createOperation(ImportConfiguration conf) {
		return new MavenCodeSetImport(conf);
	}

	protected static IProject createEclipseProjectFromExistingMavenProject(File pomFile, IProgressMonitor monitor) throws CoreException {
		Model model = MavenPlugin.getMavenModelManager().readMavenModel(pomFile);
		String derivedProjectName = model.getName();
		if (derivedProjectName == null) {
			derivedProjectName = model.getArtifactId();
		}
		if (derivedProjectName == null) {
			String[] groupPieces = model.getGroupId().split("\\.");
			int lastIndex = groupPieces.length - 1;
			if (lastIndex >= 0) {
				derivedProjectName = groupPieces[lastIndex];
			} else {
				String message = NLS.bind("Bad pom.xml: no name, artifactId, or groupId.", null);
				throw new CoreException(new Status(Status.ERROR, BootWizardActivator.PLUGIN_ID, message));
			}
		}
		MavenProjectInfo parent = null;
		MavenProjectInfo projectInfo = new MavenProjectInfo(derivedProjectName, pomFile, model, parent);
		ArrayList<MavenProjectInfo> projectInfos = new ArrayList<MavenProjectInfo>();
		projectInfos.add(projectInfo);
		ResolverConfiguration resolverConfiguration = new ResolverConfiguration();
		String activeProfiles = "pom.xml";
		resolverConfiguration.setSelectedProfiles(activeProfiles);
		ProjectImportConfiguration configuration = new ProjectImportConfiguration(resolverConfiguration);

		List<IMavenProjectImportResult> importResults = MavenPlugin.getProjectConfigurationManager().importProjects(projectInfos, configuration,
				monitor);
		for (IMavenProjectImportResult importResult : importResults) {
			// skip projects which have not been properly imported
			if (importResult.getProject() != null)
				MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(importResult.getProject(), monitor);
		}

		return importResults != null && importResults.size() > 0 ? importResults.get(0).getProject() : null;
	}


}
