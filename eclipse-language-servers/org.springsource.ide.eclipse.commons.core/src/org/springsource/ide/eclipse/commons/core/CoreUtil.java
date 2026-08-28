/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core;

import java.util.Properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;

/**
 * @author Steffen Pingel
 */
public class CoreUtil {

	/**
	 * Replaces placeholders in <code>text</code> with values from
	 * <code>properties</code>. Placeholders use the following format:
	 * <code>${key}</code>. If the key is not found in <code>properties</code>
	 * the placeholder is retained.
	 *
	 * @param text the text
	 * @param properties key value pairs for substitution
	 * @return the substituted text
	 */
	public static String substitute(String text, Properties properties) {
		Assert.isNotNull(text);
		Assert.isNotNull(properties);
		String[] segments = text.split("\\$\\{");
		StringBuffer sb = new StringBuffer(text.length());
		sb.append(segments[0]);
		for (int i = 1; i < segments.length; i++) {
			String segment = segments[i];
			String substitution = null;
			int brace = segment.indexOf('}');
			if (brace > 0) {
				String keyword = segment.substring(0, brace);
				substitution = properties.getProperty(keyword);
			}

			if (substitution != null) {
				sb.append(substitution);
				sb.append(segment.substring(brace + 1));
			}
			else {
				sb.append("${");
				sb.append(segment);
			}
		}
		return sb.toString();
	}

	/*
	 *
	 * The following constants are copied from LaunchConfigurationDelegate
	 *
	 *
	 */

	/**
	 * Constant to define debug.core for the status codes
	 *
	 * @since 3.2
	 */
	private static final String DEBUG_CORE = "org.eclipse.debug.core";

	/**
	 * Constant to define debug.ui for the status codes
	 *
	 * @since 3.2
	 */
	private static final String DEBUG_UI = "org.eclipse.debug.ui";

	/**
	 * Status code for which a UI prompter is registered.
	 */
	protected static final IStatus promptStatus = new Status(IStatus.INFO, DEBUG_UI, 200, "", null);

	/**
	 * Status code for which a prompter will ask the user to save any/all of the dirty editors which have only to do
	 * with this launch (scoping them to the current launch/build)
	 *
	 * @since 3.2
	 */
	protected static final IStatus saveScopedDirtyEditors = new Status(IStatus.INFO, DEBUG_CORE, 222, "", null);

	/**
	 * Derived from {@link LaunchConfigurationDelegate}
	 * @param project
	 * @return true if project was saved. False if canceled
	 * @throws Exception
	 */
	public static boolean promptForProjectSave(IProject project) throws Exception {
		IStatusHandler prompter = DebugPlugin.getDefault().getStatusHandler(promptStatus);

		if (prompter != null) {
			IProject[] projects = new IProject[] {project};
			ILaunchConfiguration configuration = null;
			if (!((Boolean)prompter.handleStatus(saveScopedDirtyEditors, new Object[]{configuration, projects})).booleanValue()) {
				return false;
			}
		}
		return true;
	}


}
