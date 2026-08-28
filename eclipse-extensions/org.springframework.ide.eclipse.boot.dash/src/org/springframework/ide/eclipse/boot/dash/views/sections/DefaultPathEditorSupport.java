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
package org.springframework.ide.eclipse.boot.dash.views.sections;

import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.launch.util.TextCellEditorWithContentProposal;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;

public class DefaultPathEditorSupport extends EditingSupport {

	private CellEditor editor;

	public DefaultPathEditorSupport(TableViewer tableViewer, LiveExpression<BootDashElement> input, Stylers stylers) {
		super(tableViewer);
			IContentProposalProvider proposalProvider =
				//	new SimpleContentProposalProvider(new String[] {"red", "green", "blue"});
				 new RequestMappingContentProposalProvider(input);
			this.editor = new TextCellEditorWithContentProposal(tableViewer.getTable(),
					proposalProvider, UIUtils.CTRL_SPACE, UIUtils.PATH_CA_AUTO_ACTIVATION_CHARS
			).setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);


//			this.editor = new TextCellEditor(tableViewer.getTable());
	}

	@Override
	protected CellEditor getCellEditor(Object element) {
		return editor;
	}

	@Override
	protected boolean canEdit(Object element) {
		return element instanceof BootDashElement;
	}

	@Override
	protected Object getValue(Object element) {
		if (element instanceof BootDashElement) {
			String path = ((BootDashElement) element).getDefaultRequestMappingPath();
			return path == null ? "" : path;
		}
		return "?huh?";
	}

	@Override
	protected void setValue(Object element, Object value) {
		if (value instanceof String && element instanceof BootDashElement) {
			((BootDashElement)element).setDefaultRequestMappingPath((String) value);
		}
	}
}
