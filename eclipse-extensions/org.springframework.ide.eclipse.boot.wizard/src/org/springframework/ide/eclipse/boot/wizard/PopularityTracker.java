/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springframework.ide.eclipse.boot.wizard.CheckBoxesSection.CheckBoxModel;

/**
 * Component that keeps track of 'popular' dependencies used in the NewSpringBootWizard.
 * It keeps a persistent count of how many times a given dependency has been used.
 *
 * @author Kris De Volder
 */
public class PopularityTracker {

	public static final String PREFIX = PopularityTracker.class.getName()+".";

	private IPreferenceStore store;

	public PopularityTracker(IPreferenceStore store) {
		this.store = store;
	}

	protected String key(String id) {
		String key = PREFIX+id;
		return key;
	}

	public void incrementUsageCount(Dependency d) {
		incrementUsageCount(d.getId());
	}

	public int getUsageCount(Dependency d) {
		return getUsageCount(d.getId());
	}

	public int getUsageCount(String id) {
		return store.getInt(key(id));
	}

	private void incrementUsageCount(String id) {
		String key = key(id);
		store.setValue(key, store.getInt(key)+1);
	}

	public void incrementUsageCount(Collection<Dependency> selected) {
		for (Dependency d : selected) {
			incrementUsageCount(d);
		}
	}

	public List<CheckBoxModel<Dependency>> getMostPopular(HierarchicalMultiSelectionFieldModel<Dependency> dependencies, int howMany) {
		final Map<CheckBoxModel<Dependency>, Integer> useCounts = new HashMap<CheckBoxModel<Dependency>, Integer>(); //usecounts by dependency id

		ArrayList<CheckBoxModel<Dependency>> allUsedBoxes = new ArrayList<CheckBoxModel<Dependency>>();
		for (String category : dependencies.getCategories()) {
			allUsedBoxes.addAll(dependencies.getContents(category).getCheckBoxModels());
		}

		for (Iterator<CheckBoxModel<Dependency>> iterator = allUsedBoxes.iterator(); iterator.hasNext();) {
			CheckBoxModel<Dependency> cb = iterator.next();
			int useCount = this.getUsageCount(cb.getValue());
			if (useCount == 0) {
				iterator.remove(); //don't care about those options never used at all.
			} else {
				useCounts.put(cb, this.getUsageCount(cb.getValue()));
			}
		}
		Collections.sort(allUsedBoxes, new Comparator<CheckBoxModel<Dependency>>() {
			public int compare(CheckBoxModel<Dependency> o1, CheckBoxModel<Dependency> o2) {
				return useCounts.get(o2) - useCounts.get(o1);
			}
		});

		howMany = Math.min(allUsedBoxes.size(), howMany);
		List<CheckBoxModel<Dependency>> result = new ArrayList<CheckBoxModel<Dependency>>();
		for (int i = 0; i < howMany; i++) {
			result.add(allUsedBoxes.get(i));
		}
		return Collections.unmodifiableList(result);
	}


}
