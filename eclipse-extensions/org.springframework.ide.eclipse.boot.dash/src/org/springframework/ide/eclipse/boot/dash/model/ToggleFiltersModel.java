/*******************************************************************************
 * Copyright (c) 2015, 2021 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.frameworks.core.util.StringUtils;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Ilabelable;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Filters;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableSet;

/**
 * The 'toggle filters' are fixed set of view filters that can be toggled on/off by the user.
 * This model element tracks the currently selected 'toggle' filters and the corresponding composite
 * filter that results from composing them.
 *
 * @author Kris De Volder
 */
public class ToggleFiltersModel {

	private static final Filter<BootDashElement> HIDE_SOLITARY_CONFS = new Filter<BootDashElement>() {
		public boolean accept(BootDashElement e) {
			if (e instanceof LaunchConfDashElement) {
				LaunchConfDashElement conf = (LaunchConfDashElement) e;
				return conf.getParent().getCurrentChildren().size() != 1;
			}
			return true;
		}
	};

	private static final Filter<BootDashElement> HIDE_NON_WORKSPACE_ELEMENTS = new Filter<BootDashElement>() {
		public boolean accept(BootDashElement t) {
			if (t != null) {
				IProject p = t.getProject();
				return p != null && p.exists();
			}
			return false;
		}
	};

	private static final Filter<BootDashElement> HIDE_LOCAL_SERVICES = new Filter<BootDashElement>() {
		@Override
		public boolean accept(BootDashElement t) {
			return !(t instanceof LocalCloudServiceDashElement);
		}
	};

	private static final Filter<BootDashElement> HIDE_LOCAL_NOT_RUNNABLE_APPS = new Filter<BootDashElement>() {
		@Override
		public boolean accept(BootDashElement t) {
			if (t instanceof BootProjectDashElement) {
				if (!t.getCurrentChildren().isEmpty()) {
					return true;
				}
				return ((BootProjectDashElement)t).hasMainMethod();
			}
			return true;
		}
	};

	private final Filter<BootDashElement> REGEX_FILTER = new Filter<BootDashElement>() {

		@Override
		public boolean accept(BootDashElement t) {
			String regex = regexFilter.getValue();
			if (t.getName() != null && StringUtils.hasText(regex)) {
				return !t.getName().matches(regex);
			}
			return true;
		}

	};

	public static final FilterChoice FILTER_CHOICE_HIDE_NON_WORKSPACE_ELEMENTS = new FilterChoice(
			"hide.non-workspace",
			"Hide non-workspace elements",
			"Elements not backed by a project in the workspaces will be not shown. For example elements fetched from Cloud Foundry instance.",
			HIDE_NON_WORKSPACE_ELEMENTS);

	public static final FilterChoice FILTER_CHOICE_HIDE_SOLITARY_CONFS = new FilterChoice(
			"hide.solitary-launch-config",
			"Hide solitary launch configs",
			"Launch configurations will not be shown if it the one and only configuration that exist for a Boot project or any other Boot Dashboard top level element",
			HIDE_SOLITARY_CONFS,
			true);

	public static final FilterChoice FILTER_CHOICE_HIDE_LOCAL_SERVICES = new FilterChoice(
			"hide.local-cloud-services",
			"Hide local cloud services",
			"Local Spring Cloud Service coming from Spring Cloud CLI instance pointed to from STS preferences will not be shown",
			HIDE_LOCAL_SERVICES,
			true);

	public static final FilterChoice FILTER_CHOICE_HIDE_NOT_RUNNABLE_APPS = new FilterChoice(
			"hide.not-runnable-apps",
			"Hide local non-runnable apps",
			"Spring Boot projects that cannot be laucnhed such as Spring Boot library projects not having a main method will not be shown",
			HIDE_LOCAL_NOT_RUNNABLE_APPS,
			false);

	private static final String STORE_ID = "toggle-filters";
	private static final FilterChoice[] FILTERS = {
			FILTER_CHOICE_HIDE_NON_WORKSPACE_ELEMENTS,
			FILTER_CHOICE_HIDE_SOLITARY_CONFS,
			FILTER_CHOICE_HIDE_LOCAL_SERVICES,
			FILTER_CHOICE_HIDE_NOT_RUNNABLE_APPS
	};

	private static final String REGEX_FILTER_ID = "regexFilter";
	private static final String ENABLE_REGEX_FILTER = "enableRegexFilter";

	private final PropertyStoreApi persistentProperties;

	public ToggleFiltersModel(BootDashModelContext context) {
		this(PropertyStores.createSubStore(STORE_ID, context.getViewProperties()));
	}

	public ToggleFiltersModel(IPropertyStore propertyStore) {
		this.persistentProperties = new PropertyStoreApi(propertyStore);
		this.selectedFilters = new LiveSetVariable<>(restoreFilters(), AsyncMode.SYNC);
		this.regexFilter = new LiveVariable<>(persistentProperties.get(REGEX_FILTER_ID));
		this.enableRegexFilter = new LiveVariable<>(persistentProperties.get(ENABLE_REGEX_FILTER, false));
		this.compositeFilter = new LiveExpression<Filter<BootDashElement>>() {
			{
				dependsOn(selectedFilters);
				dependsOn(regexFilter);
				dependsOn(enableRegexFilter);
			}
			@Override
			protected Filter<BootDashElement> compute() {
				Filter<BootDashElement> composed = enableRegexFilter.getValue().booleanValue() ? REGEX_FILTER : Filters.acceptAll();
				for (FilterChoice chosen : selectedFilters.getValues()) {
					composed = Filters.compose(composed, chosen.getFilter());
				}
				return composed;
			}
		};


		selectedFilters.addListener(new ValueListener<ImmutableSet<FilterChoice>>() {
			public void gotValue(LiveExpression<ImmutableSet<FilterChoice>> exp, ImmutableSet<FilterChoice> value) {
				saveFilters(value);
			}
		});

		enableRegexFilter.addListener((exp, v) -> {
			try {
				persistentProperties.put(ENABLE_REGEX_FILTER, exp.getValue().booleanValue());
			} catch (Exception e) {
				Log.log(e);
			}
		});

		regexFilter.addListener((expr, v) -> {
			try {
				String value = expr.getValue();
				if (StringUtils.hasText(value)) {
					persistentProperties.put(REGEX_FILTER_ID, value);
				} else {
					persistentProperties.put(REGEX_FILTER_ID, (String) null);
				}
			} catch (Exception e) {
				Log.log(e);
			}
		});
	}

	public static class FilterChoice implements Ilabelable {
		private final String id;
		private final String label;
		private final String description;
		private final Filter<BootDashElement> filter;
		private final boolean defaultEnable;

		public FilterChoice(String id, String label, String description, Filter<BootDashElement> filter) {
			this(id, label, description, filter, false);
		}

		public FilterChoice(String id, String label, String description, Filter<BootDashElement> filter, boolean defaultEnable) {
			this.id = id;
			this.label = label;
			this.description = description;
			this.filter = filter;
			this.defaultEnable = defaultEnable;
		}

		@Override
		public String toString() {
			return "FilterChoice("+getLabel()+")";
		}

		@Override
		public String getLabel() {
			return label;
		}

		public String getDescription() {
			return description;
		}

		public Filter<BootDashElement> getFilter() {
			return filter;
		}

		public String getId() {
			return id;
		}
	}

	private final LiveSetVariable<FilterChoice> selectedFilters;
	private final LiveVariable<String> regexFilter;
	private final LiveVariable<Boolean> enableRegexFilter;
	private final LiveExpression<Filter<BootDashElement>> compositeFilter;

	/**
	 * @return The filter that is defined by composing all the selected toggle filters.
	 */
	public LiveExpression<Filter<BootDashElement>> getFilter() {
		return compositeFilter;
	}
	private Set<FilterChoice> restoreFilters() {
		Set<FilterChoice> builder = new HashSet<>();
		for (FilterChoice filter : getAvailableFilters()) {
			if (persistentProperties.get(filter.getId(), filter.defaultEnable)) {
				builder.add(filter);
			}
		}
		return builder;
	}

	private void saveFilters(ImmutableSet<FilterChoice> filters) {
		try {
			for (FilterChoice f : getAvailableFilters()) {
				boolean active = filters.contains(f);
				if (active == f.defaultEnable) {
					//don't store default values that way if we change the default in the future then
					// users will get the new default rather than their persisted value
					persistentProperties.put(f.getId(), (String)null);
				} else {
					persistentProperties.put(f.getId(), active);
				}
			}
		} catch (Exception e) {
			//trouble saving filters... log and move on. This is not critical
			Log.log(e);
		}
	}

	public FilterChoice[] getAvailableFilters() {
		return FILTERS;
	}
	public LiveSetVariable<FilterChoice> getSelectedFilters() {
		return selectedFilters;
	}
	public LiveVariable<String> getRegexFilter() {
		return regexFilter;
	}
	public LiveVariable<Boolean> getEnableRegexFilter() {
		return enableRegexFilter;
	}
}
