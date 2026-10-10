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
package org.springframework.tooling.boot.ls.views;

import java.net.URI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springframework.tooling.boot.ls.views.StereotypeNode.JavaElementReference;
import org.springframework.tooling.jdt.ls.commons.java.JavaData;

/**
 * Opens the element a node read from a JAR dependency stands for, in the editor of the Java
 * tooling - the class file, at the method or field. Such a node has no location in a source file,
 * only the JDT binding key of its element, resolved only now that the node is opened rather than
 * for every node of the tree.
 * 
 * @author Martin Lippert
 */
final class JavaElementOpener {

	private static final String TITLE = "Logical Structure";

	private JavaElementOpener() {
	}

	static void open(Shell shell, String label, JavaElementReference reference) {
		try {
			IJavaElement element = find(reference);
			if (element != null) {
				IEditorPart editor = JavaUI.openInEditor(element);
				if (editor != null) {
					JavaUI.revealInEditor(editor, element);
				}
				return;
			}
		} catch (Exception e) {
			BootLanguageServerPlugin.getDefault().getLog().error("Failed to open " + reference.bindingKey(), e);
		}
		MessageDialog.openInformation(shell, TITLE, "Cannot open '" + label + "': not found by the Java tooling");
	}

	private static IJavaElement find(JavaElementReference reference) throws Exception {
		URI projectUri = reference.projectUri() == null ? null : URI.create(reference.projectUri());
		IJavaElement element = JavaData.findElement(projectUri, reference.bindingKey(), true);
		if (element == null) {
			// a method or field the Java tooling cannot find still opens its class
			String classKey = classKeyOf(reference.bindingKey());
			if (!classKey.isEmpty() && !classKey.equals(reference.bindingKey())) {
				element = JavaData.findElement(projectUri, classKey, true);
			}
		}
		return element;
	}

	/**
	 * The binding key of the class a member's binding key belongs to - everything up to the first
	 * semicolon.
	 */
	static String classKeyOf(String bindingKey) {
		return bindingKey.substring(0, bindingKey.indexOf(';') + 1);
	}

}
