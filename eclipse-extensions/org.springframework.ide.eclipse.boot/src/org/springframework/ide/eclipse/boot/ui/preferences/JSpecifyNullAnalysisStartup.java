/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.ui.preferences;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ClasspathListenerManager;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Offers to enable annotation-based null analysis when JSpecify is detected in
 * a newly imported Java project.
 *
 * @author Spring Tools Team
 */
public class JSpecifyNullAnalysisStartup implements IStartup {

	private static final String JSPECIFY_NULL_MARKED = "org.jspecify.annotations.NullMarked";

	private static final QualifiedName PROMPTED = new QualifiedName(BootActivator.PLUGIN_ID,
			"jspecifyNullAnalysisPrompted");

	@Override
	public void earlyStartup() {
		new ClasspathListenerManager(this::classpathChanged);
	}

	private void classpathChanged(IJavaProject javaProject) {
		Job job = new Job("Checking null analysis for " + javaProject.getElementName()) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					IProject project = javaProject.getProject();
					if (shouldPrompt(javaProject) && project.getPersistentProperty(PROMPTED) == null) {
						project.setPersistentProperty(PROMPTED, Boolean.TRUE.toString());
						Display.getDefault().asyncExec(() -> prompt(javaProject));
					}
				} catch (CoreException e) {
					Log.log(e);
				}
				return Status.OK_STATUS;
			}
		};
		job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().modifyRule(javaProject.getProject()));
		job.setSystem(true);
		job.schedule();
	}

	public static boolean shouldPrompt(IJavaProject javaProject) throws JavaModelException {
		return javaProject.exists()
				&& !JavaCore.ENABLED.equals(javaProject.getOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, true))
				&& javaProject.findType(JSPECIFY_NULL_MARKED) != null;
	}

	private void prompt(IJavaProject javaProject) {
		IProject project = javaProject.getProject();
		if (project.isAccessible() && MessageDialog.openQuestion(Display.getDefault().getActiveShell(),
				"Enable Null Analysis?", "JSpecify annotations were found on the classpath of project '"
						+ project.getName() + "'. Enable annotation-based null analysis for this project?")) {
			Job job = new Job("Enabling null analysis for " + javaProject.getElementName()) {

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						javaProject.setOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);
						return Status.OK_STATUS;
					} catch (RuntimeException e) {
						Log.log(e);
						return new Status(IStatus.ERROR, BootActivator.PLUGIN_ID, "Failed to enable null analysis", e);
					}
				}
			};
			job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().modifyRule(project));
			job.schedule();
		}
	}

}
