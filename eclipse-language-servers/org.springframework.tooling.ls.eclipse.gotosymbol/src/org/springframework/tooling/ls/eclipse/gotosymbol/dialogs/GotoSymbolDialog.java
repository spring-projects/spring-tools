/*******************************************************************************
 * Copyright (c) 2016, 2019 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Kris De Volder (Pivotal Inc) - Copied and adapted 
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;

public class GotoSymbolDialog extends PopupDialog implements IPageWithSections {
	
	private static final Point DEFAULT_SIZE = new Point(280, 300);

	private final GotoSymbolSection content;
	private ITextEditor fTextEditor;
	
	/**
	 * If true, align the dialog so it looks like its attached right side of the editor. Otherwise, dialog is centered on the editor instead.
	 */
	private boolean alignRight;
	private IDialogSettings dlgSettings;

	public GotoSymbolDialog(Shell parentShell, ITextEditor textEditor, GotoSymbolDialogModel model, boolean alignRight) {
		super(parentShell, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE, 
				/*focus*/true, /*persistSize*/true, /*persistLoc*/true, 
				/*menu*/false, /*showPersistActions*/false, null, null
		);
		//Note: because of the way shell is initialized, the options to persist size / location do not work.
		// If we want this to work, it will have to be debugged. 
		//For the time being I've simply disabled the menu that makes it appear like this should work.
		this.fTextEditor = textEditor;
		this.content = new GotoSymbolSection(this, model);
		this.alignRight = alignRight;
		create();
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		if (dlgSettings == null) {
			this.dlgSettings = GotoSymbolPlugin.getInstance().getDialogSettings();
		}
		return dlgSettings;
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite dialogArea = new Composite(parent, SWT.NONE);
		if (parent.getLayout() instanceof GridLayout) {
			dialogArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		}

		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		dialogArea.setLayout(layout);
		
		content.createContents(dialogArea);
		return dialogArea;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		
		Control control = fTextEditor.getAdapter(Control.class);
		Point dialogueSize = DEFAULT_SIZE;
		if (control != null) {
			//Set size relative to editor's size.
			dialogueSize = computeDialogueSize(control);
			if (alignRight) {
				shell.setLocation(control.toDisplay(control.getBounds().width - shell.getSize().x, control.getLocation().y));
			} else {
				//centered on the editor
				shell.setLocation(control.toDisplay(control.getBounds().width/4, control.getBounds().height/4));
			}
		} 
		shell.setSize(dialogueSize);
	}

	protected Point computeDialogueSize(Control control) {
		return new Point(control.getBounds().width, control.getBounds().height/2);
	}

	@Override
	public IRunnableContext getRunnableContext() {
		return PlatformUI.getWorkbench().getProgressService();
	}

//	/**
//	 * Determines the graphical area covered by the given text region.
//	 *
//	 * @param region the region whose graphical extend must be computed
//	 * @return the graphical extend of the given region
//	 */
//	private Rectangle computeArea(IRegion region) {
//
//		int start= 0;
//		int end= 0;
//
//		IRegion widgetRegion= modelRange2WidgetRange(region);
//		if (widgetRegion != null) {
//			start= widgetRegion.getOffset();
//			end= widgetRegion.getOffset() + widgetRegion.getLength();
//		}
//
//		StyledText styledText= fTextEditor.getTextWidget();
//		Rectangle bounds;
//		if (end > 0 && start < end)
//			bounds= styledText.getTextBounds(start, end - 1);
//		else {
//			Point loc= styledText.getLocationAtOffset(start);
//			bounds= new Rectangle(loc.x, loc.y, 0, styledText.getLineHeight(start));
//		}
//
//		Rectangle clientArea= styledText.getClientArea();
//		Geometry.moveInside(bounds, clientArea);
//		return bounds;
//	}


}
