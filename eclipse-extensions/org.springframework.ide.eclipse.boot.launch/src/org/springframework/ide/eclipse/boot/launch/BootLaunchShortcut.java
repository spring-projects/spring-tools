/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaApplicationLaunchShortcut;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.launcher.LauncherMessages;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.StructuredSelection;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.maintype.MainTypeFinder;

@SuppressWarnings("restriction")
public class BootLaunchShortcut extends JavaApplicationLaunchShortcut {

	/**
	 * Launch configuration id of the configs created by this shortcut.
	 */
	public static final String LAUNCH_CONFIG_TYPE_ID = BootLaunchConfigurationDelegate.TYPE_ID;

	@Override
	public IType[] findTypes(Object[] elements, IRunnableContext context)
			throws InterruptedException, CoreException {
		//For spring boot app, instead of searching for a main type in the entire project and all its
		// libraries... try to look inside the project's pom for the corresponding property.
		for (Object e : elements) {
			if (e instanceof IProject) {
				try {
					e = JavaCore.create((IProject)e);
				} catch (Throwable ignore) {
				}
			}
			{
				IType type = isMainMethod(elements[0]);
				if (type != null) {
					return new IType[] {type};
				}
			}
			if (e instanceof IJavaElement) {
				if (e instanceof IType) {
					if (hasMainMethod((IType) e)) {
						return new IType[] {(IType)e};
					}
				}
				if (e instanceof ICompilationUnit) {
					for (IType t : ((ICompilationUnit) e).getAllTypes()) {
						if (hasMainMethod(t)) {
							return new IType[] {t};
						}
					}
				}
				final IJavaProject jp = ((IJavaElement)e).getJavaProject();
				final IType[][] result = new IType[][] { null };
				try {
					context.run(/*fork*/false, /*true*/false, new IRunnableWithProgress() {
						@Override
						public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
							try {
								result[0] = MainTypeFinder.guessMainTypes(jp, monitor);
							} catch (CoreException e) {
								throw new InvocationTargetException(e);
							}
						}
					});
				} catch (InvocationTargetException exception) {
					throw ExceptionUtil.coreException(exception);
				}
				return result[0];
			}
		}
		//This isn't the best thing to to do as it searches also in all the library jars for main types. But it is
		// only a fallback option if the above code failed. (Or should we rather signal an error instead?)
		return super.findTypes(elements, context);
	}

	private boolean hasMainMethod(IType t) {
		try {
			for (IMethod m : t.getMethods()) {
				if (m.isMainMethod()) {
					return true;
				}
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
		return false;
	}

	/**
	 * Returns the smallest enclosing <code>IType</code> if the specified object is a main method, or <code>null</code>
	 * @param o the object to inspect
	 * @return the smallest enclosing <code>IType</code> of the specified object if it is a main method or <code>null</code> if it is not
	 */
	private IType isMainMethod(Object o) {
		if (o instanceof IAdaptable) {
			IAdaptable adapt = (IAdaptable) o;
			IJavaElement element = (IJavaElement) adapt.getAdapter(IJavaElement.class);
			if (element != null && element.getElementType() == IJavaElement.METHOD) {
				try {
					IMethod method = (IMethod) element;
					if (method.isMainMethod()) {
						return method.getDeclaringType();
					}
				}
				catch (JavaModelException jme) {JDIDebugUIPlugin.log(jme);}
			}
		}
		return null;
	}

	@Override
	protected ILaunchConfigurationType getConfigurationType() {
		return getLaunchManager().getLaunchConfigurationType(LAUNCH_CONFIG_TYPE_ID);
	}

	/**
	 * Overridden, copied and changed to alter the generated launch configuration name.
	 */
	@Override
	public ILaunchConfiguration createConfiguration(IType type) {
		ILaunchConfiguration config = null;
		try {
			config = BootLaunchConfigurationDelegate.createConf(type);
		} catch (CoreException exception) {
			MessageDialog.openError(JDIDebugUIPlugin.getActiveWorkbenchShell(), LauncherMessages.JavaLaunchShortcut_3, exception.getStatus().getMessage());
		}
		return config;
	}

	/**
	 * Returns the singleton launch manager.
	 *
	 * @return launch manager
	 */
	private ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	public static void launch(IProject project, String mode) {
		BootLaunchShortcut shortcut = new BootLaunchShortcut();
		StructuredSelection selection = new StructuredSelection(new Object[] {project});
		shortcut.launch(selection, mode);
	}

}
