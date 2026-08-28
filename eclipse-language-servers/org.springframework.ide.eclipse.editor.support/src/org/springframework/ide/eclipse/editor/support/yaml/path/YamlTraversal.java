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

import java.util.stream.Stream;

import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlFileAST;
import org.yaml.snakeyaml.nodes.Node;

public interface YamlTraversal {

	////////////////////////////////////////////////
	/// Methods for performing traversals :

	/**
	 * This is the essence of a traversal. Any concrete traversal must provide
	 * some way to be applied to a starting point and return a stream of
	 * endpoints. If a traversal is non-ambiguous the stream contains at
	 * most one element.
	 * <p>
	 * If traversal is deterministic, the stream will contain at most one element.
	 * <p>
	 * If traversal has potential ambiguity then the stream may contain more than
	 * one element. Each of the elements in the stream is an alternate
	 * place that this traversal could end-up in.
	 */
	<T extends YamlNavigable<T>> Stream<T> traverseAmbiguously(T start);

	/**
	 * Performs a traversal and silently drops all but one of the endpoints.
	 * <p>
	 * This is a convenience method for unambiguous traversals, or for when the caller
	 * doesn't care about precisely which one of the possible alternate end-points
	 * they might get.
	 */
	<T extends YamlNavigable<T>> T traverse(T startNode);

	default Node traverseNode(Node root) {
		if (root != null) {
			ASTCursor cursor = traverse(new NodeCursor(root));
			if (cursor instanceof NodeCursor) {
				return ((NodeCursor)cursor).getNode();
			}
		}
		return null;
	}

	default Node traverseToNode(YamlFileAST root) {
		ASTCursor cursor = traverse(new ASTRootCursor(root));
		if (cursor instanceof NodeCursor) {
			return ((NodeCursor)cursor).getNode();
		}
		return null;
	}

	default Stream<Node> traverseAmbiguously(YamlFileAST ast) {
		if (ast != null) {
			return traverseAmbiguously(new ASTRootCursor(ast))
			.filter(cursor -> cursor.getNode() instanceof Node)
			.map((ASTCursor cursor) -> (Node)cursor.getNode());
		}
		return Stream.empty();
	}

	default Stream<Node> traverseAmbiguously(Node startNode) {
		if (startNode != null) {
			return traverseAmbiguously(new NodeCursor(startNode))
			.map((ASTCursor cursor) -> (Node)cursor.getNode());
		}
		return Stream.empty();
	}

	/////////////////////////////////////////////////
	// Creating/composing traversals

	default YamlTraversal then(YamlTraversal other) {
		if (this.isEmpty()) {
			return other;
		} else if (other.isEmpty()) {
			return this;
		}
		return new SequencingYamlTraversal(this, other);
	}

	default YamlTraversal thenValAt(int index) {
		return then(YamlPathSegment.valueAt(index));
	}
	default YamlTraversal thenValAt(String key) {
		return then(YamlPathSegment.valueAt(key));
	}
	default YamlTraversal thenKeyAt(String key) {
		return then(YamlPathSegment.keyAt(key));
	}
	default YamlTraversal thenAnyChild() {
		return then(YamlPathSegment.anyChild());
	}
	default YamlTraversal or(YamlTraversal other) {
		if (this.isEmpty()) {
			return other;
		} else if (other.isEmpty()) {
			return this;
		} else {
			return new AlternativeYamlTraversal(this, other);
		}
	}

	default YamlTraversal repeatAtLeast(int howMany) {
		if (isEmpty()) {
			return this;
		} else if (howMany > 0) {
			return this.then(this.repeatAtLeast(howMany-1));
		} else {
			return this.repeat();
		}
	}

	default YamlTraversal repeat() {
		return new RepeatingYamlTraversal(this);
	}

	/**
	 * Filters the end-points of a traversal, retaining only those
	 * for which the `check` traversal starting at the end-point
	 * leads somewhere.
	 */
	default YamlTraversal has(YamlTraversal check) {
		if (this.isEmpty()) {
			return this; // don't bother filtering empty!
		}
		return new FilteringTraversal(this, check);
	}


	//////////////////////////////////////////////////////
	// Computing information about the traversal's nature

	/**
	 * Returns true if the traversal has only one possible end-point, equal
	 * to its starting point. In other words the traversal does nothing.
	 */
	default boolean isEmpty() {
		return false;
	}

	/**
	 * Returns true if the traversal might include the starting point in its end points.
	 * <p>
	 * Note this is not the same as 'isEmpty', though 'isEmpty' implies 'canEmpty'.
	 * <p>
	 * If this returns null, it should be taken to mean 'unknown'. This is used for
	 * cases where analyzing the traversal can not predict for certain if the traversal
	 * allows a 'no movement' step.
	 */
	boolean canEmpty();

}
