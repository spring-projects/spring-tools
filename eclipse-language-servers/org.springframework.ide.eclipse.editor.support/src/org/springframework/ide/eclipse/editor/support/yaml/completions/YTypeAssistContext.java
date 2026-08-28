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
package org.springframework.ide.eclipse.editor.support.yaml.completions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.completions.DocumentEdits;
import org.springframework.ide.eclipse.editor.support.hover.HoverInfo;
import org.springframework.ide.eclipse.editor.support.hover.YPropertyHoverInfo;
import org.springframework.ide.eclipse.editor.support.util.CollectionUtil;
import org.springframework.ide.eclipse.editor.support.util.FuzzyMatcher;
import org.springframework.ide.eclipse.editor.support.util.YamlIndentUtil;
import org.springframework.ide.eclipse.editor.support.yaml.YamlDocument;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypedProperty;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YValueHint;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNode;

public class YTypeAssistContext extends AbstractYamlAssistContext {

	final private YTypeUtil typeUtil;
	final private YType type;
	final private YamlAssistContext parent;

	public YTypeAssistContext(YTypeAssistContext parent, YamlPath contextPath, YType YType, YTypeUtil typeUtil) {
		super(parent.documentSelector, contextPath);
		this.parent = parent;
		this.type = YType;
		this.typeUtil = typeUtil;
	}

	public YTypeAssistContext(TopLevelAssistContext parent, int documentSelector, YType type, YTypeUtil typeUtil) {
		super(documentSelector, YamlPath.EMPTY);
		this.type = type;
		this.typeUtil = typeUtil;
		this.parent = parent;
	}

	@Override
	public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
		String query = getPrefix(doc, node, offset);
		List<ICompletionProposal> valueCompletions = getValueCompletions(doc, offset, query);
		if (!valueCompletions.isEmpty()) {
			return valueCompletions;
		}
		return getKeyCompletions(doc, offset, query);
	}

	public List<ICompletionProposal> getKeyCompletions(YamlDocument doc, int offset, String query) throws Exception {
		int queryOffset = offset - query.length();
		List<YTypedProperty> properties = typeUtil.getProperties(type);
		if (CollectionUtil.hasElements(properties)) {
			ArrayList<ICompletionProposal> proposals = new ArrayList<>(properties.size());
			SNode contextNode = getContextNode(doc);
			Set<String> definedProps = getDefinedProperties(contextNode);
			for (YTypedProperty p : properties) {
				String name = p.getName();
				double score = FuzzyMatcher.matchScore(query, name);
				if (score != 0) {
					YamlPath relativePath = YamlPath.fromSimpleProperty(name);
					YamlPathEdits edits = new YamlPathEdits(doc);
					if (!definedProps.contains(name)) {
						//property not yet defined
						YType YType = p.getType();
						edits.delete(queryOffset, query);
						edits.createPathInPlace(contextNode, relativePath, queryOffset, appendTextFor(YType));
						proposals.add(completionFactory().beanProperty(doc.getDocument(),
								contextPath.toPropString(), getType(),
								query, p, score, edits, typeUtil)
						);
					} else {
						//property already defined
						// instead of filtering, navigate to the place where its defined.
						deleteQueryAndLine(doc, query, queryOffset, edits);
						//Cast to SChildBearingNode cannot fail because otherwise definedProps would be the empty set.
						edits.createPath((SChildBearingNode) contextNode, relativePath, "");
						proposals.add(
							completionFactory().beanProperty(doc.getDocument(),
								contextPath.toPropString(), getType(),
								query, p, score, edits, typeUtil)
							.deemphasize() //deemphasize because it already exists
						);
					}
				}
			}
			return proposals;
		}
		return Collections.emptyList();
	}

	/**
	 * Computes the text that should be appended at the end of a completion
	 * proposal depending on what type of value is expected.
	 */
	protected String appendTextFor(YType type) {
		//Note that proper indentation after each \n" is added automatically
		//to align with the parent. The strings created here only need to contain
		//indentation spaces to indent *more* than the parent node.
		if (type == null) {
			//Assume its some kind of pojo bean
			return "\n"+YamlIndentUtil.INDENT_STR;
		} else if (typeUtil.isMap(type)) {
			//ready to enter nested map key on next line
			return "\n"+YamlIndentUtil.INDENT_STR;
		} if (typeUtil.isSequencable(type)) {
			//ready to enter sequence element on next line
			return "\n- ";
		} else if (typeUtil.isAtomic(type)) {
			//ready to enter whatever on the same line
			return " ";
		} else {
			//Assume its some kind of pojo bean
			return "\n"+YamlIndentUtil.INDENT_STR;
		}
	}

	private Set<String> getDefinedProperties(SNode contextNode) {
		try {
			if (contextNode instanceof SChildBearingNode) {
				List<SNode> children = ((SChildBearingNode)contextNode).getChildren();
				if (CollectionUtil.hasElements(children)) {
					Set<String> keys = new HashSet<>(children.size());
					for (SNode c : children) {
						if (c instanceof SKeyNode) {
							keys.add(((SKeyNode) c).getKey());
						}
					}
					return keys;
				}
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return Collections.emptySet();
	}

	private List<ICompletionProposal> getValueCompletions(YamlDocument doc, int offset, String query) {
		YValueHint[] values = typeUtil.getHintValues(type);
		if (values != null) {
			ArrayList<ICompletionProposal> completions = new ArrayList<>();
			for (YValueHint value : values) {
				double score = FuzzyMatcher.matchScore(query, value.getValue());
				if (score != 0 && !value.equals(query)) {
					DocumentEdits edits = new DocumentEdits(doc.getDocument());
					edits.delete(offset-query.length(), offset);
					edits.insert(offset, value.getValue());
					completions.add(completionFactory().valueProposal(value.getValue(), query, value.getLabel(), type, score, edits, null));
				}
			}
			return completions;
		}
		return Collections.emptyList();
	}

	@Override
	public YamlAssistContext traverse(YamlPathSegment s) {
		if (s.getType() == YamlPathSegmentType.VAL_AT_KEY) {
			if (typeUtil.isSequencable(type) || typeUtil.isMap(type)) {
				return contextWith(s, typeUtil.getDomainType(type));
			}
			String key = s.toPropString();
			Map<String, YTypedProperty> subproperties = typeUtil.getPropertiesMap(type);
			if (subproperties != null) {
				return contextWith(s, getType(subproperties.get(key)));
			}
		} else if (s.getType() == YamlPathSegmentType.VAL_AT_INDEX) {
			if (typeUtil.isSequencable(type)) {
				return contextWith(s, typeUtil.getDomainType(type));
			}
		}
		return null;
	}

	private YType getType(YTypedProperty prop) {
		if (prop != null) {
			return prop.getType();
		}
		return null;
	}

	private YamlAssistContext contextWith(YamlPathSegment s, YType nextType) {
		if (nextType != null) {
			return new YTypeAssistContext(this, contextPath.append(s), nextType, typeUtil);
		}
		return null;
	}


	@Override
	public String toString() {
		return "TypeContext("+contextPath.toPropString()+"::"+type+")";
	}


	@Override
	public HoverInfo getHoverInfo() {
		if (parent != null) {
			return parent.getHoverInfo(contextPath.getLastSegment());
		}
		return null;
	}

	public YType getType() {
		return type;
	}

	@Override
	public HoverInfo getHoverInfo(YamlPathSegment lastSegment) {
		//Hoverinfo is only attached to YTypedProperties so...
		switch (lastSegment.getType()) {
		case VAL_AT_KEY:
		case KEY_AT_KEY:
			YTypedProperty prop = getProperty(lastSegment.toPropString());
			if (prop != null) {
				return new YPropertyHoverInfo(contextPath.toPropString(), getType(), prop);
			}
			break;
		default:
		}
		return null;
	}

	private YTypedProperty getProperty(String name) {
		return typeUtil.getPropertiesMap(getType()).get(name);
	}
}
