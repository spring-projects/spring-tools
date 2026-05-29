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

import org.springframework.ide.eclipse.editor.support.preferences.ProblemSeverityPreferityPageFromMetadata.ProblemParameterData;
import org.springframework.ide.eclipse.editor.support.preferences.ProblemSeverityPreferityPageFromMetadata.ProblemTypeData;

public class ProblemCategoryData {
	private String id;
	private String label;
	private String description;
	private CategoryToggleData toggle;
	private int order;
	private ProblemTypeData[] problemTypes;
	private ProblemParameterData[] parameters;
	
	ProblemCategoryData() {}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public CategoryToggleData getToggle() {
		return toggle;
	}

	public int getOrder() {
		return order;
	}
	
	public String getId() {
		return id;
	}

	public ProblemParameterData[] getParameters() {
		return parameters;
	}

	public ProblemTypeData[] getProblemTypes() {
		return problemTypes;
	}

	public static class CategoryToggleData {
		private String label;
		
		private String[] values;
		
		private String preferenceKey;
		
		private String defaultValue;

		public String getLabel() {
			return label;
		}

		public String[] getValues() {
			return values;
		}

		public String getPreferenceKey() {
			return preferenceKey;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

	}

}
