/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.workspace;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.OperationCanceledException;

/**
 * @author Kris De Volder
 */
public class ProjectChangeListenerManager implements IResourceChangeListener {

	public interface ProjectChangeListener {
		void projectChanged(IProject project);
	}

	private IWorkspace workspace;
	private ProjectChangeListener listener;

	public ProjectChangeListenerManager(IWorkspace workspace, ProjectChangeListener listener) {
		this.workspace = workspace;
		this.listener = listener;
		this.workspace.addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			// Find out if a project was opened.
			IResourceDelta delta = event.getDelta();
			if (delta == null) return;

			IResourceDelta[] projDeltas = delta.getAffectedChildren(
					IResourceDelta.CHANGED|
					IResourceDelta.ADDED|
					IResourceDelta.REMOVED
			);
			for (int i = 0; i < projDeltas.length; ++i) {
				IResource resource = projDeltas[i].getResource();
				if (!(resource instanceof IProject))
					continue;

				IProject project = (IProject) resource;
				projectChanged(project);
			}
		} catch (OperationCanceledException oce) {
			// do nothing
		}
	}

	private void projectChanged(final IProject project) {
		listener.projectChanged(project);
	}

	public void dispose() {
		workspace.removeResourceChangeListener(this);
		if (listener != null) {
			listener = null;
		}
	}

}
