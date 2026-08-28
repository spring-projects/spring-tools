/*******************************************************************************
 * Copyright (c) 2012 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.devtools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;

@SuppressWarnings("restriction")
public class BootDevtoolsClientLaunchShortcut implements ILaunchShortcut {

	@Override
	public void launch(ISelection selection, String mode) {
		try {
			IResource rsrc = getResource(selection);
			launch(rsrc, mode);
		} catch (Throwable e) {
			BootActivator.log(e);
		}
	}

	public void launch(IResource rsrc, String mode) throws CoreException {
		if (rsrc != null && rsrc.getType() == IResource.PROJECT) {
			ILaunchConfiguration conf = findOrCreateConfiguration((IProject) rsrc);
			if (conf != null) {
				if (isLaunchable(conf)) {
					DebugUITools.launch(conf, mode);
				} else {
					IStructuredSelection selection = new StructuredSelection(new Object[] { conf });
					DebugUITools.openLaunchConfigurationDialogOnGroup(getShell(), selection, getLaunchGroup(mode));
				}
			}
		}
	}

	/**
	 * Decide whether a launch conf is ready to launch as is or should be opened in
	 * launc conf editor to allow user to fill in more info.
	 */
	private boolean isLaunchable(ILaunchConfiguration conf) {
		IProject project = BootLaunchConfigurationDelegate.getProject(conf);
		String url = BootDevtoolsClientLaunchConfigurationDelegate.getRemoteUrl(conf);
		return project != null &&
				BootPropertyTester.isBootProject(project) &&
				BootPropertyTester.hasDevtools(project) &&
				StringUtil.hasText(url);
	}

	private String getLaunchGroup(String launchMode) {
		if (ILaunchManager.RUN_MODE.equals(launchMode)) {
			return IDebugUIConstants.ID_RUN_LAUNCH_GROUP;
		} else if (ILaunchManager.DEBUG_MODE.equals(launchMode)) {
			return IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP;
		} else {
			return IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP;
		}
	}


	private ILaunchConfiguration findOrCreateConfiguration(IProject project) throws CoreException {
		List<ILaunchConfiguration> candidates = findConfigurations(project);
		if (candidates.isEmpty()) {
			return createConfiguration(project);
		} else if (candidates.size() == 1) {
			return candidates.get(0);
		} else {
			return chooseConfiguration(project, candidates);
		}
	}

	private List<ILaunchConfiguration> findConfigurations(IProject project) throws CoreException {
		ILaunchManager lm = getLaunchManager();
		List<ILaunchConfiguration> configs = new ArrayList<>();
		for (ILaunchConfiguration c : lm.getLaunchConfigurations(getLaunchType())) {
			if (Objects.equals(BootLaunchConfigurationDelegate.getProject(c), project)) {
				configs.add(c);
			}
		}
		return configs;
	}

	/**
	 * Returns a configuration from the given collection of configurations that should be launched,
	 * or <code>null</code> to cancel. Default implementation opens a selection dialog that allows
	 * the user to choose one of the specified launch configurations.  Returns the chosen configuration,
	 * or <code>null</code> if the user cancels.
	 *
	 * @param configList list of configurations to choose from
	 * @return configuration to launch or <code>null</code> to cancel
	 */
	private ILaunchConfiguration chooseConfiguration(IProject project, List<ILaunchConfiguration> configList) {
		IDebugModelPresentation labelProvider = DebugUITools.newDebugModelPresentation();
		ElementListSelectionDialog dialog= new ElementListSelectionDialog(getShell(), labelProvider);
		dialog.setElements(configList.toArray());
		dialog.setTitle("Several DevTools Client Configs found for "+project.getName());
		dialog.setMessage("Select an existing configuration");
		dialog.setMultipleSelection(false);
		int result = dialog.open();
		labelProvider.dispose();
		if (result == Window.OK) {
			return (ILaunchConfiguration) dialog.getFirstResult();
		}
		return null;
	}

	/**
	 * Convenience method to return the active workbench window shell.
	 *
	 * @return active workbench window shell
	 */
	protected Shell getShell() {
		return JDIDebugUIPlugin.getActiveWorkbenchShell();
	}

	private ILaunchConfigurationType getLaunchType() {
		return getLaunchManager().getLaunchConfigurationType(BootDevtoolsClientLaunchConfigurationDelegate.TYPE_ID);
	}

	/**
	 * Returns the singleton launch manager.
	 *
	 * @return launch manager
	 */
	private ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	protected ILaunchConfiguration createConfiguration(IProject project) throws CoreException {
		ILaunchConfigurationType configType = getConfigurationType();
		String projectName = project.getName();
		ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, getLaunchManager().generateLaunchConfigurationName("devtools-client["+projectName+"]"));
		setDefaults(wc, project);
		wc.setMappedResources(new IResource[] {project});
		ILaunchConfiguration config = wc.doSave();
		return config;
	}

	private void setDefaults(ILaunchConfigurationWorkingCopy wc, IProject project) {
		BootLaunchConfigurationDelegate.setProject(wc, project);
	}

	protected ILaunchConfigurationType getConfigurationType() {
		return getLaunchManager().getLaunchConfigurationType(BootDevtoolsClientLaunchConfigurationDelegate.TYPE_ID);
	}

	private IResource getResource(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			Object el = ss.getFirstElement();
			if (el instanceof IResource) {
				return (IResource) el;
			} else if (el instanceof IAdaptable) {
				//Warning older Eclipse API 'getAdapter() Object not IResource
				Object o = ((IAdaptable) el).getAdapter(IResource.class);
				return (IResource)o;
			}
		}
		return null;
	}


	@Override
	public void launch(IEditorPart editor, String mode) {
		try {
			IEditorInput input = editor.getEditorInput();
			Object rsrc = input.getAdapter(IResource.class);
			if (rsrc != null) {
				launch((IResource)rsrc, mode);
			}
		} catch (Throwable e) {
			BootActivator.log(e);
		}
	}

}
