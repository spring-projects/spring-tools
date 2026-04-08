/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.prefs;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.ui.PlatformUI;
import org.springframework.ide.eclipse.editor.support.preferences.ProblemSeverityPreferencesUtil;
import org.springframework.ide.eclipse.editor.support.preferences.ProblemSeverityPreferityPageFromMetadata;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springframework.tooling.boot.ls.prefs.ProblemCategoryData.CategoryToggleData;

import com.google.common.collect.ImmutableList;

public class CategoryProblemsSeverityPrefsPage extends ProblemSeverityPreferityPageFromMetadata {
	
	public static ImmutableList<ProblemCategoryData> ALL_PROBLEM_CATEGORIES;
	
	private static final String PREF_KEY_PREFIX = "";
	
	private ProblemCategoryData category;

	public CategoryProblemsSeverityPrefsPage(ProblemCategoryData category) {
		super(new ProblemSeverityPreferencesUtil("problem." + category.getId() + "."), category.getProblemTypes());
		this.category = category;
		setTitle(category.getLabel());
	}

	@Override
	protected String getPluginId() {
		return BootLanguageServerPlugin.PLUGIN_ID;
	}

	@Override
	protected String getProblemParametersPreferencePrefix() {
		return "problem-parameters." + category.getId() + ".";
	}
	
	@Override
	protected void initializeDefaults() {
		if (category.getToggle() != null) {
			IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(getPluginId());
			defaults.put(PREF_KEY_PREFIX + category.getToggle().getPreferenceKey(), category.getToggle().getDefaultValue());

		}
		super.initializeDefaults();		
	}
	
	@Override
	protected void createFieldEditors() {
		if (category.getToggle() != null) {
			CategoryToggleData toggle = category.getToggle();
			ComboFieldEditor field = new ComboFieldEditor(
					PREF_KEY_PREFIX + toggle.getPreferenceKey(),
					toggle.getLabel(),
					createToggleValues(toggle.getValues()),
					getFieldEditorParent()
			);
			addField(field);
		}
		super.createFieldEditors();
	}
	
	private static String[][] createToggleValues(String[] values) {
		String[][] res = new String[values.length][2];
		for (int i = 0; i < values.length; i++) {
			String value = values[i];
			res[i][0] = value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
			res[i][1] = value;
		}
		return res;
	}

	public static void loadProblemCategoriesIntoPreferences() throws Exception {
		List<ProblemCategoryData> categories = LanguageServerProblemTypesMetadata.load();
		
		Collections.sort(categories, (e1, e2) -> e1.getOrder() - e2.getOrder());
		
		for (ProblemCategoryData categoryData : categories) {
			
			//create a new PreferenceNode that will appear in the Preference window
			PreferenceNode node = new PreferenceNode(BootLanguageServerPlugin.PLUGIN_ID + "." + categoryData.getId()) {

				@Override
				public void createPage() {
					CategoryProblemsSeverityPrefsPage page = new CategoryProblemsSeverityPrefsPage(categoryData);
					page.init(PlatformUI.getWorkbench());
					page.setTitle(getLabelText());
					setPage(page);
				}

				@Override
				public String getLabelText() {
					return categoryData.getLabel();
				}				
				
			};
			
			//use workbenches's preference manager
			PreferenceManager pm= PlatformUI.getWorkbench().getPreferenceManager();

			pm.addTo("org.springsource.ide.eclipse.commons.preferencePage/org.springframework.tooling.boot.validation", node); //add the node in the PreferenceManager
		}
		
		ALL_PROBLEM_CATEGORIES = ImmutableList.copyOf(categories);
	}
	

}
