/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.yaml.reconcile;

import static org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlProblemType.YAML_DEPRECATED_ERROR;
import static org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlProblemType.YAML_DEPRECATED_WARNING;
import static org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlProblemType.YAML_DUPLICATE_KEY;
import static org.springframework.ide.vscode.commons.yaml.ast.NodeUtil.asScalar;
import static org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST.getChildren;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation.Level;
import org.springframework.ide.vscode.boot.metadata.IndexNavigator;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.BeanPropertyNameMode;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.EnumCaseMode;
import org.springframework.ide.vscode.boot.metadata.types.TypedProperty;
import org.springframework.ide.vscode.boot.properties.quickfix.CommonQuickfixes;
import org.springframework.ide.vscode.boot.properties.quickfix.DeprecatedPropertyData;
import org.springframework.ide.vscode.boot.properties.quickfix.MissingPropertyData;
import org.springframework.ide.vscode.boot.yaml.quickfix.AppYamlQuickfixes;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeMergeSupport;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef.Kind;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef.TupleValueRef;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlASTReconciler;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class ApplicationYamlASTReconciler implements YamlASTReconciler {

	private final IProblemCollector problems;
	private final TypeUtil typeUtil;
	private final IndexNavigator nav;
	private final NodeMergeSupport nodeMerger;
	private AppYamlQuickfixes quickFixes;

	public ApplicationYamlASTReconciler(IProblemCollector problems, IndexNavigator nav, TypeUtil typeUtil, AppYamlQuickfixes quickFixes) {
		this.problems = problems;
		this.typeUtil = typeUtil;
		this.nav = nav;
		this.quickFixes = quickFixes;
		this.nodeMerger = new NodeMergeSupport(problems);
	}

	@Override
	public void reconcile(YamlFileAST ast) {
		reconcile(ast, nav);
	}

	protected void reconcile(YamlFileAST root, IndexNavigator nav) {
		List<Node> nodes = root.getNodes();
		if (nodes!=null && !nodes.isEmpty()) {
			for (Node node : nodes) {
				if (!"".equals(NodeUtil.asScalar(node))) {
					//^^^ ignore completely empty yaml (sub)documents. Checking them results in 
					//spurious errors/warnings.
					//See https://github.com/spring-projects/sts4/issues/711
					reconcile(root, node, nav);
				}
			}
		}
	}

	protected void reconcile(YamlFileAST root, Node node, IndexNavigator nav) {
		switch (node.getNodeId()) {
		case mapping:
			MappingNode map = (MappingNode) node;
			nodeMerger.flattenMapping(map);
			checkForDuplicateKeys(map);
			for (NodeTuple entry : map.getValue()) {
				reconcile(root, entry, nav);
			}
			break;
		case scalar:
			if (!isIgnoreScalarAssignmentTo(nav.getPrefix())) {
				expectMapping(node);
			}
			break;
		default:
			expectMapping(node);
			break;
		}
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
					problems.accept(problem(YAML_DUPLICATE_KEY, keyNode, "Duplicate key '"+key+"'"));
				}
			}
		}
	}

	protected boolean isIgnoreScalarAssignmentTo(String propName) {
		//See https://issuetracker.springsource.com/browse/STS-4144
		return propName!=null && propName.equals("spring.profiles");
	}

	private void reconcile(YamlFileAST root, NodeTuple entry, IndexNavigator nav) {
		Node keyNode = entry.getKeyNode();
		String _key = asScalar(keyNode);
		if (_key==null) {
			expectScalar(keyNode);
		} else {
			IndexNavigator subNav = null;
			PropertyInfo match = null;
			PropertyInfo extension = null;
			//Try different 'relaxed' variants for this key. Maybe user is using camelCase or snake case?
			for (String key : keyAliases(_key)) {
				IndexNavigator subNavAlias = nav.selectSubProperty(key);
				match = subNavAlias.getExactMatch();
				extension = subNavAlias.getExtensionCandidate();
				if (subNav==null) {
					//ensure subnav is not null, even if no real matches found.
					subNav = subNavAlias;
				}
				if (match!=null || extension!=null) {
					subNav = subNavAlias;
					break; //stop at first alias that gives a result.
				}
			}
			if (match!=null && extension!=null) {
				//This is an odd situation, the current prefix lands on a propery
				//but there are also other properties that have it as a prefix.
				if (asScalar(entry.getValueNode())!=null) {
					//Since the property value is a 'scalar' let's ignore the extension and just check the 'exact' match here (ignore 'extension').
					extension = null;
				} else {
					//Seems like there is more stuff to be matched in sub node, so ignore exact match.
					match = null;
					return;
				}
			}
			if (match!=null) {
				Type type = TypeParser.parse(match.getType());
				if (match.isDeprecated()) {
					deprecatedProperty(root.getDocument().getUri(), match, keyNode, quickFixes.DEPRECATED_PROPERTY);
				}
				reconcile(root, entry.getValueNode(), type);
			} else if (extension!=null) {
				//We don't really care about the extension only about the fact that it
				// exists and so it is meaningful to continue checking...
				Node valueNode = entry.getValueNode();
				reconcile(root, valueNode, subNav);
			} else {
				//both are null, this means there's no valid property with the current prefix
				//whether exact or extending it with further navigation
				if (!NodeUtil.isAnchored(entry)) { //See https://github.com/spring-projects/sts4/issues/420
					unkownProperty(root.getDocument().getUri(), keyNode, subNav.getPrefix(), entry, quickFixes.MISSING_PROPERTY);
				}
			}
		}
	}

	private Collection<String> keyAliases(String originalKey) {
		ImmutableSet.Builder<String> builder = ImmutableSet.builder();
		builder.add(originalKey);
		builder.add(StringUtil.camelCaseToHyphens(originalKey));
		builder.add(StringUtil.snakeCaseToHyphens(originalKey));
		return builder.build();
	}

	/**
	 * Reconcile a node given the type that we expect the node to be.
	 */
	private void reconcile(YamlFileAST root, Node node, Type type) {
		if (type!=null) {
			switch (node.getNodeId()) {
			case scalar:
				reconcile(root, (ScalarNode)node, type);
				break;
			case sequence:
				reconcile(root, (SequenceNode)node, type);
				break;
			case mapping:
				reconcile(root, (MappingNode)node, type);
				break;
			case anchor:
				//TODO: what should we do with anchor nodes
				break;
			default:
				throw new IllegalStateException("Missing switch case");
			}
		}
	}

	private void reconcile(YamlFileAST root, MappingNode mapping, Type type) {
		nodeMerger.flattenMapping(mapping);
		checkForDuplicateKeys(mapping);
		if (typeUtil.isAtomic(type)) {
			expectTypeFoundMapping(type, mapping);
		} else if (typeUtil.isMap(type) || typeUtil.isSequencable(type)) {
			if (typeUtil.isMap(type)) {
				checkForEscapableKeys(mapping);
			}
			Type keyType = typeUtil.getKeyType(type);
			Type valueType = TypeUtil.getDomainType(type);
			if (keyType!=null) {
				for (NodeTuple entry : mapping.getValue()) {
					reconcile(root, entry.getKeyNode(), keyType);
				}
			}
			if (valueType!=null) {
				for (NodeTuple entry : mapping.getValue()) {
					Node value = entry.getValueNode();
					Type nestedValueType = valueType;
					if (value.getNodeId()==NodeId.mapping) {
						//Some special cases to handle here!!
						//   See https://issuetracker.springsource.com/browse/STS-4254
						//   See https://issuetracker.springsource.com/browse/STS-4335
						if (TypeUtil.isObject(valueType)) {
							nestedValueType = type;
						} else if (TypeUtil.isString(keyType) && typeUtil.isAtomic(valueType)) {
							nestedValueType = type;
						}
					}
					reconcile(root, entry.getValueNode(), nestedValueType);
				}
			}
		} else {
			// Neither atomic, map or sequence-like => bean-like
			Map<String, TypedProperty> props = typeUtil.getPropertiesMap(type, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
			if (props!=null) {
				for (NodeTuple entry : mapping.getValue()) {
					Node keyNode = entry.getKeyNode();
					String key = NodeUtil.asScalar(keyNode);
					if (key==null) {
						expectBeanPropertyName(keyNode, type);
					} else {
						if (!props.containsKey(key)) {
							unknownBeanProperty(keyNode, type, key);
						} else {
							Node valNode = entry.getValueNode();
							TypedProperty typedProperty = props.get(key);
							if (typedProperty!=null) {
								if (typedProperty.isDeprecated()) {
									deprecatedProperty(root.getDocument().getUri(), type, typedProperty, keyNode, quickFixes.DEPRECATED_PROPERTY);
								}
								reconcile(root, valNode, typedProperty.getType());
							}
						}
					}
				}
			}
		}
	}

	private void checkForEscapableKeys(MappingNode mapping) {
		if (mapping!=null) {
			for (NodeTuple tup : mapping.getValue()) {
				Node keyNode = tup.getKeyNode();
				String key = NodeUtil.asScalar(keyNode);
				if (needsEscaping(key)) {
					problems.accept(problem(ApplicationYamlProblemType.YAML_SHOULD_ESCAPE, keyNode, 
							"This key is used in a map and contains special characters. It is recommended to escape it by surrounding it with '[]'"));
				}
			}
		}
	}

	public static boolean needsEscaping(String key) {
		if (key!=null) {
			if (key.startsWith("[")) {
				return false; // looks like its already escaped. So doesn't need more escaping.
			}
			for (int i = 0; i < key.length(); i++) {
				char c = key.charAt(i);
				if (c=='-'||Character.isAlphabetic(c)||Character.isDigit(c)) {
					//fine!
				} else {
					return true;
				}
			}
		}
		return false;
	}

	private void reconcile(YamlFileAST root, SequenceNode seq, Type type) {
		if (typeUtil.isAtomic(type)) {
			expectTypeFoundSequence(type, seq);
		} else if (typeUtil.isSequencable(type)) {
			Type domainType = TypeUtil.getDomainType(type);
			if (domainType!=null) {
				for (Node element : seq.getValue()) {
					reconcile(root, element, domainType);
				}
			}
		} else if (typeUtil.isObjectOrSequence(type)) {
			// skip reconciling further as it can be anything - no schema
		} else {
			expectTypeFoundSequence(type, seq);
		}
	}


	private void reconcile(YamlFileAST root, ScalarNode scalar, Type type) {
		String stringValue = scalar.getValue();
		if (!hasPlaceHolder(stringValue)) { //don't check anything with placeholder expressions in it
			ValueParser valueParser = typeUtil.getValueParser(type);
			if (valueParser!=null) {
				// Tag tag = scalar.getTag(); //use the tag? Actually, boot tolerates String values
				//  even if integeger etc are expected. It has its ways of parsing the String to the
				//  expected type
				try {
					valueParser.parse(stringValue);
				} catch (ValueParseException e) {
					valueParseError(root, scalar, e);
				} catch (Exception e) {
					//Couldn't parse
					valueTypeMismatch(type, scalar);
				}
			}
		}
	}

	private static final Pattern PLACE_HOLDER = Pattern.compile("(\\$\\{\\S+\\})|(\\@\\S+\\@)");

	private boolean hasPlaceHolder(String str) {
		return PLACE_HOLDER.matcher(str).find();
	}

	private void expectTypeFoundMapping(Type type, MappingNode node) {
		expectType(ApplicationYamlProblemType.YAML_EXPECT_TYPE_FOUND_MAPPING, type, node);
	}

	private void expectTypeFoundSequence(Type type, SequenceNode seq) {
		expectType(ApplicationYamlProblemType.YAML_EXPECT_TYPE_FOUND_SEQUENCE, type, seq);
	}

	private void valueTypeMismatch(Type type, ScalarNode scalar) {
		expectType(ApplicationYamlProblemType.YAML_VALUE_TYPE_MISMATCH, type, scalar);
	}

	private void valueParseError(YamlFileAST root, ScalarNode scalar, ValueParseException e) {
		IDocument doc = root.getDocument();
		DocumentRegion containingRegion = new DocumentRegion(doc, scalar.getStartMark().getIndex(), scalar.getEndMark().getIndex());
		problems.accept(problem(ApplicationYamlProblemType.YAML_VALUE_TYPE_MISMATCH, e.getHighlightRegion(containingRegion), ExceptionUtil.getMessage(e)));
	}

	private void unkownProperty(String docUri, Node node, String name, NodeTuple entry, QuickfixType... fixTypes) {
		SpringPropertyProblem p = problem(ApplicationYamlProblemType.YAML_UNKNOWN_PROPERTY, node, "Unknown property '"+name+"'");
		p.setPropertyName(extendForQuickfix(StringUtil.camelCaseToHyphens(name), entry.getValueNode()));

		for (QuickfixType fixType : fixTypes) {
			if (fixType != null) {
				switch (fixType.getId()) {
				case CommonQuickfixes.MISSING_PROPERTY_APP_QF_ID:
					for (String missingProp : getUnknownProperties(name, entry.getValueNode(), new ArrayList<>())) {
						p.addQuickfix(new QuickfixData<>(fixType,
								new MissingPropertyData(new TextDocumentIdentifier(docUri), missingProp),
								"Create metadata for `" + missingProp +"`"));
					}
					break;
				}
			}
		}

		problems.accept(p);
	}

	private List<String> getUnknownProperties(String name, Node valueNode, List<String> unknownProps) {
		if (valueNode instanceof MappingNode) {
			MappingNode map = (MappingNode) valueNode;
			for (NodeTuple entry : map.getValue()) {
				String key = NodeUtil.asScalar(entry.getKeyNode());
				if (key!=null) {
					key = StringUtil.camelCaseToHyphens(key);
					getUnknownProperties(name+"."+key, entry.getValueNode(), unknownProps);
				}
			}
		} else {
			unknownProps.add(name);
		}
		return unknownProps;
	}

	private String extendForQuickfix(String name, Node node) {
		if (node!=null) {
			TupleValueRef child = getFirstTupleValue(getChildren(node));
			if (child!=null) {
				String extra = NodeUtil.asScalar(child.getKey());
				if (extra!=null) {
					return extendForQuickfix(name + "." + StringUtil.camelCaseToHyphens(extra),
							child.get());
				}
			}
		}
		//couldn't extend name any further
		return name;
	}

	private TupleValueRef getFirstTupleValue(List<NodeRef<?>> children) {
		for (NodeRef<?> nodeRef : children) {
			if (nodeRef.getKind()==Kind.VAL) {
				return (TupleValueRef) nodeRef;
			}
		}
		return null;
	}

	private void expectScalar(Node node) {
		problems.accept(problem(ApplicationYamlProblemType.YAML_EXPECT_SCALAR, node, "Expecting a 'Scalar' node but got "+describe(node)));
	}

	protected void expectMapping(Node node) {
		problems.accept(problem(ApplicationYamlProblemType.YAML_EXPECT_MAPPING, node, "Expecting a 'Mapping' node but got "+describe(node)));
	}

	private void expectBeanPropertyName(Node keyNode, Type type) {
		problems.accept(problem(ApplicationYamlProblemType.YAML_EXPECT_BEAN_PROPERTY_NAME, keyNode, "Expecting a bean-property name for object of type '"+typeUtil.niceTypeName(type)+"' "
				+ "but got "+describe(keyNode)));
	}

	private void unknownBeanProperty(Node keyNode, Type type, String name) {
		problems.accept(problem(ApplicationYamlProblemType.YAML_INVALID_BEAN_PROPERTY, keyNode, "Unknown property '"+name+"' for type '"+typeUtil.niceTypeName(type)+"'"));
	}

	private void expectType(ApplicationYamlProblemType problemType, Type type, Node node) {
		problems.accept(problem(problemType, node, "Expecting a '"+typeUtil.niceTypeName(type)+"' but got "+describe(node)));
	}

	private void deprecatedProperty(String docUri, PropertyInfo property, Node keyNode, QuickfixType fixType) {
		SpringPropertyProblem problem = deprecatedPropertyProblem(docUri, property.getId(), null, keyNode,
				property.getDeprecationReplacement(), property.getDeprecationReason(), property.getDeprecationLevel(), fixType);
		problem.setMetadata(property);
		//problem.setProblemFixer(ReplaceDeprecatedYamlQuickfix.FIXER);
		problems.accept(problem);
	}

	private void deprecatedProperty(String docUri, Type contextType, TypedProperty property, Node keyNode, QuickfixType fixType) {
		SpringPropertyProblem problem = deprecatedPropertyProblem(docUri, property.getName(), typeUtil.niceTypeName(contextType),
				keyNode, property.getDeprecationReplacement(), property.getDeprecationReason(), property.getDeprecationLevel(), fixType);
		problems.accept(problem);
	}

	protected SpringPropertyProblem deprecatedPropertyProblem(String docUri, String name, String contextType, Node keyNode,
			String replace, String reason, Level level, QuickfixType fixType) {
		ApplicationYamlProblemType problemType = level==Level.ERROR ? YAML_DEPRECATED_ERROR : YAML_DEPRECATED_WARNING;
		SpringPropertyProblem problem = problem(problemType, keyNode, TypeUtil.deprecatedPropertyMessage(name, contextType, replace, reason));
		problem.setPropertyName(name);
		Range range = new Range(new Position(keyNode.getStartMark().getLine(), keyNode.getStartMark().getColumn()),
				new Position(keyNode.getEndMark().getLine(), keyNode.getEndMark().getColumn()));
		if (StringUtil.hasText(replace)) {
			problem.addQuickfix(new QuickfixData<>(fixType, new DeprecatedPropertyData(docUri, range, replace), "Replace with `" + replace + "`"));
		}
		return problem;
	}

	protected SpringPropertyProblem problem(ApplicationYamlProblemType type, Node node, String msg) {
		int start = node.getStartMark().getIndex();
		int end = node.getEndMark().getIndex();
		return SpringPropertyProblem.problem(type, msg, start, end-start);
	}

	private SpringPropertyProblem problem(ApplicationYamlProblemType type, DocumentRegion region, String msg) {
		return SpringPropertyProblem.problem(type, msg, region.getStart(), region.getLength());
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

}
