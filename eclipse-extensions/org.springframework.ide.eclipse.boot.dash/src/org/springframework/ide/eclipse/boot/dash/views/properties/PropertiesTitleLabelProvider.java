/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.properties;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.labels.BootDashLabels;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;

/**
 * Label provider for Boot Dash elements for the tabbed properties view
 *
 * @author Alex Boyko
 *
 */
public class PropertiesTitleLabelProvider implements ILabelProvider {

	BootDashLabels labels = new BootDashLabels(BootDashActivator.getDefault().getInjections(), new Stylers(null));

	@Override
	public void addListener(ILabelProviderListener listener) {

	}

	@Override
	public void dispose() {

	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {

	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof IStructuredSelection) {
			IStructuredSelection selection = (IStructuredSelection) element;
			if (selection.size() == 1) {
				Object o = selection.getFirstElement();
				if (o instanceof BootDashElement) {
					return ((BootDashElement) o).getPropertiesTitleIconImage();
				}
			}
		}
		return null;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof IStructuredSelection) {
			IStructuredSelection selection = (IStructuredSelection) element;
			if (selection.size() > 1) {
				return selection.size() + " of elements are selected";
			} else {
				Object o = ((IStructuredSelection)element).getFirstElement();
				if (o instanceof BootDashElement) {
					StyledString l = labels.getStyledText((BootDashElement)o, BootDashColumn.NAME);
					if (l != null) {
						return l.getString();
					} else {
						return ((BootDashElement) o).getName();
					}
				}
			}
		}
		return null;
	}

}
