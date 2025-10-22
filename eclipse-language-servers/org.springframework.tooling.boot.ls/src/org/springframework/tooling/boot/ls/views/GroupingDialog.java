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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ContainerCheckedTreeViewer;
import org.osgi.framework.FrameworkUtil;
import org.springframework.tooling.boot.ls.views.GroupingDialogModel.GroupItem;
import org.springframework.tooling.boot.ls.views.GroupingDialogModel.ProjectItem;
import org.springframework.tooling.boot.ls.views.GroupingDialogModel.TreeItem;
import org.springframework.tooling.boot.ls.views.StructureClient.Groups;

public class GroupingDialog extends TrayDialog {
	
	private GroupingDialogModel model;

	protected GroupingDialog(Shell parentShell, Supplier<CompletableFuture<List<Groups>>> client, Supplier<Map<String, List<String>>> groupings) {
		super(parentShell);
		this.model = new GroupingDialogModel(client, groupings);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		
		ContainerCheckedTreeViewer viewer = new ContainerCheckedTreeViewer(composite, SWT.SINGLE);
		
		viewer.setCheckStateProvider(new ICheckStateProvider() {
			
			@Override
			public boolean isGrayed(Object element) {
				if (element instanceof TreeItem) {
					return ((TreeItem) element).getChecked() == null;
				}
				return false;
			}
			
			@Override
			public boolean isChecked(Object element) {
				if (element instanceof TreeItem) {
					Boolean checked = ((TreeItem) element).getChecked();
					return checked == null || Boolean.TRUE.equals(checked);
				}
				return false;
			}
		});
		
		viewer.setContentProvider(new ITreeContentProvider() {

			@Override
			public Object[] getElements(Object input) {
				if (input instanceof GroupingDialogModel) {
					return ((GroupingDialogModel) input).getLiveSet().getValue().toArray();
				}
				return new Object[0];
			}

			@Override
			public Object[] getChildren(Object p) {
				if (p instanceof ProjectItem) {
					return ((ProjectItem) p).getGroups().toArray();
				}
				return new Object[0];
			}

			@Override
			public Object getParent(Object e) {
				if (e instanceof GroupItem) {
					return ((GroupItem) e).getProjectItem();
				}
				return null;
			}

			@Override
			public boolean hasChildren(Object e) {
				if (e instanceof ProjectItem) {
					return !((ProjectItem) e).getGroups().isEmpty();
				}

				return false;
			}
			
		});
		
		viewer.setLabelProvider(new LabelProvider() {

			@Override
			public String getText(Object e) {
				return e instanceof TreeItem ? ((TreeItem) e).getLabel() : null;
			}
			
		});
		
		viewer.addCheckStateListener(e -> {
			Object o = e.getElement();
			if (o instanceof TreeItem) {
				((TreeItem) o).setChecked(e.getChecked());
			}
		});
		
		viewer.setInput(model);
		
		model.getLiveSet().addListener((e, v) -> {
			UI.getDisplay().asyncExec(viewer::refresh);
		});
		
		model.getLoaded().addListener((e, v) -> {
			UI.getDisplay().asyncExec(viewer::expandAll);
		});
		
		model.load();
		
		viewer.getControl().setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
		
		return composite;
	}
	
	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		IDialogSettings settings = PlatformUI
				.getDialogSettingsProvider(FrameworkUtil.getBundle(getClass()))
				.getDialogSettings();
		String dialogSettingsId = getClass().getName();
		IDialogSettings section = settings.getSection(dialogSettingsId);
		if (section == null) {
			section = settings.addNewSection(dialogSettingsId);
		}
		return section;
	}
	
	@Override
	protected boolean isResizable() {
		return true;
	}
	
	Map<String, List<String>> getResult() {
		return model.getResult();
	}

}
