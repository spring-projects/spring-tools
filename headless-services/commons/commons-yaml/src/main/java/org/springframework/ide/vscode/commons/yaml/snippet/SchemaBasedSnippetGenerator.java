/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.snippet;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.languageserver.util.PlaceHolderString;
import org.springframework.ide.vscode.commons.languageserver.util.SnippetBuilder;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.util.YamlIndentUtil;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/**
 * An implementation of {@link TypeBasedSnippetProvider} that generates snippets
 * automatically from schema types.
 */
public class SchemaBasedSnippetGenerator implements TypeBasedSnippetProvider {

	private YTypeUtil typeUtil;
	private Supplier<SnippetBuilder> snippetBuilderFactory;

	public SchemaBasedSnippetGenerator(YTypeUtil typeUtil, Supplier<SnippetBuilder> snippetBuilderFactory) {
		super();
		this.typeUtil = typeUtil;
		this.snippetBuilderFactory = snippetBuilderFactory;
	}

	private int maxNesting = Integer.MAX_VALUE;

	@Override
	public Collection<Snippet> getSnippets(YType type) {
		return generateSnippets(type);
	}

	private Collection<Snippet> generateSnippets(YType type) {
		//Generate a 'full' snippet that defines all required properties of the current type.
		Snippet snippet = generateFullSnippet(type, 0);
		return snippet == null ? ImmutableList.of() : ImmutableList.of(snippet);
	}

	private Snippet generateFullSnippet(YType type, int indent) {
		if (typeUtil.isBean(type)) {
			SnippetBuilder builder = snippetBuilderFactory.get();
			List<YTypedProperty> requiredProps = typeUtil.getProperties(type).stream()
			.filter(p -> p.isRequired())
			.collect(CollectorUtil.toImmutableList());
			if (!requiredProps.isEmpty()) {
				generateBeanSnippet(requiredProps, builder, indent, maxNesting);
			}
			if (builder.getPlaceholderCount() >= 2) {
				return new Snippet(typeUtil.niceTypeName(type)+" Snippet", builder.build(), (dc) ->
					requiredProps.stream().noneMatch(p -> dc.getDefinedProperties().contains(p.getName()))
				);
			}
		}
		return null;
	}

	private void generateBeanSnippet(List<YTypedProperty> props, SnippetBuilder builder, int indent, int nestingLimit) {
		if (nestingLimit > 0 && !props.isEmpty()) {
			boolean first = true;
			for (YTypedProperty p : props) {
				if (!first) {
					builder.newline(indent);
				}
				builder.text(p.getName());
				builder.text(":");
				generateNestedSnippet(false, p.getType(), builder, indent, nestingLimit-1);
				first = false;
			}
		} else {
			//reached the limit of bean number of nested property expansions allowed.
			builder.placeHolder();
		}
	}

	@Override
	public Snippet getSnippet(List<YTypedProperty> props) {
		SnippetBuilder builder = snippetBuilderFactory.get();
		generateBeanSnippet(props, builder, 0, maxNesting);
		String snippetName;
		if (props.size() == 1) {
			snippetName = props.get(0).getName();
		} else {
			snippetName = props.stream().map(p -> p.getName()).collect(Collectors.toList()).toString() + " Snippet";
		}
		return new Snippet(snippetName, builder.build(), (dc) ->
			props.stream().allMatch(p -> !dc.getDefinedProperties().contains(p.getName()))
		);
	}


	private void generateNestedSnippet(boolean parentIsSeq, YType type, SnippetBuilder builder, int indent, int nestingLimit) {
		if (type == null) {
			//Assume its some kind of pojo bean
			builder.newline(indent+YamlIndentUtil.INDENT_BY);
			builder.placeHolder();
		} else if (typeUtil.isBean(type) || typeUtil.isMap(type)) {
			if (!parentIsSeq) {
				//ready to enter nested keys on next line
				indent += YamlIndentUtil.INDENT_BY;
				builder.newline(indent);
			}
			//Insert required keys
			List<YTypedProperty> requiredProps = typeUtil.getProperties(type).stream()
			.filter(p -> p.isRequired())
			.collect(Collectors.toList());
			generateBeanSnippet(requiredProps, builder, indent, nestingLimit);
		} else if (typeUtil.isSequencable(type)) {
			//ready to enter sequence element on next line
			builder.newline(indent);
			builder.text("- ");
			indent += YamlIndentUtil.INDENT_BY;
			generateNestedSnippet(true, typeUtil.getDomainType(type), builder, indent, nestingLimit);
		} else { //Treat like atomic
			//ready to enter whatever on the same line
			builder.ensureSpace();
			builder.placeHolder();
		}
	}

}
