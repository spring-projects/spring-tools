/*******************************************************************************
 *  Copyright (c) 2015, 2023 Pivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.buildship30;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.SynchronizationResult;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.springframework.ide.eclipse.boot.wizard.content.BuildType;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportConfiguration;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategy;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategyFactory;
import org.springsource.ide.eclipse.commons.core.IRunnableWithProgressAndResult;
import org.springsource.ide.eclipse.commons.core.SpringCoreUtils;
import org.springsource.ide.eclipse.commons.core.util.NatureUtils;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;


/**
 * Importer strategy implementation for importing CodeSets into the workspace and set them
 * up to use Buildship Gradle Tooling.
 *
 * @author Kris De Volder
 */
public class Buildship30ImportStrategy extends ImportStrategy {

	public Buildship30ImportStrategy(BuildType buildType, String name, String notInstalledMessage) {
		super(buildType, name, notInstalledMessage);
	}

	public static class Factory implements ImportStrategyFactory {
		@Override
		public ImportStrategy create(BuildType buildType, String name, String notInstalledMessage) throws Exception {
			Assert.isLegal(buildType == BuildType.GRADLE);
			Class.forName("org.eclipse.buildship.core.GradleBuild");
			return new Buildship30ImportStrategy(buildType, name, notInstalledMessage);
		}

	}

	@Override
	public IRunnableWithProgressAndResult<IProject> createOperation(final ImportConfiguration conf) {
		return new IRunnableWithProgressAndResult<IProject>() {
			@Override
			public IProject run(IProgressMonitor mon) throws InvocationTargetException, InterruptedException {
				mon.beginTask("Import Gradle Buildship project", 10);
				try {
					File loc = new File(conf.getLocation());
					conf.getCodeSet().createAt(loc);
					
					// Replace project name in the settings.gradle file with the one from `conf.getProjectName()`
					// i.e. replace rootProject.name = 'project' with rootProject.name = 'project-generated'
					File settingsFile = new File(loc, "settings.gradle");
					if (settingsFile.exists()) {
						String content = IOUtils.toString(new FileInputStream(settingsFile), Charset.defaultCharset());
						String newContent = content.replaceAll("(\\s*rootProject\\.name\\s*=\\s*').*('\\s*)", "$1" + conf.getProjectName() + "$2");
						IOUtils.write(newContent.getBytes(), new FileOutputStream(settingsFile));
					}
					
					GradleBuild build = GradleCore.getWorkspace().createBuild(BuildConfiguration.forRootProjectDirectory(loc).build());
					SynchronizationResult buildResult = build.synchronize(SubMonitor.convert(mon, 9));
					if (buildResult.getStatus().isOK()) {
						//For STS3 we should add spring nature? But what project? The buildresult doesn't tell us what was imported.
						//We try to work around this by looking for the project in the workspace by its location.
						IContainer[] containers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(loc.toURI());
						if (containers != null) {
							for (IContainer c : containers) {
								if (c instanceof IProject) {
									IProject p = (IProject) c;
									try {
										NatureUtils.ensure(p, new NullProgressMonitor(), SpringCoreUtils.NATURE_ID);
									} catch (CoreException e) {
										//Ignore: happens in STS 4 which no longer has spring nature.
									}
									return p;
								}
							}
						}
					} else if (!mon.isCanceled()) {
						//Try not to loose the error completely.
						throw ExceptionUtil.coreException(buildResult.getStatus());
					}
					return null;
				} catch (Exception e) {
					if (e instanceof InterruptedException) {
						throw (InterruptedException)e;
					}
					if (e instanceof InvocationTargetException) {
						throw (InvocationTargetException)e;
					}
					throw new InvocationTargetException(e);
				}
				finally {
					mon.done();
				}
			}
		};
	}
	
}
