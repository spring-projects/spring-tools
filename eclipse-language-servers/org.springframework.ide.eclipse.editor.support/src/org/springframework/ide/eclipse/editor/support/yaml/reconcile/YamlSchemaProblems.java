/*******************************************************************************
 * Copyright (c) 2016, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.reconcile;

import org.eclipse.jface.text.IDocument;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemSeverity;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemType;
import org.springframework.ide.eclipse.editor.support.reconcile.ReconcileProblem;
import org.springframework.ide.eclipse.editor.support.reconcile.ReconcileProblemImpl;
import org.springframework.ide.eclipse.editor.support.util.DocumentRegion;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypedProperty;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Methods for creating reconciler problems for Schema based reconciler implementation.
 *
 * @author Kris De Volder
 */
public class YamlSchemaProblems {

	public static final ProblemType SYNTAX_PROBLEM = problemType("YamlSyntaxProblem");
	public static final ProblemType SCHEMA_PROBLEM = problemType("YamlSchemaProblem");
	public static final ProblemType DEPRECATED_PROPERTY = problemType("DeprecatedProperty", ProblemSeverity.WARNING);
	public static final ProblemType DEPRECATED_VALUE = problemType("DeprecatedValue", ProblemSeverity.WARNING);
	public static final ProblemType MISSING_PROPERTY = problemType("MissingProperty", ProblemSeverity.ERROR);
	public static final ProblemType EXTRA_PROPERTY = problemType("ExtraProperty", ProblemSeverity.ERROR);

	public static ProblemType problemType(final String typeName, ProblemSeverity defaultSeverity) {
		return new ProblemType() {
			@Override
			public String getId() {
				return typeName;
			}
			@Override
			public String toString() {
				return getId();
			}
			@Override
			public ProblemSeverity getDefaultSeverity() {
				return defaultSeverity;
			}
			@Override
			public String getLabel() {
				//TODO: if we want a prefs page that allows controlling the severities of problems we need to implement this properly
				return getId();
			}
			@Override
			public String getDescription() {
				//TODO: if we want a prefs page that allows controlling the severities of problems we need to implement this properly
				return getId();
			}
		};
	}
	public static ProblemType problemType(final String typeName) {
		return problemType(typeName, ProblemSeverity.ERROR);
	}

	public static ReconcileProblem problem(ProblemType problemType, String msg, Node node) {
		int start = node.getStartMark().getIndex();
		int end = node.getEndMark().getIndex();
		return new ReconcileProblemImpl(problemType, msg, start, end-start);
	}

	public static ReconcileProblem syntaxProblem(String msg, int offset, int len) {
		return new ReconcileProblemImpl(SYNTAX_PROBLEM, msg, offset, len);
	}

	public static ReconcileProblem schemaProblem(String msg, Node node) {
		int start = node.getStartMark().getIndex();
		int end = node.getEndMark().getIndex();
		return new ReconcileProblemImpl(SCHEMA_PROBLEM, msg, start, end-start);
	}

	public static ReconcileProblemImpl missingProperty(String msg, IDocument doc, Node parent, MappingNode map) {
		DocumentRegion underline = NodeUtil.region(doc, map);

		if (parent instanceof MappingNode) {
			for (NodeTuple prop : ((MappingNode) parent).getValue()) {
				if (prop.getValueNode() == map) {
					underline = NodeUtil.region(doc, prop.getKeyNode());
				}
			}

		} else if (parent instanceof SequenceNode) {
			Object flowStyle = ((SequenceNode) parent).getFlowStyle();
			boolean flow = false;

			// check the type of the flow style, since that return type changed between snakeyaml versions
			// from Boolean to DumperOptions.FlowStyle, so this type check allows us to compile against and
			// work with both versions (1.14 and 1.27)
			if (flowStyle instanceof Boolean) {
				flow = ((Boolean)flowStyle).booleanValue();
			}
			else if (flowStyle instanceof DumperOptions.FlowStyle) {
				DumperOptions.FlowStyle style = (DumperOptions.FlowStyle) flowStyle;
				flow = style == DumperOptions.FlowStyle.FLOW;
			}

			if (!flow) {
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

	public static ReconcileProblemImpl problem(ProblemType problemType, String msg, DocumentRegion node) {
		int start = node.getStart();
		int end = node.getEnd();
		return new ReconcileProblemImpl(problemType, msg, start, end-start);
	}
	public static ReconcileProblem deprecatedProperty(String msg, Node node) {
		return problem(DEPRECATED_PROPERTY, msg, node);
	}

	public static ReconcileProblem deprecatedProperty(Node node, YType bean, YTypedProperty property) {
		return deprecatedProperty("Property '"+property.getName()+"' of '"+bean+"' is Deprecated", node);
	}

}
