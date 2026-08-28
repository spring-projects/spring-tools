/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata.hints;

import java.util.Collection;
import java.util.List;

import org.springframework.ide.vscode.boot.configurationmetadata.ValueHint;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry.ValueProviderStrategy;
import org.springframework.ide.vscode.boot.metadata.types.TypedProperty;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * A single hint provider that combines the two kinds of 'hint' metadata that spring boot
 * may contain (namely 'values' data and 'valueProvider' data).
 * <p>
 * This basic hint provider is not context-aware and returns the same value hints regardless of the
 * yaml context.
 * <p>
 * This hint provider doesn't provide 'property' hints because property hints typically require
 * context information (i.e the type of the parent context).
 * <p>
 * To make this provider 'context aware' it can be wrapped in an adapter created by calling
 * one of the static methods in the {@link HintProviders} class.
 *
 * @author Kris De Volder
 */
public class BasicHintProvider implements HintProvider {

	private IJavaProject javaProject;
	private ImmutableList<ValueHint> valueHints;
	private ValueProviderStrategy valueProvider;

	public BasicHintProvider(IJavaProject javaProject,
			ImmutableList<ValueHint> valueHints,
			ValueProviderStrategy valueProvider) {
		this.javaProject = javaProject;
		this.valueHints = valueHints;
		this.valueProvider = valueProvider;
	}

	@Override
	public HintProvider traverse(YamlPathSegment s) throws Exception {
		//since this provider is not context sensitive it just returns itself (So hints provides in 'sub-contexts'
		// are exaclty the same as hints in the parent context.
		return this;
	}

	@Override
	public List<StsValueHint> getValueHints(String query) {
		Builder<StsValueHint> builder = ImmutableList.builder();
		if (CollectionUtil.hasElements(valueHints)) {
			for (ValueHint hint : valueHints) {
				builder.add(StsValueHint.create(hint));
			}
		}
		if (valueProvider != null) {
			Collection<StsValueHint> provided = valueProvider.getValuesNow(javaProject, query);
			if (CollectionUtil.hasElements(provided)) {
				builder.addAll(provided);
			}
		}
		return builder.build();
	}

	@Override
	public List<TypedProperty> getPropertyHints(String query) {
		return ImmutableList.of();
	}


}
