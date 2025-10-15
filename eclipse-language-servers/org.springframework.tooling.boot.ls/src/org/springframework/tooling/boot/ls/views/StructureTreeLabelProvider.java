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

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;

/**
 * Label provider for the logical structure tree view.
 * Provides text labels for tree elements.
 * 
 * @author Alex Boyko
 */
public class StructureTreeLabelProvider extends LabelProvider {

	@Override
	public String getText(Object element) {
		if (element instanceof StereotypeNode) {
			return ((StereotypeNode) element).getText();
		}
		return super.getText(element);
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof StereotypeNode) {
			String descriptor = ((StereotypeNode) element).getIcon();
			if (descriptor != null && !descriptor.isBlank()) {
				return BootLanguageServerPlugin.getDefault().getStereotypeImage(descriptor);
			}
		}
		return super.getImage(element);
	}
	
	
}

