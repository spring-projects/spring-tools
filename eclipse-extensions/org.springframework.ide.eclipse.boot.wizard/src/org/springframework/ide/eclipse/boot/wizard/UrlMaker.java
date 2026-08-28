/*******************************************************************************
 * Copyright (c) 2013, 2016 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ide.eclipse.boot.core.SimpleUriBuilder;
import org.springframework.ide.eclipse.boot.core.initializr.IdAble;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

/**
 * LiveExpression that computes a URL String based on a number of input fields.
 */
public class UrlMaker extends LiveExpression<String> {

	private final List<FieldModel<String>> inputs = new ArrayList<FieldModel<String>>();
	private final List<MultiSelectionFieldModel<IdAble>> multiInputs = new ArrayList<MultiSelectionFieldModel<IdAble>>();
	private final List<FieldModel<RadioInfo>> radioInputs = new ArrayList<FieldModel<RadioInfo>>();

	private final LiveExpression<String> baseUrl;

	public UrlMaker(String baseUrl) {
		this(LiveExpression.constant(baseUrl));
	}

	public UrlMaker(LiveExpression<String> baseUrl) {
		this.baseUrl = baseUrl;
		dependsOn(baseUrl);
	}

	public UrlMaker addField(FieldModel<String> param) {
		inputs.add(param);
		dependsOn(param.getVariable()); //Recompute my value when the input changes.
		return this;
	}

	@SuppressWarnings("unchecked")
	public <T extends IdAble> UrlMaker addField(MultiSelectionFieldModel<T> param) {
		multiInputs.add((MultiSelectionFieldModel<IdAble>) param);
		for (T choice : param.getChoices()) {
			//Recomput value when any 'checkbox' selection status changes
			dependsOn(param.getSelection(choice));
		}
		return this;
	}

	public void addField(HierarchicalMultiSelectionFieldModel<? extends IdAble> param) {
		for (String cat : param.getCategories()) {
			addField(param.getContents(cat));
		}
	}

	public UrlMaker addField(RadioGroup group) {
		this.radioInputs.add(group);
		dependsOn(group.getVariable());
		return this;
	}

	@Override
	protected String compute() {
		String baseUrl = this.baseUrl.getValue();
		if (baseUrl == null) {
			baseUrl = "";
		} else {
			baseUrl = baseUrl.trim();
		}
		SimpleUriBuilder builder = new SimpleUriBuilder(baseUrl);
		for (FieldModel<String> f : inputs) {
			String paramValue = f.getValue();
			if (paramValue != null) {
				builder.addParameter(f.getName(), paramValue);
			}
		}

		for (FieldModel<RadioInfo> f : radioInputs) {
			RadioInfo radio = f.getValue();
			if (radio != null) {
				String paramValue = radio.getUrlParamValue();
				if (paramValue != null) {
					builder.addParameter(f.getName(), paramValue);
				}
			}
		}

		for (MultiSelectionFieldModel<IdAble> mf : multiInputs) {
			String name = mf.getName();
			for (IdAble selectedValue : mf.getCurrentSelection()) {
				//Note that it is possible for values to be selected and disabled at the same time
				// (i.e. a checkbox can be checked and 'greyed' out at the same time)
				//We must therefore check enablement before adding a selection to the URI.
				if (mf.getEnablement(selectedValue).getValue()) {
					builder.addParameter(name, selectedValue.getId());
				}
			}
		}

		return builder.toString();
	}

}
