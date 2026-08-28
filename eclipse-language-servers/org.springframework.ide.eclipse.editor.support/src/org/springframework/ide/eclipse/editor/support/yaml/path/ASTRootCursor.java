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
package org.springframework.ide.eclipse.editor.support.yaml.path;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlFileAST;
import org.yaml.snakeyaml.nodes.Node;

public class ASTRootCursor extends ASTCursor {

	private final YamlFileAST currentNode;

	public ASTRootCursor(YamlFileAST astRoot) {
		Assert.isNotNull(astRoot);
		this.currentNode = astRoot;
	}

	@Override
	public YamlFileAST getNode() {
		return currentNode;
	}

	@Override
	public Stream<ASTCursor> traverseAmbiguously(YamlPathSegment s) {
		switch (s.getType()) {
		case KEY_AT_KEY: {
			return Stream.empty();
		}
		case ANY_CHILD: {
			return getNode().getNodes().stream().map(NodeCursor::new);
		}
		case VAL_AT_INDEX: {
			List<Node> nodes = getNode().getNodes();
			int index = s.toIndex();
			int size = nodes.size();
			if (index < size && index >= 0) {
				return Stream.of(new NodeCursor(nodes.get(index)));
			}
			return Stream.empty();
		}
		case VAL_AT_KEY: {
			return Stream.empty();
		}
		default:
			Assert.isLegal(false, "Bug? Missing switch case?");
			return Stream.empty();
		}
	}

}
