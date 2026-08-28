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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.springframework.ide.eclipse.boot.wizard.content.BuildType;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet;
import org.springsource.ide.eclipse.commons.core.IRunnableWithProgressAndResult;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Importer strategy implementation for importing CodeSets into the workspace as a
 * general eclipse project (i.e. without any natures or builders).
 *
 * up to use Maven Tooling.
 *
 * @author Kris De Volder
 */
public class GeneralProjectStrategy extends ImportStrategy {

	public GeneralProjectStrategy(BuildType buildType, String name, String notInstalledMessage) {
		super(buildType, name, notInstalledMessage);
	}

	public static class GeneralProjectImport implements IRunnableWithProgressAndResult<IProject> {

		private final String projectName;
		private final File location;
		private final CodeSet codeset;

		public GeneralProjectImport(ImportConfiguration conf) {
			this.projectName = conf.getProjectName();
			this.location = new File(conf.getLocation());
			this.codeset = conf.getCodeSet();
		}

		public IProject run(IProgressMonitor mon) throws InvocationTargetException, InterruptedException {
			mon.beginTask("Create General Project '"+projectName+"'", 2);
			try {
				//1: copy/isntantiate codeset data
				codeset.createAt(location);
				mon.worked(1);

				//2: create project in workspace
				return GeneralProjectImport.createGeneralProject(projectName, location, new SubProgressMonitor(mon, 1));

			} catch (InterruptedException e) {
				throw e;
			} catch (InvocationTargetException e) {
				throw e;
			} catch (Throwable e) {
				throw new InvocationTargetException(e);
			}
			finally {
				mon.done();
			}
		}

		public static boolean isDefaultProjectLocation(String projectName, File projectDir) {
			IPath workspaceLoc = Platform.getLocation();
			if (workspaceLoc != null) {
				File defaultLoc = new File(workspaceLoc.toFile(), projectName);
				return defaultLoc.equals(projectDir);
			}
			return false;
		}

		/**
		 * Create a general eclipse project (no builders natures etc) with a given name and project contents
		 * from a given location. The contents of the project will be linked, not copied.
		 * @return 
		 */
		public static IProject createGeneralProject(String projectName, File projectDir, IProgressMonitor mon) throws CoreException {
			mon.beginTask("Create project "+projectName, 3);
			try {
				//1
				IWorkspace ws = ResourcesPlugin.getWorkspace();
				IProjectDescription projectDescription = ws.newProjectDescription(projectName);
				Path projectLocation = new Path(projectDir.getAbsolutePath());
				if (!GeneralProjectImport.isDefaultProjectLocation(projectName, projectDir)) {
					projectDescription.setLocation(projectLocation);
				}
				//To improve error message... check validity of project location vs name
				//note: in import wizard use, this error is impossible since wizard validates this constraint.
				//Be careful that this constraint only needs to hold in a very specific case where the
				//location is nested exactly one level below the workspace location on disk.
				IPath wsLocation = ws.getRoot().getLocation();
				if (wsLocation.isPrefixOf(projectLocation) && wsLocation.segmentCount()+1 == projectLocation.segmentCount()) {
					String expectedName = projectDir.getName();
					if (!expectedName.equals(projectName)) {
						throw ExceptionUtil.coreException("Project-name ("+projectName+") should match last segment of location ("+projectDir+")");
					}
				}
				mon.worked(1);

				//2
				IProject project = ws.getRoot().getProject(projectName);
				project.create(projectDescription, new SubProgressMonitor(mon, 1));

				//3
				project.open(new SubProgressMonitor(mon, 1));
				return project;
			} finally {
				mon.done();
			}
		}
	}

	@Override
	public IRunnableWithProgressAndResult<IProject> createOperation(ImportConfiguration conf) {
		return new GeneralProjectImport(conf);
	}



}
