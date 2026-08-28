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

/**
 * An adapter/manager for a single listener interested in project deletions
 * from the Eclipse workspace.
 * <p>
 * Create an instance of ProjectDeletionListenerManager and pass it a
 * ProjectDeletionListener to attach it to the workspace.
 * <p>
 * To deregister the listener, dispose the manager.
 *
 * @author Kris De Volder
 */
public class ProjectDeletionListenerManager implements IResourceChangeListener {

	public interface ProjectDeletionListener {
		void projectWasDeleted(IProject project);
	}

	private IWorkspace workspace;
	private ProjectDeletionListener listener;

	public ProjectDeletionListenerManager(IWorkspace workspace, ProjectDeletionListener listener) {
		this.workspace = workspace;
		this.listener = listener;
		this.workspace.addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta _delta = event.getDelta();
		if (_delta != null) {
			IResourceDelta[] children = _delta.getAffectedChildren();
			if (children != null) {
				for (IResourceDelta delta : children) {
					if (delta != null) {
						IResource rsrc = delta.getResource();
						if (rsrc instanceof IProject) {
							int kind = delta.getKind();
							if (kind == IResourceDelta.REMOVED) {
								if (!isRename(delta)) {
									listener.projectWasDeleted((IProject) rsrc);
								}
							}
						}
					}
				}
			}
		}
	}

	private boolean isRename(IResourceDelta delta) {
		return 0 != (delta.getFlags()&IResourceDelta.MOVED_TO);
	}

	public void dispose() {
		if (listener != null) {
			workspace.removeResourceChangeListener(this);
			listener = null;
		}
	}

}
