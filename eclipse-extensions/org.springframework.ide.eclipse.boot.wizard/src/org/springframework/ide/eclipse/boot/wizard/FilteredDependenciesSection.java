/*******************************************************************************
 * Copyright (c) 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.ui.ExpandableSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.Scroller;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;

public class FilteredDependenciesSection extends WizardPageSection {

	private Composite dependencyArea;
	private Scroller scroller;

	private LiveExpression<Filter<Dependency>> filter;

	private Point sizeHint = new Point(SWT.DEFAULT, SWT.DEFAULT);

	private int columns = 1;
	private HierarchicalMultiSelectionFieldModel<Dependency> dependencies;

	public FilteredDependenciesSection(IPageWithSections owner, NewSpringBootWizardModel model,
			LiveExpression<Filter<Dependency>> filter) {
		this(owner, model.dependencies, filter);
	}

	public FilteredDependenciesSection(IPageWithSections owner,  HierarchicalMultiSelectionFieldModel<Dependency> dependencies,
			LiveExpression<Filter<Dependency>> filter) {
		super(owner);
		this.dependencies = dependencies;
		this.filter = filter;
	}

	public FilteredDependenciesSection sizeHint(Point sizeHint) {
		if (sizeHint != null) {
			this.sizeHint = sizeHint;
		}
		return this;
	}

	public FilteredDependenciesSection columns(int columns) {
		this.columns = columns;
		return this;
	}

	@Override
	public void createContents(Composite page) {
		scroller = new Scroller(page);
		GridDataFactory.fillDefaults().grab(true, true).minSize(sizeHint).hint(sizeHint).applyTo(scroller);
		dependencyArea = scroller.getBody();
		GridLayoutFactory.fillDefaults().applyTo(dependencyArea);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(dependencyArea);
		Map<String, CheckboxExpandableSection<Dependency>> sectionsToRefresh = new HashMap<>();
		for (String cat : dependencies.getCategories()) {
			MultiSelectionFieldModel<Dependency> dependencyGroup = dependencies.getContents(cat);
			CheckBoxesSection<Dependency> checkboxesSection = new CheckBoxesSection<>(owner,
					dependencyGroup.getCheckBoxModels()).columns(columns);
			CheckboxExpandableSection<Dependency> expandable = new CheckboxExpandableSection<>(owner, dependencyGroup.getLabel(), checkboxesSection);
			expandable.createContents(dependencyArea);

			// Always expanded as it only shows selections. If there are no
			// selections, the expandable
			// section itself is hidden
			expandable.getExpansionState().setValue(false);

			sectionsToRefresh.put(cat, expandable);
		}
		this.filter.addListener((exp, value) -> {
			// PT 143003753: there is a bit of lag when deleting characters in text filter that produce a lot of
			// results. Consequently running this asynchronously.
			Display.getCurrent().asyncExec(() -> {
				for (String cat : dependencies.getCategories()) {
					CheckboxExpandableSection<Dependency> expandable = sectionsToRefresh.get(cat);
					if (expandable != null) {
						onFilter(expandable, expandable.getCheckBoxSection(), cat);
					}
				}
			});
		});
	}

	private void onFilter(ExpandableSection expandable, CheckBoxesSection<Dependency> checkboxesSection, String cat) {
		Filter<Dependency> filter = this.filter.getValue();
		checkboxesSection.applyFilter(filter);
		boolean isTrivialFilter = filter == null || filter.isTrivial();
		if (checkboxesSection.isCreated()) {
			boolean hasVisible = checkboxesSection.hasVisible();
			expandable.setVisible(hasVisible);
			if (hasVisible) {
				expandable.getExpansionState().setValue(!isTrivialFilter);
			}
		}
		layout();
	}

	private void layout() {
		if (dependencyArea != null && !dependencyArea.isDisposed()) {
			dependencyArea.layout(true);
			dependencyArea.getParent().layout(true);
		}
	}

	static class CheckboxExpandableSection<T> extends ExpandableSection {

		private CheckBoxesSection<T> checkBoxSection;
		public CheckboxExpandableSection(IPageWithSections owner, String title, CheckBoxesSection<T> checkBoxSection) {
			super(owner, title, checkBoxSection);
			this.checkBoxSection = checkBoxSection;
		}

		public CheckBoxesSection<T> getCheckBoxSection() {
			return checkBoxSection;
		}

	}
}
