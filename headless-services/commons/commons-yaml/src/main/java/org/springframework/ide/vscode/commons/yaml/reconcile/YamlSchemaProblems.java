/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.reconcile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.DiagnosticTag;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemTypes;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Methods for creating reconciler problems for Schema based reconciler implementation.
 *
 * @author Kris De Volder
 */
public class YamlSchemaProblems {
	
	public static final ProblemCategory CATEGORY = new ProblemCategory("yaml-schema-problems", "YAML Shema Problems", null);

	public static final ProblemType SYNTAX_PROBLEM = problemType("YamlSyntaxProblem", CATEGORY);
	public static final ProblemType SCHEMA_PROBLEM = problemType("YamlSchemaProblem", CATEGORY);
	public static final ProblemType DEPRECATED_PROPERTY = problemType("DeprecatedProperty", ProblemSeverity.WARNING, CATEGORY, List.of(DiagnosticTag.Deprecated));
	public static final ProblemType DEPRECATED_VALUE = problemType("DeprecatedValue", ProblemSeverity.WARNING, CATEGORY, List.of(DiagnosticTag.Deprecated));
	public static final ProblemType MISSING_PROPERTY = problemType("MissingProperty", ProblemSeverity.ERROR, CATEGORY);
	public static final ProblemType EXTRA_PROPERTY = problemType("ExtraProperty", ProblemSeverity.ERROR, CATEGORY);
	public static final ProblemType EMPTY_OPTIONAL_STRING = problemType("EmptyOptionalString", ProblemSeverity.WARNING, CATEGORY);

	public static final Set<ProblemType> PROPERTY_CONSTRAINT = ImmutableSet.of(
			MISSING_PROPERTY, EXTRA_PROPERTY
	);

	public static ProblemType problemType(final String typeName, ProblemSeverity defaultSeverity, ProblemCategory category, List<DiagnosticTag> tags) {
		return ProblemTypes.create(typeName, defaultSeverity, category, tags);
	}

	public static ProblemType problemType(final String typeName, ProblemSeverity defaultSeverity, ProblemCategory category) {
		return problemType(typeName, defaultSeverity, category, null);
	}

	public static ProblemType problemType(final String typeName, ProblemCategory category) {
		return problemType(typeName, ProblemSeverity.ERROR, category);
	}

	public static ReconcileProblem syntaxProblem(String msg, int offset, int len) {
		return new ReconcileProblemImpl(SYNTAX_PROBLEM, msg, offset, len);
	}

	public static ReconcileProblem schemaProblem(String msg, Node node) {
		int start = node.getStartMark().getIndex();
		int end = node.getEndMark().getIndex();
		return new ReconcileProblemImpl(SCHEMA_PROBLEM, msg, start, end-start);
	}

	public static ReconcileProblem schemaProblem(String msg, DocumentRegion node) {
		return new ReconcileProblemImpl(SCHEMA_PROBLEM, msg, node.getStart(), node.getLength());
	}

	public static ReconcileProblem deprecatedProperty(String msg, Node node) {
		return problem(DEPRECATED_PROPERTY, msg, node);
	}

	public static ReconcileProblem deprecatedProperty(Node node, YType bean, YTypedProperty property) {
		return deprecatedProperty("Property '"+property.getName()+"' of '"+bean+"' is Deprecated", node);
	}

	public static ReconcileProblemImpl problem(ProblemType problemType, String msg, DocumentRegion node) {
		int start = node.getStart();
		int end = node.getEnd();
		return new ReconcileProblemImpl(problemType, msg, start, end-start);
	}

	public static ReconcileProblem problem(ProblemType problemType, String msg, Node node) {
		int start = node.getStartMark().getIndex();
		int end = node.getEndMark().getIndex();
		return new ReconcileProblemImpl(problemType, msg, start, end-start);
	}

	public static ReconcileProblemImpl missingProperty(String msg, IDocument doc, Node parent, Node map) {
		DocumentRegion underline = NodeUtil.region(doc, map);
		if (parent instanceof MappingNode) {
			for (NodeTuple prop : ((MappingNode) parent).getValue()) {
				if (prop.getValueNode() == map) {
					underline = NodeUtil.region(doc, prop.getKeyNode());
				}
			}
		} else if (parent instanceof SequenceNode) {
			boolean flowStyle = ((SequenceNode) parent).getFlowStyle() == DumperOptions.FlowStyle.FLOW;
			if (!flowStyle) {
				Mark nodeStart = map.getStartMark();
				underline = new DocumentRegion(doc, 0, nodeStart.getIndex());
				underline = underline.trimEnd();
				if (underline.endsWith("-")) {
					underline = underline.subSequence(underline.length()-1, underline.length());
				}
			}
		} else {
			underline = underline.trimEnd().textAtEnd(1);
		}
		return problem(MISSING_PROPERTY, msg, underline);
	}

	public static ReconcileProblem missingProperties(String msg, DynamicSchemaContext dc, Set<String> missingProps, String snippet, int cursorOffset, Node parent, MappingNode map, QuickfixType quickfixType) {
		YamlPath contextPath = dc.getPath();
		List<String> segments = Stream.of(contextPath.getSegments())
				.map(YamlPathSegment::encode)
				.collect(Collectors.toList());

		String fixTitle = missingProps.size() == 1
				? "Add property '"+CollectionUtil.getAny(missingProps)+"'"
				: "Add properties: "+missingProps;
		QuickfixData<MissingPropertiesData> fix = new QuickfixData<MissingPropertiesData>(
				quickfixType,
				new MissingPropertiesData(
						dc.getDocument().getUri(),
						segments,
						ImmutableList.copyOf(missingProps),
						snippet,
						cursorOffset
				),
				fixTitle
		);

		return missingProperty(msg, dc.getDocument(), parent, map)
				.addQuickfix(fix);
	}

}
