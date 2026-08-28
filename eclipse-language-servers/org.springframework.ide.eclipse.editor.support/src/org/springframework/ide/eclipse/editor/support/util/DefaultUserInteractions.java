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
package org.springframework.ide.eclipse.editor.support.util;

import org.eclipse.core.resources.IContainer;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

/**
 * Provides 'real' implementations of the UserInteractions (in testing context
 * a 'mock' UserInteractions object can be used instead.
 *
 * @author Kris De Volder
 */
public class DefaultUserInteractions implements UserInteractions {

	private Shell shell;

	public DefaultUserInteractions(Shell shell) {
		this.shell = shell;
	}

	@Override
	public IContainer chooseOneSourceFolder(String title, String message, IContainer[] options, IContainer preferred) {
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IContainer) {
					IContainer c = (IContainer) element;
					return c.getFullPath().toString();
				}
				return element.toString();
			}
		});
		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.setElements(options);
		dialog.setBlockOnOpen(true);
		dialog.setMultipleSelection(false);
		if (preferred != null) {
			dialog.setInitialSelections(new Object[] {preferred});
		}
		int code = dialog.open();
		if (code == Window.OK) {
			return (IContainer) dialog.getFirstResult();
		}
		return null;
	}

	@Override
	public void error(String title, String message) {
		MessageDialog.openError(shell, title, message);
	}

}
