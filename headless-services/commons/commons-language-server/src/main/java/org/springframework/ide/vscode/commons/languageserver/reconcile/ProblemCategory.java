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
package org.springframework.ide.vscode.commons.languageserver.reconcile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProblemCategory {
	
	private static AtomicInteger counter = new AtomicInteger();
	
	public static final ProblemCategory NO_CATEGORY = new ProblemCategory("uncategorized", "Uncategorized", null);

	final private String id;
	
	final private String label;
	
	final private List<ProblemType> problemTypes = new ArrayList<>();;
	
	final private Toggle toggle;
	final private List<ProblemTypeParameter> parameters = new ArrayList<>();
	
	final public int order;
		
	public ProblemCategory(String id, String label, Toggle toggle) {
		this(id, label, toggle, null);
	}

	public ProblemCategory(String id, String label, Toggle toggle, List<ProblemTypeParameter> parameters) {
		this.id = id;
		this.label = label;
		this.toggle = toggle;
		if (parameters != null) {
			this.parameters.addAll(parameters);
		}
		this.order = counter.getAndIncrement();
	}
	
	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public List<ProblemType> getProblemTypes() {
		return problemTypes;
	}

	public List<ProblemTypeParameter> getParameters() {
		return parameters;
	}

	public Toggle getToggle() {
		return toggle;
	}

	public static final class Toggle {
		
		public enum Option {
			AUTO,
			OFF,
			ON		
		}
		
		private final String label;
		
		private final EnumSet<Option> values;
		
		private final String preferenceKey;
		
		private final Option defaultValue;

		public Toggle(String label, EnumSet<Option> values, Option defaultValue, String preferenceKey) {
			this.label = label;
			this.values = values;
			this.defaultValue = defaultValue;
			this.preferenceKey = preferenceKey;
		}

		public String getLabel() {
			return label;
		}

		public EnumSet<Option> getValues() {
			return values;
		}

		public String getPreferenceKey() {
			return preferenceKey;
		}

		public Option getDefaultValue() {
			return defaultValue;
		}
			
	}

}
