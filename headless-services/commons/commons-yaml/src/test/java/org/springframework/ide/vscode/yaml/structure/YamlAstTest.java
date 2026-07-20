/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.yaml.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.yaml.snakeyaml.nodes.Node;

/**
 * Yaml AST tests 
 * 
 * @author Kris De Volder
 * @author Alex Boyko
 *
 */
public class YamlAstTest {

	/**
	 * Check that node at given offset exactly covers the expected text
	 * snippet in the document.
	 */
	public void assertNodeTextAt(MockYamlEditor input, int offset, String expectText) throws Exception {
		YamlFileAST ast = input.parse();
		Node node = ast.findNode(offset);
		String actualText = input.textUnder(node);
		assertEquals(expectText, actualText);
	}


	@Test
	public void testFindNode() throws Exception {
		MockYamlEditor input = new MockYamlEditor(
				"spring:\n" +
				"  application:\n" +
				"    name: foofoo\n" +
				"    \n" +
				"server:\n" +
				"  port: 8888"
		);
		doFindNodeTest(input, "foofoo");
		doFindNodeTest(input, "application");
		doFindNodeTest(input, "spring");
		doFindNodeTest(input, "port");
		doFindNodeTest(input, "server");
	}

	private void doFindNodeTest(MockYamlEditor input, String nodeText) throws Exception {
		assertNodeTextAt(input, input.startOf(nodeText), nodeText);
		assertNodeTextAt(input, input.endOf(nodeText)-1, nodeText);
		assertNodeTextAt(input, input.middleOf(nodeText), nodeText);
	}

	public void testFindPath() throws Exception {
		MockYamlEditor input = new MockYamlEditor(
				"foo:\n" +
				"  bar:\n" +
				"    first: foofoo\n" +
				"    second: barbar\n" +
				"    third: lalala\n" +
				"server:\n" +
				"  port: 8888"
		);

		assertPath(input, "barbar",
				"ROOT[0]@val['foo']@val['bar']@val['second']");

		assertPath(input, "8888",
				"ROOT[0]@val['server']@val['port']");
	}

	@Test
	public void testMultiDocsPath() throws Exception {
		MockYamlEditor input = new MockYamlEditor(
				"theFirst\n" +
				"---\n" +
				"second\n" +
				"...\n"
		);
		assertPath(input, "First",
				"ROOT[0]"
		);
		assertPath(input, "second",
				"ROOT[1]"
		);
	}

	public void testSequencePath() throws Exception {
		MockYamlEditor input = new MockYamlEditor(
				"fooList:\n"+
				"  - a\n" +
				"  - b\n" +
				"  - c\n"
		);

		assertPath(input, "a",
				"ROOT[0]@val['fooList'][0]"
		);
		assertPath(input, "b",
				"ROOT[0]@val['fooList'][1]"
		);
		assertPath(input, "c",
				"ROOT[0]@val['fooList'][2]"
		);
	}

	protected void assertPath(MockYamlEditor input, String nodeText, String expected) throws Exception {
		YamlFileAST ast = input.parse();
		String path = pathString(ast.findPath(input.middleOf(nodeText)));
		assertEquals(expected,  path);
	}


	private String pathString(List<NodeRef<?>> findPath) {
		StringBuilder buf = new StringBuilder();
		for (NodeRef<?> n : findPath) {
			buf.append(n);
		}
		return buf.toString();
	}

	@Test
	public void testSelfReferentialAliasDoesNotHang() throws Exception {
		MockYamlEditor input = new MockYamlEditor(
				"a: &x\n" +
				"  b: *x\n" +
				"c: normal\n"
		);

		// Sibling content unrelated to the cycle must still resolve normally.
		assertNodeTextAt(input, input.middleOf("normal"), "normal");

		// Descending into the self-referential anchor must terminate rather than recurse forever.
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			YamlFileAST ast = input.parse();
			ast.findNode(input.middleOf("b"));
			ast.findPath(input.middleOf("b"));
		});
	}

}
