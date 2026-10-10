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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.FrameworkUtil;
import org.springframework.tooling.boot.ls.views.StructureClient.DependencyDescriptor;

/**
 * Lets the user select which of the dependencies of a project are included in its tree - the
 * workspace projects it depends on and its libraries. The list can be narrowed down by typing.
 * 
 * @author Martin Lippert
 */
class DependencySelectionDialog extends TrayDialog {

	private final String projectName;
	private final List<DependencyDescriptor> dependencies;
	private final Set<String> checked = new LinkedHashSet<>();

	private String filter = "";

	private CheckboxTableViewer viewer;

	/**
	 * @param dependencies the dependencies to choose from, in the order to show them
	 * @param selected the ids of the dependencies selected so far
	 */
	DependencySelectionDialog(Shell parentShell, String projectName, List<DependencyDescriptor> dependencies, List<String> selected) {
		super(parentShell);
		this.projectName = projectName;
		this.dependencies = dependencies;
		this.checked.addAll(selected);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Select Dependencies");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);

		Label message = new Label(composite, SWT.WRAP);
		message.setText("Select the dependencies to include in the structure of project '" + projectName + "':");
		message.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		Text filterText = new Text(composite, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		filterText.setMessage("type filter text");
		filterText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		filterText.addModifyListener(e -> {
			filter = filterText.getText();
			viewer.refresh();
		});

		Composite tableComposite = new Composite(composite, SWT.NONE);
		tableComposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).hint(750, 350).create());
		TableColumnLayout columnLayout = new TableColumnLayout();
		tableComposite.setLayout(columnLayout);

		Table table = new Table(tableComposite, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		table.setHeaderVisible(true);
		table.setLinesVisible(false);
		viewer = new CheckboxTableViewer(table);
		viewer.setContentProvider(ArrayContentProvider.getInstance());

		addColumn(columnLayout, "Dependency", 30, d -> d.displayName());
		addColumn(columnLayout, "Workspace project or library", 30, DependencyChoices::describe);
		addColumn(columnLayout, "Location", 40, d -> d.location() == null ? "" : d.location());

		viewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer v, Object parentElement, Object element) {
				return DependencyChoices.matches((DependencyDescriptor) element, filter);
			}
		});
		viewer.setCheckStateProvider(new ICheckStateProvider() {
			@Override
			public boolean isChecked(Object element) {
				return checked.contains(((DependencyDescriptor) element).id());
			}

			@Override
			public boolean isGrayed(Object element) {
				return false;
			}
		});
		viewer.addCheckStateListener(e -> {
			String id = ((DependencyDescriptor) e.getElement()).id();
			if (e.getChecked()) {
				checked.add(id);
			} else {
				checked.remove(id);
			}
		});
		viewer.setInput(dependencies);

		Composite buttons = new Composite(composite, SWT.NONE);
		buttons.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(true).create());
		buttons.setLayoutData(GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.CENTER).create());
		Button selectAll = new Button(buttons, SWT.PUSH);
		selectAll.setText("Select All");
		selectAll.setToolTipText("Select all the dependencies shown");
		selectAll.addListener(SWT.Selection, e -> setShown(true));
		Button deselectAll = new Button(buttons, SWT.PUSH);
		deselectAll.setText("Deselect All");
		deselectAll.setToolTipText("Deselect all the dependencies shown");
		deselectAll.addListener(SWT.Selection, e -> setShown(false));

		return composite;
	}

	private void addColumn(TableColumnLayout columnLayout, String title, int weight, java.util.function.Function<DependencyDescriptor, String> text) {
		TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
		column.getColumn().setText(title);
		columnLayout.setColumnData(column.getColumn(), new ColumnWeightData(weight, 80));
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return text.apply((DependencyDescriptor) element);
			}
		});
	}

	/**
	 * Selects or deselects the dependencies that are shown - those that match the filter.
	 */
	private void setShown(boolean select) {
		for (DependencyDescriptor dependency : dependencies) {
			if (DependencyChoices.matches(dependency, filter)) {
				if (select) {
					checked.add(dependency.id());
				} else {
					checked.remove(dependency.id());
				}
			}
		}
		viewer.refresh();
	}

	/**
	 * The ids of the dependencies selected, in the order they were shown.
	 */
	List<String> getSelectedIds() {
		return dependencies.stream().map(DependencyDescriptor::id).filter(checked::contains).toList();
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

}
