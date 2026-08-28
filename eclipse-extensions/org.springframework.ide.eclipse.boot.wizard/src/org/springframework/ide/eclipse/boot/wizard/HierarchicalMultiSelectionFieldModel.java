/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.springframework.ide.eclipse.boot.wizard.CheckBoxesSection.CheckBoxModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

/**
 * Model for a UI widget that offers multiple choices. Similar to MultiSelectionFieldModel, however
 * the choices are organized into categories instead of a flat list of choices.
 *
 * @author Kris De Volder
 */

public class HierarchicalMultiSelectionFieldModel<T> {

	private Map<String, MultiSelectionFieldModel<T>> categories = new TreeMap<String, MultiSelectionFieldModel<T>>();

	private String name;
	private String label;
	private Class<T> type;

	public HierarchicalMultiSelectionFieldModel(Class<T> type, String name) {
		this.name = name;
		this.label = name;
		this.type = type;
	}

	public Collection<String> getCategories() {
		return Collections.unmodifiableCollection(categories.keySet());
	}

	public MultiSelectionFieldModel<T> getContents(String category) {
		return categories.get(category);
	}

	public HierarchicalMultiSelectionFieldModel<T> label(String label) {
		this.label = label;
		return this;
	}

	private MultiSelectionFieldModel<T> ensureCategory(String categoryName) {
		MultiSelectionFieldModel<T> existing = categories.get(categoryName);
		if (existing == null) {
			categories.put(categoryName, existing = new MultiSelectionFieldModel<T>(type, name)
					.label(categoryName));
		}
		return existing;
	}

	public void sort() {
		for (String cat : getCategories()) {
			getContents(cat).sort();
		}
	}

	/**
	 * Add a choice to a category, create the category if it doesn't exist yet.
	 */
	public void choice(String catName, String name, T dep, Supplier<String> tooltipHtml, String requirementTooltip, LiveExpression<Boolean> enablement) {
		MultiSelectionFieldModel<T> cat = ensureCategory(catName);
		cat.choice(name, dep, tooltipHtml, requirementTooltip, enablement);
	}

	public void setSelection(String catName, T dep, boolean selected) {
		MultiSelectionFieldModel<T> cat = categories.get(catName);
		if (cat != null) {
			cat.getSelection(dep).setValue(selected);
		}
	}



	public String getLabel() {
		return label;
	}

	public List<T> getCurrentSelection() {
		ArrayList<T> selecteds = new ArrayList<>();
		for (String cat : getCategories()) {
			selecteds.addAll(getContents(cat).getCurrentSelection());
		}
		return Collections.unmodifiableList(selecteds);
	}

	/**
	 * Gets the checkbox models for all the checkboxes in all the categories in a single
	 * list.
	 */
	public List<CheckBoxModel<T>> getAllBoxes() {
		ArrayList<CheckBoxModel<T>> allUsedBoxes = new ArrayList<CheckBoxModel<T>>();
		for (String category : getCategories()) {
			allUsedBoxes.addAll(getContents(category).getCheckBoxModels());
		}
		return allUsedBoxes;
	}

	public void clearSelection() {
		categories.values().forEach(MultiSelectionFieldModel::clearSelection);
	}
}
