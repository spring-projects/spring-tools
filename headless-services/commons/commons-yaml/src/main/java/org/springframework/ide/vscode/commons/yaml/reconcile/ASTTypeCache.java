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
package org.springframework.ide.vscode.commons.yaml.reconcile;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;

/**
 * An implementation of {@link ITypeCollector} which keeps track of the
 * types of 'interesting' nodes in the ast.
 *
 * @author Kris De Volder
 */
public class ASTTypeCache implements ITypeCollector {
	
	private static Logger log = LoggerFactory.getLogger(ASTTypeCache.class);

	public interface NodeTypes {
		Collection<Node> getNodes(YType type);
		Map<Node, YType> getTypes();
	}

	/**
	 * Wraps around a {@link ImmutableMap}<Node, Type> and lazy builds the inverse
	 * map as needed.
	 */
	private static class NodeTypesImpl implements NodeTypes {

		private final ImmutableMap<Node, YType> node2type;
		private Multimap<YType, Node> type2node = null; //lazy initialized when used.

		public NodeTypesImpl(YamlFileAST ast, ImmutableMap<Node, YType> node2type) {
			this.node2type = node2type;
		}

		@Override
		public synchronized Collection<Node> getNodes(YType type) {
			if (type2node == null) {
				ImmutableMultimap.Builder<YType, Node> builder = ImmutableMultimap.builder();
				for (Entry<Node, YType> e : node2type.entrySet()) {
					builder.put(e.getValue(), e.getKey());
				}
				type2node = builder.build();
			}
			return type2node.get(type);
		}

		@Override
		public Map<Node, YType> getTypes() {
			return node2type;
		}
	}

	/**
	 * Set upon commencing a reconciler session.
	 */
	private YamlFileAST currentAst = null;

	public ASTTypeCache() {}

	/**
	 * Collects types for the current session.
	 */
	private Map<Node, Pair<YType, YamlPath>> currentTypes = null;

	private final Set<YType> interestingTypes = new HashSet<>();
	private final Map<String, NodeTypes> typeIndex = new HashMap<>();

	@Override
	public void beginCollecting(YamlFileAST ast) {
		Assert.isNull("A session is already active. Concurrency isn't supported by ITypeCollector protocol", currentTypes);
		this.currentAst = ast;
		this.currentTypes = new HashMap<>();
	}

	@Override
	public synchronized void endCollecting(YamlFileAST ast) {
		Assert.isLegal(currentAst == ast);
		String uri = ast.getDocument().getUri();
		ImmutableMap.Builder<Node, YType> nodeTypes = ImmutableMap.builder();
		for (Entry<Node, Pair<YType, YamlPath>> entry : currentTypes.entrySet()) {
			nodeTypes.put(entry.getKey(), entry.getValue().getLeft());
		}
		typeIndex.put(uri, new NodeTypesImpl(currentAst, nodeTypes.build()));
		this.currentAst = null;
		this.currentTypes = null;
	}

	@Override
	public void accept(Node node, YType type, YamlPath path) {
		if (interestingTypes.contains(type)) {
			Pair<YType, YamlPath> existing = currentTypes.get(node);
			if (existing != null) {
				//A second time assinging type to the same node. This is possible when anchors / references
				// are used (this makes parts of the tree 'shared').
				YType oldType = existing.getLeft();
				if (oldType.equals(type)) {
					//If the types are the same nothing is lost by dropping the extra assignment.
				} else {
					//different types trying to be assigned to same node. 
					//This is possible in theory when 'anchors and references' are in use.
					//We hope this doesn't happen in practice. We'll log it in case it does.
					log.warn("Ignore assignment of type for: {}", path);
					log.warn("          Already assigned at: {}", existing.getRight());
					log.warn("               Previous type : {}", existing.getLeft());
					log.warn("                    New type : {}", type);
				}
			} else {
				currentTypes.put(node, Pair.of(type, path));
			}
		}
	}

	public synchronized YType getType(YamlFileAST ast, Node node) {
		NodeTypes types = typeIndex.get(ast.getDocument().getUri());
		if (types != null) {
			return types.getTypes().get(node);
		}
		return null;
	}

	/**
	 * Declares a given YType as 'interesting'. This means that nodes of this type will be
	 * added to the index.
	 */
	public void addInterestingType(YType type) {
		this.interestingTypes.add(type);
	}

	public synchronized NodeTypes getNodeTypes(String uri) {
		return typeIndex.get(uri);
	}

	/**
	 * Use this astTypeCache to extract all defined names for a given type of definition.
	 */
	public Collection<String> getDefinedNames(DynamicSchemaContext dc, YType defType) {
		IDocument doc = dc.getDocument();
		if (doc != null) {
			String uri = doc.getUri();
			if (uri != null) {
				NodeTypes typeMap =  getNodeTypes(uri);
				if (typeMap != null) {
					Collection<Node> nodes = typeMap.getNodes(defType);
					if (nodes != null) {
						ImmutableSet.Builder<String> builder = ImmutableSortedSet.naturalOrder();
						for (Node node : nodes) {
							String name = NodeUtil.asScalar(node);
							if (StringUtil.hasText(name)) {
								builder.add(name);
							}
						}
						return builder.build();
					}
				}
				return ImmutableList.of();
			}
		}
		return null;
	}

	public Collection<Node> getNodes(String uri, YType type) {
		NodeTypes nodeMap = getNodeTypes(uri);
		if (nodeMap != null) {
			Collection<Node> nodes = nodeMap.getNodes(type);
			if (nodes != null) {
				return nodes;
			}
		}
		return ImmutableList.of();
	}

}
