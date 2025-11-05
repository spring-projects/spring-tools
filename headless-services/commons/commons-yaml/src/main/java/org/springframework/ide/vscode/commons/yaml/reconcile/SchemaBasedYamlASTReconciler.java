/*******************************************************************************
 * Copyright (c) 2016-2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.reconcile;

import static org.springframework.ide.vscode.commons.util.ExceptionUtil.getSimpleError;
import static org.springframework.ide.vscode.commons.yaml.ast.NodeUtil.asScalar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemTypeProvider;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileException;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReplacementQuickfix;
import org.springframework.ide.vscode.commons.languageserver.util.SnippetBuilder;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.IntegerRange;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeMergeSupport;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.quickfix.YamlQuickfixes;
import org.springframework.ide.vscode.commons.yaml.schema.ASTDynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.SchemaContextAware;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.schema.YamlSchema;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraint;
import org.springframework.ide.vscode.commons.yaml.snippet.SchemaBasedSnippetGenerator;
import org.springframework.ide.vscode.commons.yaml.snippet.Snippet;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class SchemaBasedYamlASTReconciler implements YamlASTReconciler {
	
	private static final Logger log = LoggerFactory.getLogger(SchemaBasedYamlASTReconciler.class);

	private final IProblemCollector problems;
	private final YamlSchema schema;
	private final YTypeUtil typeUtil;
	private final ITypeCollector typeCollector;
	private final YamlQuickfixes quickfixes;
	private final NodeMergeSupport nodeMerger;

	private List<Runnable> delayedConstraints = new ArrayList<>();
		// keeps track of dynamic constraints discovered during reconciler walk
		// the constraints are validated at the end of the walk rather than during the walk.
		// This facilitates constraints that depend on, for example, the contents of the ast type cache being
		// populated prior to checking.

	private List<Runnable> slowDelayedConstraints = new ArrayList<>();

	public SchemaBasedYamlASTReconciler(IProblemCollector problems, YamlSchema schema, ITypeCollector typeCollector, YamlQuickfixes quickfixes) {
		this.problems = problems;
		this.schema = schema;
		this.typeCollector = typeCollector;
		this.typeUtil = schema.getTypeUtil();
		this.quickfixes = quickfixes;
		this.nodeMerger = new NodeMergeSupport(problems);
	}

	@Override
	public void reconcile(YamlFileAST ast) {
		if (typeCollector!=null) typeCollector.beginCollecting(ast);
		delayedConstraints.clear();
		slowDelayedConstraints.clear();
		try {
			List<Node> nodes = ast.getNodes();
			boolean emptyDoc = isEmptyDoc(ast);
			IntegerRange expectedDocs = schema.expectedNumberOfDocuments();
			int numberOfDocs = emptyDoc ? 0 : nodes.size();
			if (!expectedDocs.isInRange(numberOfDocs)) {
				//wrong number of documents in the file. Figure out a good error message.
				if (emptyDoc) {
					problem(allOf(ast.getDocument()), "'"+schema.getName()+"' must have at least some Yaml content");
				} else if (expectedDocs.isTooLarge(numberOfDocs)) {
					int upperBound = expectedDocs.getUpperBound();
					Node extraNode = nodes.get(upperBound);
					problem(dashesAtStartOf(ast, extraNode), "'"+schema.getName()+"' should not have more than "+upperBound+" Yaml Documents");
				} else if (expectedDocs.isTooSmall(numberOfDocs)) {
					int lowerBound = expectedDocs.getLowerBound();
					problem(endOf(ast.getDocument()), "'"+schema.getName()+"' should have at least "+lowerBound+" Yaml Documents");
				}
			}
			if (!emptyDoc && nodes!=null && !nodes.isEmpty()) {
				for (int i = 0; i < nodes.size(); i++) {
					Node node = nodes.get(i);
					reconcile(ast, new YamlPath(YamlPathSegment.valueAt(i)), /*parent*/null, node, schema.getTopLevelType());
				}
			}
		} finally {
			if (typeCollector!=null) {
				typeCollector.endCollecting(ast);
			}
			verifyDelayedConstraints();
		}
	}
	
	private static boolean isEmptyDoc(YamlFileAST ast) {
		List<Node> nodes = ast.getNodes();
		if (nodes != null && !nodes.isEmpty()) {
			if (nodes.size() == 1) {
				Node n = nodes.get(0);
				if (n instanceof MappingNode) {
					MappingNode mn = (MappingNode) n;
					return mn.getValue().isEmpty();
				}
			}
			return false;
		}
		return true;
	}

	private DocumentRegion dashesAtStartOf(YamlFileAST ast, Node node) {
		try {
			int start = node.getStartMark().getIndex();
			DocumentRegion textBefore = new DocumentRegion(ast.getDocument(), 0, start)
					.trimEnd(Pattern.compile("\\s*"));
			DocumentRegion dashes = textBefore.subSequence(textBefore.getLength()-3);
			if (dashes.toString().equals("---")) {
				return dashes;
			}
		} catch (Exception e) {
			log.error("", e);
		}
		//something unexpected... we couldn't find the '---'. So just mark the entire node.
		return allOf(ast, node);
	}

	private void reconcile(YamlFileAST ast, YamlPath path, Node parent, Node node, YType _type) {
		nodeMerger.flattenMapping(node);

		if (_type!=null && !skipReconciling(node)) {
			DynamicSchemaContext schemaContext = new ASTDynamicSchemaContext(ast, path, node);
			YType type = typeUtil.inferMoreSpecificType(_type, schemaContext);
			if (typeCollector!=null) {
				typeCollector.accept(node, type, path);
			}
			checkConstraints(parent, node, type, schemaContext);
			switch (getNodeId(node)) {
			case mapping:
				MappingNode map = (MappingNode) node;
				checkForDuplicateKeys(map);
				if (typeUtil.isMap(type)) {
					for (NodeTuple entry : map.getValue()) {
						String key = NodeUtil.asScalar(entry.getKeyNode());
						reconcile(ast, keyAt(path, key), map, entry.getKeyNode(), typeUtil.getKeyType(type));
						reconcile(ast, valueAt(path, key), map, entry.getValueNode(), typeUtil.getDomainType(type));
					}
				} else if (typeUtil.isBean(type)) {
					Map<String, YTypedProperty> beanProperties = typeUtil.getPropertiesMap(type);
					checkRequiredProperties(parent, map, type, beanProperties, schemaContext);
					for (NodeTuple entry : map.getValue()) {
						Node keyNode = entry.getKeyNode();
						String key = NodeUtil.asScalar(keyNode);
						if (key==null) {
							expectScalar(node);
						} else {
							YTypedProperty prop = beanProperties.get(key);
							if (prop==null) {
								if (!NodeUtil.isAnchored(entry)) {
									unknownBeanProperty(keyNode, type, key);
								}
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
					SchemaContextAware<ValueParser> parserProvider = typeUtil.getValueParser(type);
					if (parserProvider!=null) {
						//Take care not to execute parserProvider early just to check how long it should be delayed.
						delayedConstraints.add(() -> {
							parserProvider.safeWithContext(schemaContext).ifPresent(parser -> {
								if (parser.longRunning()) {
									slowDelayedConstraints.add(() -> {
										parse(ast, node, type, parser);
									});
								} else {
									parse(ast, node, type, parser);
								}
							});
						});
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

	private void parse(YamlFileAST ast, Node node, YType type, ValueParser parser) {
		try {
			String value = NodeUtil.asScalar(node);
			if (value!=null) {
				parser.parse(value);
			}
		} catch (Exception e) {
			// check for YTT
			if (!isYttUsed(node)) {
				ProblemType problemType = getProblemType(e);
				DocumentRegion region = getRegion(e, ast.getDocument(), node);
				String msg = getMessage(e);
				valueParseError(type, region, msg, problemType, getValueReplacement(e));
			}
		}
	}
	
	private static boolean isYttUsed(Node node) {
		if (node.getBlockComments() != null 
				&& node.getBlockComments().stream().anyMatch(cl -> isYttComment(cl.getValue()))) {
			return true;
		}
		if (node.getInLineComments() != null
				&& node.getInLineComments().stream().anyMatch(cl -> isYttComment(cl.getValue()))) {
			return true;
		}
		return false;
	}
	
	private static boolean isYttComment(String s) {
		return s.startsWith("@ ");
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

	private void checkRequiredProperties(Node parent, MappingNode map, YType type, Map<String, YTypedProperty> beanProperties, DynamicSchemaContext dc) {
		Set<String> foundProps = NodeUtil.getScalarKeys(map);
		boolean allPropertiesKnown = beanProperties.keySet().containsAll(foundProps);
		//Don't check for missing properties if some properties look like they might be spelled incorrectly.
		if (allPropertiesKnown) {
			//Check for missing required properties:
			List<YTypedProperty> missingProps = beanProperties.values().stream()
					.filter(YTypedProperty::isRequired)
					.filter(prop -> !foundProps.contains(prop.getName()))
					.collect(CollectorUtil.toImmutableList());
			Set<String> missingPropNames = missingProps.stream()
					.map(YTypedProperty::getName)
					.collect(Collectors.toCollection(TreeSet::new));
			if (!missingPropNames.isEmpty()) {
				String message;
				if (missingPropNames.size()==1) {
					// slightly more specific message when only one missing property
					String missing = missingPropNames.stream().findFirst().get();
					message = "Property '"+missing+"' is required for '"+type+"'";
				} else {
					message = "Properties "+missingPropNames+" are required for '"+type+"'";
				}
				SchemaBasedSnippetGenerator snippetProvider = new SchemaBasedSnippetGenerator(typeUtil, SnippetBuilder::gimped);
				Snippet snippet = snippetProvider.getSnippet(missingProps);
				problems.accept(YamlSchemaProblems.missingProperties(message, dc, missingPropNames, snippet.getSnippet(), snippet.getPlaceHolder(1).getOffset(), parent, map, quickfixes.MISSING_PROP_FIX));
			}
		}
	}

	protected void checkConstraints(Node parent, Node node, YType type, DynamicSchemaContext dc) {
		//Check for other constraints attached to the type
		for (Constraint constraint : typeUtil.getConstraints(type)) {
			if (constraint!=null) {
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

		// First report the "faster" delayed constraints
		problems.checkPointCollecting();

		delayedConstraints.clear();

		for (Runnable runnable : slowDelayedConstraints) {
			runnable.run();
		}
		slowDelayedConstraints.clear();
	}

	protected NodeId getNodeId(Node node) {
		NodeId id = node.getNodeId();
//		if (id==NodeId.mapping && isMoustacheVar(node)) {
//			return NodeId.scalar;
//		}
		return id;
	}

	/**
	 * 'Moustache' variables look like `{{name}}` and unfortuately when
	 * parsed these will parse as a kind of weird map node.
	 * <p>
	 * This function recognizes a mapping node that actually is moustache var pattern.
	 */
	private boolean isMoustacheVar(Node node) {
		return NodeUtil.asScalar(debrace(debrace(node))) != null;
	}

	private boolean isParensPlaceHolder(Node node) {
		String scalar = NodeUtil.asScalar(node);
		return scalar != null && scalar.startsWith("((") && scalar.endsWith("))");
	}

	protected boolean skipReconciling(Node node) {
		return isMoustacheVar(node) || isParensPlaceHolder(node);
	}


	private Node debrace(Node _node) {
		MappingNode node = NodeUtil.asMapping(_node);
		if (node!=null && node.getFlowStyle()==FlowStyle.FLOW && node.getValue().size()==1) {
			NodeTuple entry = node.getValue().get(0);
			if ("".equals(NodeUtil.asScalar(entry.getValueNode()))) {
				return entry.getKeyNode();
			}
		}
		return null;
	}

	private YamlPath keyAt(YamlPath path, String key) {
		if (path!=null && key!=null) {
			return path.append(YamlPathSegment.keyAt(key));
		}
		return null;
	}

	private YamlPath valueAt(YamlPath path, int index) {
		if (path!=null) {
			return path.append(YamlPathSegment.valueAt(index));
		}
		return null;
	}

	private YamlPath valueAt(YamlPath path, String key) {
		if (path!=null && key!=null) {
			return path.append(YamlPathSegment.valueAt(key));
		}
		return null;
	}

	private void checkForDuplicateKeys(MappingNode node) {
		Set<String> duplicateKeys = new HashSet<>();
		Set<String> seenKeys = new HashSet<>();
		for (NodeTuple entry : node.getValue()) {
			String key = asScalar(entry.getKeyNode());
			if (key!=null) {
				if (!seenKeys.add(key)) {
					duplicateKeys.add(key);
				}
			}
		}
		if (!duplicateKeys.isEmpty()) {
			for (NodeTuple entry : node.getValue()) {
				Node keyNode = entry.getKeyNode();
				String key = asScalar(keyNode);
				if (key!=null && duplicateKeys.contains(key)) {
					problem(keyNode, "Duplicate key '"+key+"'");
				}
			}
		}
	}

	private void valueParseError(YType type, DocumentRegion region, String parseErrorMsg, ProblemType problemType, ReplacementQuickfix fix) {
		if (!StringUtil.hasText(parseErrorMsg)) {
			parseErrorMsg= "Couldn't parse as '"+describe(type)+"'";
		}
		ReconcileProblemImpl problem = YamlSchemaProblems.problem(problemType, parseErrorMsg, region);
		if (fix!=null && StringUtil.hasText(fix.replacement)) {
			try {
				problem.addQuickfix(
					new QuickfixData<>(quickfixes.SIMPLE_TEXT_EDIT,
						new ReplaceStringData(region, fix.replacement),
						fix.msg
					)
				);
			} catch (Exception e) {
				log.error("", e);
			}
		}
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
		return StringUtil.collectionToDelimitedString(expectedNodeTypes, " or ");
	}

	private void problem(Node node, String msg) {
		problems.accept(YamlSchemaProblems.schemaProblem(msg, node));
	}

	private void problem(DocumentRegion region, String msg) {
		problems.accept(YamlSchemaProblems.schemaProblem(msg, region));
	}

	private DocumentRegion endOf(IDocument document) {
		return new DocumentRegion(document, document.getLength(), document.getLength());
	}

	private DocumentRegion allOf(IDocument doc) {
		return new DocumentRegion(doc, 0, doc.getLength());
	}
	private DocumentRegion allOf(YamlFileAST ast, Node node) {
		return new DocumentRegion(ast.getDocument(), node.getStartMark().getIndex(), node.getEndMark().getIndex());
	}


}
