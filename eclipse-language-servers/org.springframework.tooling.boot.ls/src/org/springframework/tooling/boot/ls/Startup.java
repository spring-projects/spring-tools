/*******************************************************************************
 * Copyright (c) 2022, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.ui.IStartup;
import org.springframework.tooling.jdt.ls.commons.BootProjectTracker;
import org.springframework.tooling.jdt.ls.commons.Logger;

@SuppressWarnings("restriction")
public class Startup implements IStartup {

	private boolean started;

	@Override
	public void earlyStartup() {
		if (BootLanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(Constants.PREF_START_LS_EARLY)) {
			
			new BootProjectTracker(Logger.forEclipsePlugin(() -> BootLanguageServerPlugin.getDefault()),
					List.of(this::handleProjectsFound));
		}
	}
	
	private synchronized void handleProjectsFound(Set<IJavaProject> springProjects) {
		if (!started && !springProjects.isEmpty()) {
			LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance()
					.getDefinition(BootLanguageServerPlugin.BOOT_LS_DEFINITION_ID);

			try {
				LanguageServiceAccessor.startLanguageServer(serverDefinition);
				started = true;
			} catch (Throwable e1) {
				BootLanguageServerPlugin.getDefault().getLog().error("Failed to launch Boot Language Server", e1);
			}

		}
	}

}
