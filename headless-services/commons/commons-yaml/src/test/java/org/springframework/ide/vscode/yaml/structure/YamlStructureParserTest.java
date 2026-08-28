/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.yaml.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SDocNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SRootNode;

public class YamlStructureParserTest {

	@Test public void escapedStringKey() throws Exception {
		MockYamlEditor editor;
		
		editor = new MockYamlEditor(
				"my:\n" +
				"  map:\n"+
				"    foobar:\n" +
				"      name: jeff"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): my:", 
				"    KEY(2): map:", 
				"      KEY(4): foobar:",
				"        KEY(6): name: jeff"
		);
		
		editor = new MockYamlEditor(
				"my:\n" +
				"  map:\n"+
				"    '[foo.bar]':\n" +
				"      name: jeff"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): my:", 
				"    KEY(2): map:", 
				"      KEY(4): '[foo.bar]':",
				"        KEY(6): name: jeff"
		);
		
	}
	
	@Test public void escapedStringKey_2() throws Exception {
		MockYamlEditor editor;
		
		editor = new MockYamlEditor(
				"my:\n" +
				"  map:\n"+
				"    foobar:\n" +
				"      name: jeff"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): my:", 
				"    KEY(2): map:", 
				"      KEY(4): foobar:",
				"        KEY(6): name: jeff"
		);
		
		editor = new MockYamlEditor(
				"my:\n" +
				"  map:\n"+
				"    '[**/]':\n" +
				"      name: jeff"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): my:", 
				"    KEY(2): map:", 
				"      KEY(4): '[**/]':",
				"        KEY(6): name: jeff"
		);
		
	}
	
	@Test public void ignoreLeadingYamlCruftBeforeLeadingDocumentSeparator() throws Exception {
		String[] stuffToIgnore = {
				"#comment",
				"   # comment with leading spaces",
				"%directive"
		};

		for (String stuff : stuffToIgnore) {
			System.out.println(stuff);
			MockYamlEditor editor = new MockYamlEditor(
					stuff+"\n" +
					"---\n" +
					"hello:\n"+
					"  world:\n" +
					"    message\n"
			);
			assertParseOneDoc(editor,
					"DOC(0): ---",
					"  KEY(0): hello:",
					"    KEY(2): world:",
					"      RAW(4): message",
					"      RAW(-1): "
			);

		}

	}

	@Test public void testLeadingDocumentSeparator() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"---\n" +
				"hello:\n"+
				"  world:\n" +
				"    message\n"
		);

		assertParseOneDoc(editor,
				"DOC(0): ---",
				"  KEY(0): hello:",
				"    KEY(2): world:",
				"      RAW(4): message",
				"      RAW(-1): "
		);
	}

	@Test public void testSimple() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"hello:\n"+
				"  world:\n" +
				"    message\n"
		);

		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): hello:",
				"    KEY(2): world:",
				"      RAW(4): message",
				"      RAW(-1): "
		);
	}

	public void assertParse(MockYamlEditor editor, String... expectDumpLines) throws Exception {
		StringBuilder expected = new StringBuilder();
		for (String line : expectDumpLines) {
			expected.append(line);
			expected.append("\n");
		}
		assertEquals(expected.toString().trim(),  editor.parseStructure().toString().trim());
	}

	public void assertParseOneDoc(MockYamlEditor editor, String... expectDumpLines) throws Exception {
		StringBuilder expected = new StringBuilder();
		for (String line : expectDumpLines) {
			expected.append(line);
			expected.append("\n");
		}
		assertEquals(expected.toString().trim(),  getOnlyDocument(editor.parseStructure()).toString().trim());
	}

	@Test public void testComments() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"#A comment\n" +
				"hello:\n"+
				"  #Another comment\n" +
				"  world:\n" +
				"    message\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
//				"  RAW(-1): #A comment",
				"  KEY(0): hello:",
				"    RAW(-1):   #Another comment",
				"    KEY(2): world:",
				"      RAW(4): message",
				"      RAW(-1): "
		);
	}

	@Test public void testSiblings() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): world:",
				"    KEY(2): europe:",
				"      KEY(4): france:",
				"        RAW(6): cheese",
				"      KEY(4): belgium:",
				"        RAW(4): beer",
				"    KEY(2): canada:",
				"      KEY(4): montreal: poutine",
				"      KEY(4): vancouver:",
				"        RAW(6): salmon",
				"  KEY(0): moon:",
				"    KEY(2): moonbase-alfa:",
				"      RAW(4): moonstone",
				"      RAW(-1): "
		);
	}

	@Test public void testMultiDocs() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"---\n"+
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"---\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n" +
				"...\n"
		);
		assertParse(editor,
				"ROOT(0): ",
				"  DOC(0): ",
				"    KEY(0): world:",
				"      KEY(2): europe:",
				"        KEY(4): france:",
				"          RAW(6): cheese",
				"        KEY(4): belgium:",
				"          RAW(4): beer",
				"  DOC(0): ---",
				"    KEY(2): canada:",
				"      KEY(4): montreal: poutine",
				"      KEY(4): vancouver:",
				"        RAW(6): salmon",
				"  DOC(0): ---",
				"    KEY(0): moon:",
				"      KEY(2): moonbase-alfa:",
				"        RAW(4): moonstone",
				"  DOC(0): ...",
				"    RAW(-1): "
		);
	}

	@Test public void testSequenceBasic() throws Exception {
		MockYamlEditor editor;

		//Sequence at root level
		editor = new MockYamlEditor(
				"- foo\n" +
				"- bar\n" +
				"- zor"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  SEQ(0): - foo",
				"  SEQ(0): - bar",
				"  SEQ(0): - zor"
		);

		//Sequences nested in map without indent
		editor = new MockYamlEditor(
				"something:\n" +
				"- foo\n" +
				"- bar\n" +
				"- zor\n" +
				"else:\n" +
				"- a\n" +
				"- def"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): something:" ,
				"    SEQ(0): - foo",
				"    SEQ(0): - bar",
				"    SEQ(0): - zor",
				"  KEY(0): else:",
				"    SEQ(0): - a",
				"    SEQ(0): - def"
		);

		//Sequences nested in map without indent
		editor = new MockYamlEditor(
				"higher:\n" +
				"  something:\n" +
				"  - foo\n" +
				"  - bar\n" +
				"  - zor\n" +
				"  else:\n" +
				"  - a\n" +
				"  - def"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): higher:",
				"    KEY(2): something:" ,
				"      SEQ(2): - foo",
				"      SEQ(2): - bar",
				"      SEQ(2): - zor",
				"    KEY(2): else:",
				"      SEQ(2): - a",
				"      SEQ(2): - def"
		);

		//Sequences nested in map with indent
		editor = new MockYamlEditor(
				"something:\n" +
				"  - foo\n" +
				"  - bar\n" +
				"  - zor\n" +
				"else:\n" +
				"  - a\n" +
				"  - def"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): something:" ,
				"    SEQ(2): - foo",
				"    SEQ(2): - bar",
				"    SEQ(2): - zor",
				"  KEY(0): else:",
				"    SEQ(2): - a",
				"    SEQ(2): - def"
		);

	}

	@Test public void testKeyWithADot() throws Exception {
		MockYamlEditor editor;

		//First try without a '.'
		editor = new MockYamlEditor(
				"logging:\n" +
				"  level:\n" +
				"    somepackage: "
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): logging:",
				"    KEY(2): level:",
				"      KEY(4): somepackage:"
		);

		editor = new MockYamlEditor(
				"logging:\n" +
				"  level:\n" +
				"    some.package: "
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): logging:",
				"    KEY(2): level:",
				"      KEY(4): some.package:"
		);

	}

	@Test public void testDeeplyNestedSequenceOnSingleLineDoesNotOverflowStack() throws Exception {
		// A single line packed with many "- " sequence markers must be
		// parsed iteratively - one recursive call per marker would blow the stack.
		int depth = 50_000;
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			line.append("- ");
		}
		line.append("leaf");

		MockYamlEditor editor = new MockYamlEditor(line.toString());
		SRootNode root = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> editor.parseStructure());
		assertNotNull(root);
	}

	@Test public void testSequenceWithNestedSequence() throws Exception {
		MockYamlEditor editor;

		editor = new MockYamlEditor(
				"- - a\n" +
				"  - b\n" +
				"- - c\n" +
				"  - d\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  SEQ(0): - - a",
				"    SEQ(2): - a",
				"    SEQ(2): - b",
				"  SEQ(0): - - c",
				"    SEQ(2): - c",
				"    SEQ(2): - d",
				"      RAW(-1): "
		);

		editor = new MockYamlEditor(
				"foo:\n" +
				"- - a\n" +
				"  - b\n" +
				"- - c\n" +
				"  - d"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): foo:",
				"    SEQ(0): - - a",
				"      SEQ(2): - a",
				"      SEQ(2): - b",
				"    SEQ(0): - - c",
				"      SEQ(2): - c",
				"      SEQ(2): - d"
		);

		editor = new MockYamlEditor(
				"foo:\n" +
				"- - a\n" +
				"  - b\n" +
				"bar:\n" +
				"- - c\n" +
				"  - d\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): foo:",
				"    SEQ(0): - - a",
				"      SEQ(2): - a",
				"      SEQ(2): - b",
				"  KEY(0): bar:",
				"    SEQ(0): - - c",
				"      SEQ(2): - c",
				"      SEQ(2): - d",
				"        RAW(-1): "
		);

		editor = new MockYamlEditor(
				"foo:\n" +
				"- \n" +
				"  - a\n" +
				"  - b\n" +
				"-\n" +
				"  - c\n" +
				"  - d"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): foo:",
				"    SEQ(0): - ",
				"      SEQ(2): - a",
				"      SEQ(2): - b",
				"    SEQ(0): -",
				"      SEQ(2): - c",
				"      SEQ(2): - d"
		);

		editor = new MockYamlEditor(
				"foo:\n" +
				"- - - - a\n" +
				"      - b\n" +
				"    - c\n" +
				"  - d\n" +
				"- e\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): foo:",
				"    SEQ(0): - - - - a",
				"      SEQ(2): - - - a",
				"        SEQ(4): - - a",
				"          SEQ(6): - a",
				"          SEQ(6): - b",
				"        SEQ(4): - c",
				"      SEQ(2): - d",
				"    SEQ(0): - e",
				"      RAW(-1): "
		);

		editor = new MockYamlEditor(
				"foo:\n" +
				"- - - - a\n" +
				"    - c\n" +
				"- e\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): foo:",
				"    SEQ(0): - - - - a",
				"      SEQ(2): - - - a",
				"        SEQ(4): - - a",
				"          SEQ(6): - a",
				"        SEQ(4): - c",
				"    SEQ(0): - e",
				"      RAW(-1): "
		);

	}

	@Test public void testSequenceWithNestedMap() throws Exception {
		MockYamlEditor editor;

		// A map nested in a sequence may start on the same line
		editor = new MockYamlEditor(
				"- foo: is foo\n" +
				"  bar: is bar\n" +
				"    junk\n" +
				"- a: aaa\n" +
				"  b: bbb\n"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  SEQ(0): - foo: is foo",
				"    KEY(2): foo: is foo",
				"    KEY(2): bar: is bar",
				"      RAW(4): junk",
				"  SEQ(0): - a: aaa",
				"    KEY(2): a: aaa",
				"    KEY(2): b: bbb",
				"      RAW(-1): "
		);

		//A map nested in a sequence may start on a new line
		editor = new MockYamlEditor(
				"-\n"+  //without space
				"  foo: is foo\n" +
				"  bar: is bar\n" +
				"    junk\n" +
				"- \n" +  //with space
				"  a: aaa\n" +
				"  b: bbb"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  SEQ(0): -",
				"    KEY(2): foo: is foo",
				"    KEY(2): bar: is bar",
				"      RAW(4): junk",
				"  SEQ(0): - ", //with space
				"    KEY(2): a: aaa",
				"    KEY(2): b: bbb"
		);

		editor = new MockYamlEditor(
				"foo:\n" +
				"-\n"+  //without space
				"  foo: is foo\n" +
				"  bar: is bar\n" +
				"    junk\n" +
				"- \n" +  //with space
				"  a: aaa\n" +
				"  b: bbb"
		);
		assertParseOneDoc(editor,
				"DOC(0): ",
				"  KEY(0): foo:",
				"    SEQ(0): -",
				"      KEY(2): foo: is foo",
				"      KEY(2): bar: is bar",
				"        RAW(4): junk",
				"    SEQ(0): - ", //with space
				"      KEY(2): a: aaa",
				"      KEY(2): b: bbb"
		);
	}

	@Test public void testTraverseSeq() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"foo:\n" +
				"- - - - a\n" +
				"    - c\n" +
				"- e"
		);
		SRootNode root = editor.parseStructure();
		YamlPath path;

		path = pathWith(0, "foo", 0, 0, 0, 0);
		assertEquals(
				"SEQ(6): - a\n",
				path.traverse((SNode)root).toString());

		path = pathWith(0, "foo", -1);
		assertNull(path.traverse((SNode)root));

		path = pathWith(0, "foo", 1);
		assertEquals(
				"SEQ(0): - e\n",
				path.traverse((SNode)root).toString());

		path = pathWith(0, "foo", 2);
		assertNull(path.traverse((SNode)root));
	}

	@Test public void testFindAndTraverseSeqNode() throws Exception {
		MockYamlEditor editor;

		editor = new MockYamlEditor(
				"foo:\n"+
				"- abc\n" +
				"- def\n" +
				"- ghi\n"
		);
		findAndTraversPathPath(editor, "abc");
		findAndTraversPathPath(editor, "def");
		findAndTraversPathPath(editor, "ghi");

		// nodes are position sensitive make sure that generated positions agree
		// with traverse interpretation, even in case where it is not so well-defined
		// how the indices should be interpreted:
		editor = new MockYamlEditor(
				"foo:\n"+
				"  garbage\n" +
				"  - abc\n" +
				"  junk\n" +
				"  - def\n" +
				"  crap\n" +
				" - ghi\n"
		);
		findAndTraversPathPath(editor, "abc");
		findAndTraversPathPath(editor, "def");
		findAndTraversPathPath(editor, "ghi");

	}

	private void findAndTraversPathPath(MockYamlEditor editor, String snippet) throws Exception {
		SRootNode root = editor.parseStructure();
		SNode node = root.find(editor.startOf(snippet));
		assertNotNull(node);

		YamlPath path = node.getPath();
		SNode actualNode = path.traverse((SNode)root);
		assertEquals(node, actualNode);
	}

	@Test public void testTraverseSeqKey() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"foo:\n" +
				"- bar:\n" +
				"  - a\n" +
				"  - key: lol\n" +
				"- e\n"
		);
		SRootNode root = editor.parseStructure();
		YamlPath path;

		path = pathWith(0, "foo", 0, "bar", 1, "key");
		assertEquals(
				"KEY(4): key: lol\n",
				path.traverse((SNode)root).toString());
	}

	@Test public void testDedentedRawNode() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"  beer\n" + //De-dented raw node, doesn't obviously belong to any node, but we associate it with closest 'structural' node
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		assertParseOneDoc(editor, ////////////////
				"DOC(0): ",
				"  KEY(0): world:",
				"    KEY(2): europe:",
				"      KEY(4): france:",
				"        RAW(6): cheese",
				"      KEY(4): belgium:",
				"        RAW(2): beer",
				"    KEY(2): canada:",
				"      KEY(4): montreal: poutine",
				"      KEY(4): vancouver:",
				"        RAW(6): salmon",
				"  KEY(0): moon:",
				"    KEY(2): moonbase-alfa:",
				"      RAW(4): moonstone",
				"      RAW(-1):"
		);
	}

	@Test public void testTreeEnd() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		SRootNode root = editor.parseStructure();
		SNode node = getNodeAtPath(root, 0, 0, 1);
		assertTreeText(editor, node,
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n"
		);

		node = getNodeAtPath(root, 0, 0, 0, 1, 0);
		assertTreeText(editor, node,
				"beer"
		);
	}

	@Test public void testTreeEndKeyNodeNoChildren() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		SRootNode root = editor.parseStructure();
		SNode node = getNodeAtPath(root, 0, 0, 0);
		assertTreeText(editor, node,
				"  europe:"
		);
	}

	@Test public void testFind() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		SRootNode root = editor.parseStructure();
		assertFind(editor, root, "world:", 					0, 0);
		assertFind(editor, root,   "europe:", 				0, 0, 0);
		assertFind(editor, root,     "france:",				0, 0, 0, 0);
		assertFind(editor, root,       "cheese",			0, 0, 0, 0, 0);
		assertFind(editor, root,     "belgium:",			0, 0, 0, 1);
		assertFind(editor, root,     "beer",				0, 0, 0, 1, 0);
		assertFind(editor, root,   "canada:",				0, 0, 1);
		assertFind(editor, root,     "montreal: poutine",	0, 0, 1, 0);
		assertFind(editor, root,     "vancouver:",			0, 0, 1, 1);
		assertFind(editor, root,        "salmon",			0, 0, 1, 1, 0);
		assertFind(editor, root, "moon:",					0, 1);
		assertFind(editor, root,   "moonbase-alfa:",		0, 1, 0);
		assertFind(editor, root,     "moonstone",			0, 1, 0, 0);

		assertFindStart(editor, root, " europe:", 0, 0);
	}

	@Test public void testFindInMultiDoc() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"---\n" +
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"---\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n" +
				"..."
		);
		SRootNode root = editor.parseStructure();
		assertFind(editor, root, "world:", 					0, 0);
		assertFind(editor, root,   "europe:", 				0, 0, 0);
		assertFind(editor, root,     "france:",				0, 0, 0, 0);
		assertFind(editor, root,       "cheese",			0, 0, 0, 0, 0);
		assertFind(editor, root,     "belgium:",			0, 0, 0, 1);
		assertFind(editor, root,     "beer",				0, 0, 0, 1, 0);
		assertFind(editor, root,   "canada:",				1, 0);
		assertFind(editor, root,     "montreal: poutine",	1, 0, 0);
		assertFind(editor, root,     "vancouver:",			1, 0, 1);
		assertFind(editor, root,        "salmon",			1, 0, 1, 0);
		assertFind(editor, root, "moon:",					2, 0);
		assertFind(editor, root,   "moonbase-alfa:",		2, 0, 0);
		assertFind(editor, root,     "moonstone",			2, 0, 0, 0);

		assertFindStart(editor, root, " canada:", 1);
	}


	@Test public void testFindInSequence() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"foo:\n" +
				"- alchemy\n" +
				"- bistro\n" +
				"bar:\n" +
				"- - - nice: text\n"+
				"zor:\n" +
				"  - - - very: good\n" +
				"end: END"
		);
		SRootNode root = editor.parseStructure();

		assertFind     (editor, root, "foo:",				0, 0);
		assertFind     (editor, root, "- alchemy",			0, 0, 0);
		assertFind     (editor, root, "- bistro",			0, 0, 1);
		assertFind     (editor, root, "bar:",				0, 1);
		assertFindStart(editor, root, "- - - nice: text",	0, 1, 0);
		assertFindStart(editor, root,  " - - nice: text",	0, 1, 0);
		assertFindStart(editor, root,   "- - nice: text",	0, 1, 0, 0);
		assertFindStart(editor, root,    " - nice: text",	0, 1, 0, 0);
		assertFindStart(editor, root,     "- nice: text",	0, 1, 0, 0, 0);
		assertFindStart(editor, root,      " nice: text",	0, 1, 0, 0, 0);
		assertFind     (editor, root,       "nice: text",	0, 1, 0, 0, 0, 0);
		assertFind     (editor, root, "zor:",				0, 2);
		assertFindStart(editor, root, "  - - - very: good",	0, 2);
		assertFindStart(editor, root,  " - - - very: good",	0, 2);
		assertFindStart(editor, root,   "- - - very: good",	0, 2, 0);
		assertFindStart(editor, root,    " - - very: good",	0, 2, 0);
		assertFindStart(editor, root,     "- - very: good",	0, 2, 0, 0);
		assertFindStart(editor, root,      " - very: good",	0, 2, 0, 0);
		assertFindStart(editor, root,       "- very: good",	0, 2, 0, 0, 0);
		assertFindStart(editor, root,        " very: good",	0, 2, 0, 0, 0);
		assertFind     (editor, root,         "very: good",	0, 2, 0, 0, 0, 0);
	}

	@Test public void testGetKey() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		SRootNode root = editor.parseStructure();
		assertKey(editor, root, "world:", 				"world");
		assertKey(editor, root, "europe:", 				"europe");
		assertKey(editor, root, "montreal: poutine",	"montreal");
	}

	@Test public void testIsInValue() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"foo:\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);
		SRootNode root = editor.parseStructure();
		assertValueRange(editor, root, "montreal: poutine",	" poutine");
		assertValueRange(editor, root, "europe:", "\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer");
		assertValueRange(editor, root, "foo:", null);
	}

	@Test public void testEmptyLines() throws Exception {
		//Attempt to pin down issue in YamlStructureParser related to
		//https://www.pivotaltracker.com/story/show/163752179
		String[] insertion = {
				"",
				"\n"
		};

		YamlPath expect = new YamlPath(
				valueAt(0), //document index
				valueAt("jobs"),
				valueAt(0),
				valueAt("plan"),
				valueAt(1),
				valueAt("params")
		);

		for (String maybeEmptyLine : insertion) {
			MockYamlEditor editor = new MockYamlEditor(
//					"resources:\n" +
//					"\n" +
//					"- name: banana-img.git\n" +
//					"  type: docker-image\n" +
//					"  source:\n" +
//					"    repository: repo/banana-scratch-build\n" +
//					"\n" +
//					"- name: repo.git\n" +
//					"  type: git\n" +
//					"  source:\n" +
//					"    uri: https://example.com/repo.git\n" +
//					"\n" +
					"jobs:\n" +
					"- name: work-img\n" +
					"  plan:\n" +
					maybeEmptyLine +
					"  - get: repo.git\n" +
					"  - put: banana-img.git\n" +
					"    params:\n" +
					"      "
			);

			int cursor = editor.getRawText().length()-1;
			SRootNode root = editor.parseStructure();
			SNode node = root.find(cursor);
			System.out.println("node = "+node);
			YamlPath path = node.getPath();
			System.out.println("path = "+path);
			assertEquals(expect.toString(), path.toString());

			SNode traversed = path.traverse(root);
			//Note: We might expact path of a node should lead to that node, but actually it doesn't for historic reasons.
			assertEquals(node.getParent(), traversed);
		}
	}

	private void assertValueRange(MockYamlEditor editor, SRootNode root, String nodeText, String expectedValue) throws Exception {
		int start = editor.getText().indexOf(nodeText);
		SKeyNode node = (SKeyNode) root.find(start);
		int valueRangeStart;
		int valueRangeEnd;
		if (expectedValue == null) {
			valueRangeStart = valueRangeEnd = start+nodeText.length();
		} else {
			valueRangeStart = editor.getRawText().lastIndexOf(expectedValue);
			valueRangeEnd = valueRangeStart+expectedValue.length();
			assertEquals(expectedValue, editor.textBetween(valueRangeStart, valueRangeEnd));
		}

		assertTrue(node.isInValue(valueRangeStart));
		assertFalse(node.isInValue(valueRangeStart-1));
		assertTrue(node.isInValue(valueRangeEnd));
		assertFalse(node.isInValue(valueRangeEnd+1));
	}

	@Test public void testTraverse() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"world:\n" +
				"  europe:\n" +
				"    france:\n" +
				"      cheese\n" +
				"    belgium:\n" +
				"    beer\n" + //At same level as key, technically this is a syntax error but we tolerate it
				"  canada:\n" +
				"    montreal: poutine\n" +
				"    vancouver:\n" +
				"      salmon\n" +
				"moon:\n" +
				"  moonbase-alfa:\n" +
				"    moonstone\n"
		);


		SRootNode root = editor.parseStructure();
		YamlPath pathToFrance = pathWith(
				0, "world", "europe", "france"
		);
		assertEquals(
				"KEY(4): france:\n"+
				"  RAW(6): cheese\n",
				pathToFrance.traverse((SNode)root).toString());

		assertNull(pathWith(0, "world", "europe", "bogus").traverse((SNode)root));
	}

	@Test public void testGetFirstRealChild() throws Exception {
		MockYamlEditor editor = new MockYamlEditor(
				"no-children:\n" +
				"unreal-children:\n" +
				"  #Unreal\n" +
				"\n" +
				"  #comment only\n" +
				"real-child:\n" +
				"  abc\n" +
				"mixed-children:\n" +
				"\n" +
				"#comment\n" +
				"  def"
		);

		assertFirstRealChild(editor, "no-children", null);
		assertFirstRealChild(editor, "unreal-children", null);
		assertFirstRealChild(editor, "real-child", "abc");
		assertFirstRealChild(editor, "mixed-children", "def");
	}

	@Test public void testDocumentSeparatorRegexp() throws Exception {
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "---");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "...");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "---   	");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "...			");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "---   #The next doc starts here");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "...   #The previous doc ends here");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "---#The next doc starts here");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "...#The previous doc ends here");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "---#");
		assertMatch(YamlStructureParser.DOCUMENT_SEPERATOR, "...#");
	}

	private void assertMatch(Pattern pat, String string) {
		assertTrue(pat.matcher(string).matches(), "Doesn't match: '"+string+"'");
	}

	private void assertFirstRealChild(MockYamlEditor editor, String testNodeName, String expectedNodeSnippet) throws Exception {
		SDocNode doc = getOnlyDocument(editor.parseStructure());
		SKeyNode testNode = doc.getChildWithKey(testNodeName);
		assertNotNull(testNode);
		SNode expected = null;
		if (expectedNodeSnippet != null) {
			int offset = editor.getRawText().indexOf(expectedNodeSnippet);
			expected = doc.find(offset);
			assertTrue(editor.textUnder(expected).contains(expectedNodeSnippet));
		}

		assertEquals(expected, testNode.getFirstRealChild());
	}

	private SDocNode getOnlyDocument(SRootNode root) {
		assertEquals(1, root.getChildren().size());
		return (SDocNode) root.getChildren().get(0);
	}

	private YamlPath pathWith(Object... keysOrIndexes) {
		ArrayList<YamlPathSegment> segments = new ArrayList<YamlPathSegment>();
		for (Object keyOrIndex : keysOrIndexes) {
			if (keyOrIndex instanceof String) {
				segments.add(YamlPathSegment.valueAt((String)keyOrIndex));
			} else if (keyOrIndex instanceof Integer) {
				segments.add(YamlPathSegment.valueAt((Integer)keyOrIndex));
			} else {
				fail("Unknown type of path element: "+keyOrIndex);
			}
		}
		return new YamlPath(segments);
	}

	private void assertKey(MockYamlEditor editor, SRootNode root, String nodeText, String expectedKey) throws Exception {
		int start = editor.getText().indexOf(nodeText);
		SKeyNode node = (SKeyNode) root.find(start);
		String key = node.getKey();
		assertEquals(expectedKey, key);

		//test the key range as well
		int startOfKeyRange = node.getStart();
		int keyRangeLen = key.length();
		int endOfKeyRange = startOfKeyRange + keyRangeLen;
		assertTrue(node.isInKey(startOfKeyRange));
		assertFalse(node.isInKey(startOfKeyRange-1));
		assertTrue(node.isInKey(endOfKeyRange));
		assertFalse(node.isInKey(endOfKeyRange+1));
	}

	private void assertFind(MockYamlEditor editor, SRootNode root, String snippet, int... expectPath) {
		int start = editor.getRawText().indexOf(snippet);
		int end = start+snippet.length();
		int middle = (start+end) / 2;

		SNode expectNode = getNodeAtPath(root, expectPath);

		assertEquals(expectNode, root.find(start));
		assertEquals(expectNode, root.find(middle));
		assertEquals(expectNode, root.find(end));
	}

	private void assertFindStart(MockYamlEditor editor, SRootNode root, String snippet, int... expectPath) {
		int start = editor.getRawText().indexOf(snippet);
		SNode expectNode = getNodeAtPath(root, expectPath);
		assertEquals(expectNode, root.find(start));
	}

	private void assertTreeText(MockYamlEditor editor, SNode node, String expected) throws Exception {
		String actual = editor.textBetween(node.getStart(), node.getTreeEnd());
		assertEquals(expected.trim(), actual.trim());
	}

	private SNode getNodeAtPath(SNode node, int... childindices) {
		int i = 0;
		while (i < childindices.length) {
			int child = childindices[i];
			node = ((SChildBearingNode)node).getChildren().get(child);
			i++;
		}
		return node;
	}

}
