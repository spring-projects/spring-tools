/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.reconcile;

import static org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil.getSimpleError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.springframework.ide.eclipse.editor.support.reconcile.DuplicateFilterProblemCollector;
import org.springframework.ide.eclipse.editor.support.reconcile.IProblemCollector;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemType;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemTypeProvider;
import org.springframework.ide.eclipse.editor.support.reconcile.ReconcileException;
import org.springframework.ide.eclipse.editor.support.reconcile.ReconcileProblemImpl;
import org.springframework.ide.eclipse.editor.support.reconcile.ReplacementQuickfix;
import org.springframework.ide.eclipse.editor.support.util.DocumentRegion;
import org.springframework.ide.eclipse.editor.support.util.ValueParseException;
import org.springframework.ide.eclipse.editor.support.util.ValueParser;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlFileAST;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.editor.support.yaml.schema.ASTDynamicSchemaContext;
import org.springframework.ide.eclipse.editor.support.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypedProperty;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YamlSchema;
import org.springframework.ide.eclipse.editor.support.yaml.schema.constraints.Constraint;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class SchemaBasedYamlASTReconciler implements YamlASTReconciler {

	private final IProblemCollector problems;
	private final YamlSchema schema;
	private final YTypeUtil typeUtil;

	private List<Runnable> delayedConstraints = new ArrayList<>();
	// keeps track of dynamic constraints discovered during reconciler walk
	// the constraints are validated at the end of the walk rather than during the walk.
	// This facilitates constraints that depend on, for example, the contents of the ast type cache being
	// populated prior to checking.

	public SchemaBasedYamlASTReconciler(IProblemCollector problems, YamlSchema schema) {
		this.problems = new DuplicateFilterProblemCollector(problems);
		this.schema = schema;
		this.typeUtil = schema.getTypeUtil();
	}

	@Override
	public void reconcile(YamlFileAST ast, IProgressMonitor mon) {
		List<Node> nodes = ast.getNodes();
		if (nodes != null && !nodes.isEmpty()) {
			mon.beginTask("Reconcile", nodes.size());
			try {
				if (nodes != null && !nodes.isEmpty()) {
					for (int i = 0; i < nodes.size(); i++) {
						Node node = nodes.get(i);
						reconcile(ast, new YamlPath(YamlPathSegment.valueAt(i)), /*parent*/null, node, schema.getTopLevelType());
						mon.worked(1);
					}
				}
			} finally {
				verifyDelayedConstraints();
				mon.done();
			}
		}
	}

	private void reconcile(YamlFileAST ast, YamlPath path, Node parent, Node node, YType type) {
		if (type != null) {
			DynamicSchemaContext schemaContext = new ASTDynamicSchemaContext(ast, path, node);
//			type = typeUtil.inferMoreSpecificType(type, schemaContext);
//			if (typeCollector != null) {
//				typeCollector.accept(node, type);
//			}
			checkConstraints(parent, node, type, schemaContext);
			switch (node.getNodeId()) {
			case mapping:
				MappingNode map = (MappingNode) node;
				if (typeUtil.isMap(type)) {
					for (NodeTuple entry : map.getValue()) {
						String key = NodeUtil.asScalar(entry.getKeyNode());
						reconcile(ast, keyAt(path, key), map, entry.getKeyNode(), typeUtil.getKeyType(type));
						reconcile(ast, valueAt(path, key), map, entry.getValueNode(), typeUtil.getDomainType(type));
					}
				} else if (typeUtil.isBean(type)) {
					Map<String, YTypedProperty> beanProperties = typeUtil.getPropertiesMap(type);
					for (NodeTuple entry : map.getValue()) {
						Node keyNode = entry.getKeyNode();
						String key = NodeUtil.asScalar(keyNode);
						if (key == null) {
							expectScalar(node);
						} else {
							YTypedProperty prop = beanProperties.get(key);
							if (prop == null) {
								unknownBeanProperty(keyNode, type, key);
							} else {
								if (prop.isDeprecated()) {
									String msg = prop.getDeprecationMessage();
									if (StringUtil.hasText(msg)) {
										problems.accept(YamlSchemaProblems.deprecatedProperty(msg, keyNode));
									} else {
										problems.accept(YamlSchemaProblems.deprecatedProperty(keyNode, type, prop));
									}
								}
								reconcile(ast, valueAt(path, key), map, entry.getValueNode(), prop.getType());
							}
						}
					}
				} else {
					expectTypeButFoundMap(type, node);
				}
				break;
			case sequence:
				SequenceNode seq = (SequenceNode) node;
				if (typeUtil.isSequencable(type)) {
					for (int i = 0; i < seq.getValue().size(); i++) {
						Node el = seq.getValue().get(i);
						reconcile(ast, valueAt(path, i), seq, el, typeUtil.getDomainType(type));
					}
				} else {
					expectTypeButFoundSequence(type, node);
				}
				break;
			case scalar:
				if (typeUtil.isAtomic(type)) {
					ValueParser parser = typeUtil.getValueParser(type);
					if (parser != null) {
						try {
							String value = NodeUtil.asScalar(node);
							if (value != null) {
								parser.parse(value);
							}
						} catch (Exception e) {
							ProblemType problemType = getProblemType(e);
							DocumentRegion region = getRegion(e, ast.getDocument(), node);
							String msg = getMessage(e);
							valueParseError(type, region, msg, problemType, getValueReplacement(e));
						}
					}
				} else {
					expectTypeButFoundScalar(type, node);
				}
				break;
			default:
				// other stuff we don't check
			}
		}
	}

	protected ReplacementQuickfix getValueReplacement(Exception _e) {
		if (_e instanceof ReconcileException) {
			ReconcileException e = (ReconcileException) _e;
			return e.getReplacement();
		}
		return null;
	}

	protected DocumentRegion getRegion(Exception e, IDocument doc, Node node) {
		DocumentRegion region = new DocumentRegion(doc, node.getStartMark().getIndex(), node.getEndMark().getIndex());
		if (e instanceof ValueParseException) {
			ValueParseException parseException = (ValueParseException) e;
			int start = parseException.getStartIndex() >= 0
					? Math.min(node.getStartMark().getIndex() + parseException.getStartIndex(),
							node.getEndMark().getIndex())
					: node.getStartMark().getIndex();
			int end = parseException.getEndIndex() >= 0
					? Math.min(node.getStartMark().getIndex() + parseException.getEndIndex(),
							node.getEndMark().getIndex())
					: node.getEndMark().getIndex();
			region = new DocumentRegion(doc, start, end);
		}
		return region;
	}

	private String getMessage(Exception _e) {
		Throwable e = ExceptionUtil.getDeepestCause(_e);

		// If value parse exception, do not append any additional information
		if (e instanceof ValueParseException) {
			String msg = e.getMessage();
			if (StringUtil.hasText(msg)) {
				return msg;
			} else {
				return "An error occurred: " + getSimpleError(e);
			}
		} else {
			return ExceptionUtil.getMessage(e);
		}
	}

	protected ProblemType getProblemType(Exception _e) {
		Throwable e = ExceptionUtil.getDeepestCause(_e);
		return e instanceof ProblemTypeProvider ? ((ProblemTypeProvider) e).getProblemType() : YamlSchemaProblems.SCHEMA_PROBLEM;
	}


	protected void checkConstraints(Node parent, Node node, YType type, DynamicSchemaContext dc) {
		//Check for other constraints attached to the type
		for (Constraint constraint : typeUtil.getConstraints(type)) {
			if (constraint != null) {
				delayedConstraints.add(() -> {
					constraint.verify(dc, parent, node, type, problems);
				});
			}
		}
	}

	private void verifyDelayedConstraints() {
		for (Runnable runnable : delayedConstraints) {
			runnable.run();
		}
		delayedConstraints.clear();
	}

	private YamlPath keyAt(YamlPath path, String key) {
		if (path != null && key != null) {
			return path.append(YamlPathSegment.keyAt(key));
		}
		return null;
	}

	private YamlPath valueAt(YamlPath path, int index) {
		if (path != null) {
			return path.append(YamlPathSegment.valueAt(index));
		}
		return null;
	}

	private YamlPath valueAt(YamlPath path, String key) {
		if (path != null && key != null) {
			return path.append(YamlPathSegment.valueAt(key));
		}
		return null;
	}

	private void valueParseError(YType type, Node node, String parseErrorMsg) {
		String msg= "Couldn't parse as '"+describe(type)+"'";
		if (!StringUtils.isBlank(parseErrorMsg)) {
			msg += " ("+parseErrorMsg+")";
		}
		problem(node, msg);
	}

	private void valueParseError(YType type, DocumentRegion region, String parseErrorMsg, ProblemType problemType, ReplacementQuickfix fix) {
		if (!StringUtil.hasText(parseErrorMsg)) {
			parseErrorMsg= "Couldn't parse as '"+describe(type)+"'";
		}
		ReconcileProblemImpl problem = YamlSchemaProblems.problem(problemType, parseErrorMsg, region);
// TODO: backport quickfix support for deprecated replacement somehow.
//		if (fix != null && StringUtil.hasText(fix.replacement)) {
//			try {
//				problem.addQuickfix(
//					new QuickfixData<>(quickfixes.SIMPLE_TEXT_EDIT,
//						new ReplaceStringData(region, fix.replacement),
//						fix.msg
//					)
//				);
//			} catch (Exception e) {
//				Log.log(e);
//			}
//		}
		problems.accept(problem);
	}

	private void unknownBeanProperty(Node keyNode, YType type, String name) {
		problem(keyNode, "Unknown property '"+name+"' for type '"+typeUtil.niceTypeName(type)+"'");
	}

	private void expectScalar(Node node) {
		problem(node, "Expecting a 'Scalar' node but got "+describe(node));
	}

	private String describe(Node node) {
		switch (node.getNodeId()) {
		case scalar:
			return "'"+((ScalarNode)node).getValue()+"'";
		case mapping:
			return "a 'Mapping' node";
		case sequence:
			return "a 'Sequence' node";
		case anchor:
			return "a 'Anchor' node";
		default:
			throw new IllegalStateException("Missing switch case");
		}
	}

	private void expectTypeButFoundScalar(YType type, Node node) {
		problem(node, "Expecting a '"+describe(type)+"' but found a 'Scalar'");
	}

	private void expectTypeButFoundSequence(YType type, Node node) {
		problem(node, "Expecting a '"+describe(type)+"' but found a 'Sequence'");
	}

	private void expectTypeButFoundMap(YType type, Node node) {
		problem(node, "Expecting a '"+describe(type)+"' but found a 'Map'");
	}

	private String describe(YType type) {
		if (typeUtil.isAtomic(type)) {
			return typeUtil.niceTypeName(type);
		}
		ArrayList<String> expectedNodeTypes = new ArrayList<>();
		if (typeUtil.isBean(type) || typeUtil.isMap(type)) {
			expectedNodeTypes.add("Map");
		}
		if (typeUtil.isSequencable(type)) {
			expectedNodeTypes.add("Sequence");
		}
		return StringUtils.join(expectedNodeTypes, " or ");
	}

	private void problem(Node node, String msg) {
		problems.accept(YamlSchemaProblems.schemaProblem(msg, node));
	}

}
