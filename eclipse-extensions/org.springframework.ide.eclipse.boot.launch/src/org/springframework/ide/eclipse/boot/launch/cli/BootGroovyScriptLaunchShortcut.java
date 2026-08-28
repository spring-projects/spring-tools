/*******************************************************************************
 * Copyright (c) 2012, 2017 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.cli;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.springframework.ide.eclipse.boot.util.Log;

public class BootGroovyScriptLaunchShortcut implements ILaunchShortcut {

	@Override
	public void launch(ISelection selection, String mode) {
		try {
			IResource rsrc = getResource(selection);
			launch(rsrc, mode);
		} catch (Throwable e) {
			Log.log(e);
		}
	}

	public void launch(IResource rsrc, String mode) throws CoreException {
		if (rsrc != null && rsrc.getType() == IResource.FILE) {
			ILaunchConfiguration conf = createConfiguration((IFile) rsrc);
			DebugUITools.launch(conf, mode);
		}
	}

	/**
	 * Returns the singleton launch manager.
	 *
	 * @return launch manager
	 */
	private ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	protected ILaunchConfiguration createConfiguration(IFile rsrc) throws CoreException {
		ILaunchConfigurationType configType = getConfigurationType();
		String projectName = rsrc.getProject().getName();
		ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, getLaunchManager().generateLaunchConfigurationName(projectName+" "+rsrc.getName()));
		BootGroovyScriptLaunchConfigurationDelegate.setScript(wc, rsrc);
		wc.setMappedResources(new IResource[] {rsrc});
		//Normally you should call:
		//config = wc.doSave();
		//But we skip it for now. The launch conf will not be saved so it will be 'transient'.
		return wc;
	}

	protected ILaunchConfigurationType getConfigurationType() {
		return getLaunchManager().getLaunchConfigurationType(BootGroovyScriptLaunchConfigurationDelegate.ID);
	}

	private IResource getResource(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			Object el = ss.getFirstElement();
			if (el instanceof IResource) {
				return (IResource) el;
			} else if (el instanceof IAdaptable) {
				return (IResource) ((IAdaptable) el).getAdapter(IResource.class);
			}
		}
		return null;
	}


	@Override
	public void launch(IEditorPart editor, String mode) {
		try {
			IEditorInput input = editor.getEditorInput();
			IResource rsrc = (IResource) input.getAdapter(IResource.class);
			if (rsrc != null) {
				launch(rsrc, mode);
			}
			System.out.println(input);
		} catch (Throwable e) {
			Log.log(e);
		}
	}

}
