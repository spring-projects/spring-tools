/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.schema;

import java.util.Set;

import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Adapts a SnakeYaml ast node as a {@link DynamicSchemaContext} (so it
 * can be used in YamlSchema based reconciler.
 *
 * @author Kris De Volder
 */
public class ASTDynamicSchemaContext extends CachingSchemaContext {

	private MappingNode mapNode;
	private YamlPath path;
	private YamlFileAST ast;
	private Node node;

	public ASTDynamicSchemaContext(YamlFileAST ast, YamlPath path, Node node) {
		this.ast = ast;
		this.path = path;
		this.mapNode = as(MappingNode.class, node);
		this.node = node;
	}

	@SuppressWarnings("unchecked")
	private <T> T as(Class<T> klass, Node node) {
		if (node != null && klass.isInstance(node)) {
			return (T) node;
		}
		return null;
	}

	@Override
	protected Set<String> computeDefinedProperties() {
		return NodeUtil.getScalarKeys(mapNode);
	}

	@Override
	public IDocument getDocument() {
		return ast.getDocument();
	}

	@Override
	public YamlPath getPath() {
		return path;
	}

	@Override
	public YamlFileAST getAST() {
		return ast;
	}

	@Override
	public boolean isAtomic() {
		return node instanceof ScalarNode;
	}

	@Override
	public boolean isMap() {
		return mapNode != null;
	}
	@Override
	public boolean isSequence() {
		return node instanceof SequenceNode;
	}
}
