/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation;

import java.time.Duration;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springsource.ide.eclipse.commons.core.SpringCoreUtils;
import org.springsource.ide.eclipse.commons.core.pstore.IScopedPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Watches workspace projects to detect boot projects and install spring
 * boot builder that checks validation rules.
 * 
 * @author Kris De Volder
 */
public class BootValidationEnabler implements org.eclipse.ui.IStartup {
	
	class ValidationEnablerStartupJob extends Job {
		
		public ValidationEnablerStartupJob() {
			super("Initializing Boot Validation Enabler");
			setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
			setSystem(true);
		}

		@Override
		protected IStatus run(IProgressMonitor arg0) {
			Enabler enabler = new Enabler();
			ClasspathListenerManager classpathListener = new ClasspathListenerManager(
				enabler::checkProject, 
				true
			);
			BootValidationActivator.onStop(() -> classpathListener.close());
			return Status.OK_STATUS;
		}
		
	}

	
	private static class Enabler {

		private static final String VALIDATION_INITIALIZED = "boot.validation.initialized";
		
		private final IScopedPropertyStore<IProject> projectProperties = PropertyStores.createForProjects("org.springframework.ide.eclipse");
		
		private void checkProject(IJavaProject jp) {
			Job job = new Job("Check Project "+jp.getElementName()) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						IProject p = jp.getProject();
						if (p.isAccessible()) {
							PropertyStoreApi properties = PropertyStores.createApi(PropertyStores.createForScope(p, projectProperties));
							if (
									!properties.get(VALIDATION_INITIALIZED, false) && 
									!hasBuilder(p, BootValidationActivator.BUILDER_ID) && 
									BootPropertyTester.isBootProject(p)
							) {
								properties.put(VALIDATION_INITIALIZED, true); //prevents adding it back automatically a second time, if user somehow removes it.
								SpringCoreUtils.addProjectBuilder(p, BootValidationActivator.BUILDER_ID, monitor);
							}
						}
					} catch (Exception e) {
						Log.log(e);
					}
					return Status.OK_STATUS;
				}
			};
			job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
			job.setSystem(true);
			job.schedule();
		}
		
		private boolean hasBuilder(IProject p, String builderId) throws CoreException {
			ICommand[] builderCommands = p.getDescription().getBuildSpec();
			if (builderCommands != null) {
				for (ICommand cmd : builderCommands) {
					if (cmd.getBuilderName().equals(builderId)) {
						return true;
					}
				}
			}
			return false;
		}
	}

	@Override
	public void earlyStartup() {
		//Don't do this stuff actually during startup. Its not critical and it can wait.
		new ValidationEnablerStartupJob().schedule(Duration.ofSeconds(10).toMillis());
	}

}
