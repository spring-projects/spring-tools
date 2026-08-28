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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.springframework.ide.eclipse.boot.dash.livexp.DelegatingLiveSet;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelectionSource;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.PageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.util.ReflowUtil;

public class DynamicCompositeSection<M> extends PageSection implements MultiSelectionSource, Disposable {

	public static interface SectionFactory<M> {
		IPageSection create(M model);
	}

	/**
	 * A dynamically changing collection of models. When this changes the DynamicComposite will react by
	 * creating or deleting sections corresponding to the corresponding added/deleted models.
	 */
	private LiveExpression<Set<M>> models;

	/**
	 * Keeps track of sections per model.
	 */
	private Map<M, SubSection> sectionsMap = new LinkedHashMap<>();

	/**
	 * Keeps track of the selected elements
	 */
	private DelegatingLiveSet<?> elements;
	private MultiSelection<?> selection; //Because of the dynamic nature, we can't really know what kinds of selections will get added
											// So we must assume any kinds of object may appear.
	private Composite composite = null; //Set once UI is created

	private SectionFactory<M> sectionFactory;

	private ValueListener<Set<M>> modelListener = new ValueListener<Set<M>>() {
		public void gotValue(LiveExpression<Set<M>> exp, Set<M> value) {
			updateSections();
		}
	};

	private Class<?> selectionType = Object.class;

	private class SubSection {
		Collection<Control> ui;
		IPageSection section;
	}

	public <T> DynamicCompositeSection(IPageWithSections owner, LiveExpression<Set<M>> models, SectionFactory<M> sectionFactory, Class<T> selectionType) {
		super(owner);
		this.models = models;
		DelegatingLiveSet<T> _elements = new DelegatingLiveSet<>();
		this.elements = _elements;
		this.sectionFactory = sectionFactory;
		this.selectionType = selectionType;
		this.selection = MultiSelection.from(selectionType, _elements);
	}

	@Override
	public MultiSelection<?> getSelection() {
		return selection;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return OK_VALIDATOR;
	}

	@Override
	public void createContents(Composite page) {
//		composite = new Composite(page, SWT.NONE);
//		Layout l = new GridLayout();
//		composite.setLayout(l);
//		GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);
		composite = page;

		models.addListener(modelListener);
	}

	/**
	 * Called when we need to update the sections based on the models
	 */
	private synchronized void updateSections() {
		Set<M> currentModels = models.getValue();

		//Missing: current models for which we have no section
		Set<M> missing = new LinkedHashSet<>(currentModels);
		missing.removeAll(sectionsMap.keySet());

		//Extra: current sections for which there is no more model
		Set<M> extra = new HashSet<>();
		extra.addAll(sectionsMap.keySet());
		extra.removeAll(currentModels);

		for (M m : extra) {
			deleteSectionFor(m);
		}

		for (M m : missing) {
			createSectionFor(m);
		}

		boolean dirty = !missing.isEmpty() || !extra.isEmpty();
		if (dirty) {
			ReflowUtil.reflow(owner, composite);
			updateSelectionDelegate();
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void updateSelectionDelegate() {
		Class<T> selectionType = (Class<T>) this.selectionType;
		MultiSelection<T> newSelection = MultiSelection.empty(selectionType);
		for (SubSection s : sectionsMap.values()) {
			if (s.section instanceof MultiSelectionSource) {
				newSelection = combineSelections(
						newSelection,
						((MultiSelectionSource)s.section).getSelection().filter(selectionType)
				);
			}
		}
		@SuppressWarnings("rawtypes")
		ObservableSet newElements = newSelection.getElements();
		elements.setDelegate(newElements);
	}

	protected <T> MultiSelection<T> combineSelections(MultiSelection<T> a, MultiSelection<T> b) {
		return MultiSelection.union(a, b);
	}

	private void createSectionFor(M m) {
		Set<Control> oldWidgets = new HashSet<>(Arrays.asList(composite.getChildren()));
		SubSection s = new SubSection();
		s.section = sectionFactory.create(m);
		s.section.createContents(composite);
		s.ui = new HashSet<>(Arrays.asList(composite.getChildren()));
		s.ui.removeAll(oldWidgets);
		sectionsMap.put(m, s);
	}

	private void deleteSectionFor(M m) {
		SubSection s = sectionsMap.get(m);
		sectionsMap.remove(m);
		if (s.section instanceof Disposable) {
			((Disposable) s.section).dispose();
		}
		if (s.ui != null) {
			for (Control widget : s.ui) {
				widget.dispose();
			}
		}
	}

	@Override
	public void dispose() {
		if (modelListener != null) {
			models.removeListener(modelListener);
		}
		if (sectionsMap != null) {
			for (SubSection s : sectionsMap.values()) {
				if (s.section instanceof Disposable) {
					((Disposable)s.section).dispose();
				}
			}
		}
	}

}
