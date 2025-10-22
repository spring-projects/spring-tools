/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springframework.tooling.jdt.ls.commons.BootProjectTracker;

class GroupingRepository {
	
	private static final String KEY = "stereotype-structure-grouping";
	
	private List<String> getGrouping(IProject project) {
		IEclipsePreferences prefNode = new ProjectScope(project).getNode(BootLanguageServerPlugin.getDefault().getBundle().getSymbolicName());
		String s = prefNode.get(KEY, null);
		return s == null ? null : Arrays.asList(s.split("\\|"));
	}
	
	private void setGrouping(IProject project, List<String> grouping) {
		IEclipsePreferences prefNode = new ProjectScope(project).getNode(BootLanguageServerPlugin.getDefault().getBundle().getSymbolicName());
		if (grouping == null) {
			prefNode.remove(KEY);
		} else {
			prefNode.put(KEY, String.join("|", grouping.toArray(new String[grouping.size()])));
		}
		try {
			prefNode.flush();
		} catch (BackingStoreException e) {
			BootLanguageServerPlugin.getDefault().getLog().error("Failed to stote stereotype structure grouping settings", e);
		}
	}
	
	Map<String, List<String>> getWorkspaceGroupings() {
		Map<String, List<String>> workspaceGroupings = new HashMap<>();
		BootProjectTracker.streamSpringProjects().forEach(p -> {
			List<String> g = getGrouping(p.getProject());
			if (g != null) {
				workspaceGroupings.put(p.getElementName(), g);
			}
		});
		return workspaceGroupings;
	}
	
	void saveWorkspaceGroupings(Map<String, List<String>> groupings) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		for (Entry<String, List<String>> e : groupings.entrySet()) {
			String n = e.getKey();
			List<String> g = e.getValue();
			IProject project = root.getProject(n);
			if (project != null) {
				setGrouping(project, g);
			}
		}
	}

}
