/*******************************************************************************
 * Copyright (c) 2014, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.hover;

import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;

/**
 * IInformationControlCreator for 'hover information' associated with editor contents. This control is
 * used in two different contexts.
 *
 *    - tooltip info shown when hovering over something
 *    - side view for content assist.
 */
@SuppressWarnings("restriction")
public class HoverInformationControlCreator implements IInformationControlCreator {

	/**
	 * Status text shown in the bottom 'status' area of the control (but it is only shown on the
	 * non-enriched version of the control)
	 */
	private String statusText;

	/**
	 * Whether or not a 'enriched' version of the control should be created. Information controls generally
	 * have two different forms a 'plain' non-resizable form without scrollbars and toolbar and
	 * an 'enriched' version which may have a toolbar and scrollbars and is shown when the control
	 * has focus.
	 */
	private boolean enriched;

	public HoverInformationControlCreator(String statusText) {
		this(false, statusText);
	}

	public HoverInformationControlCreator(boolean enriched, String statusText) {
		this.enriched = enriched;
		this.statusText = statusText;
	}

	@Override
	public IInformationControl createInformationControl(Shell parent) {
		if (BrowserInformationControl.isAvailable(parent)) {
			if (!enriched) {
				return new HoverInformationControl(parent, PreferenceConstants.APPEARANCE_JAVADOC_FONT, statusText) {
					@Override
					public IInformationControlCreator getInformationPresenterControlCreator() {
						return new HoverInformationControlCreator(true, null);
					}
				};
			} else {
				ToolBarManager toolbar= new ToolBarManager(SWT.FLAT);
				BrowserInformationControl control = new HoverInformationControl(parent, PreferenceConstants.APPEARANCE_JAVADOC_FONT, toolbar) {
					@Override
					public IInformationControlCreator getInformationPresenterControlCreator() {
						return new HoverInformationControlCreator(true, null);
					}
				};
				fillToolbar(toolbar, control);
				toolbar.update(true);
				return control;
			}
		}
		return new DefaultInformationControl(parent, true);
	}

	/**
	 * Add menu items to the toolbar for the enriched version of the control.
	 */
	private void fillToolbar(ToolBarManager tbm, BrowserInformationControl infoControl) {
		tbm.add(new OpenDeclarationAction(infoControl));
	}

	/**
	 * Action that opens the current hover input element.
	 *
	 * @since 3.4
	 */
	private static final class OpenDeclarationAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public OpenDeclarationAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText("Open Declaration");
			JavaPluginImages.setLocalImageDescriptors(this, "goto_input.gif"); //$NON-NLS-1$
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			try {
				BrowserInformationControlInput input = fInfoControl.getInput();
				if (input instanceof HoverInfo) {
					HoverInfo infoInput= (HoverInfo) input;
					if (infoInput != null) {
						List<IJavaElement> elements = infoInput.getJavaElements();
						//TODO: This only opens the first element, if there's more than one should offer a choice/
						if (!elements.isEmpty()) {
							IJavaElement je = elements.get(0);
							fInfoControl.notifyDelayedInputChange(null);
							fInfoControl.dispose(); //FIXME: should have protocol to hide, rather than dispose
							JavaUI.openInEditor(je);
						}
					}
				}
			} catch (Exception e) {
				EditorSupportActivator.log(e);
			}
		}
	}

}
