/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.schema;

import java.util.Set;

import org.eclipse.jface.text.IDocument;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlFileAST;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;

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

	public ASTDynamicSchemaContext(YamlFileAST ast, YamlPath path, Node node) {
		this.ast = ast;
		this.path = path;
		this.mapNode = as(MappingNode.class, node);
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
}
