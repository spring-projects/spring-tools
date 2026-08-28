/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.structure;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.Streams;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.yaml.path.KeyAliases;
import org.springframework.ide.vscode.commons.yaml.path.YamlNavigable;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.util.YamlIndentUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

/**
 * A robust, coarse-grained parser that guesses the structure of a
 * yml document based on indentation levels.
 * <p>
 * This is not a full parser but is desgned to succeed computing some kind of 'structure' tree
 * for anything you might throw at it. The goal is to be accurate only for 'typical' yml files
 * used to define spring-boot properties. Essentially a the file contains a bunch of nested
 * mapping nodes in 'block' style using 'simple' keys.
 * <p>
 * I.e something like this:
 * <pre>
 * foo:
 *   bar:
 *     zor: Hello
 *       this is
 *       tex
 *     more-keys:
 *       - foo
 *       - bar
 * </pre>
 * <p>
 * When the parser encounters something it can not identify as a 'simple-key: <value>'
 * binding then it treats that just as 'raw' text data and associates it as nested
 * information with the closest preceding recognized key node which is indented
 * at the same or lower level than this node.
 *
 * @author Kris De Volder
 */
public class YamlStructureParser {
	
	private static final Logger log = LoggerFactory.getLogger(YamlStructureParser.class);

	/**
	 * Pattern that matches a line starting with a 'simple key'
	 */
	public static final Pattern SIMPLE_KEY_LINE = Pattern.compile(
			"^((\\w(\\.|\\w|-)*)|(\\'(\\.|\\w|-|\\[|\\]|\\*|/)*\\')):( .*|$)");
	//TODO: the parrern above is too selective (e.g. in real yaml one can have
	//spaces in simple keys and lots of other characters that this pattern does not
	//allow. For now it is good enough because we are only interested in spring property
	//names which typically do not contain spaces and other funky characters.

	/**
	 * Pattern that matches a line starting with a sequence header '- '
	 */
	public static final Pattern SEQ_LINE = Pattern.compile(
			"^(\\-( |$)).*");


	public static final Pattern DOCUMENT_SEPERATOR = Pattern.compile("^(---|\\.\\.\\.)(\\s)*(\\#.*)?");
	//This expression matches:
	//   either "..." or "---" at the start of a line
	//   followed by arbitrary amount of whitepsace
	//   optionally followed by a "#" end of line comment.
	//Note: "..." isn't a document separator but document terminator. Treating it as a separator is
	// technically not correct. As the structure parser is meant to be 'robust' and do something
	// sensible with incorrect input this makes sense here. The effect it will have is that user
	// can type after a document terminator and get content assist as if they are in a new document.
	// (They will also receive a syntax error message from the more formal and precise SnakeYaml parser)


	/**
	 * Stuff to ignore at the beginning of a yaml file (before the start of the first document):
	 */
	private static final Pattern SKIP_AT_START_OF_DOC = Pattern.compile(
			"^((\\s*\\#)|(\\%)).*"
	);

//	public static final Pattern SEQ_LINE = Pattern.compile(
//			"^( *)- .*");

	public static enum SNodeType {
		ROOT, DOC, KEY, SEQ, RAW
	}

	private YamlLineReader input;

	private final KeyAliases keyAliases;

	public static class YamlLine {

		// line = "    hello"
		//         ^   ^    ^
		//         |   |    end
		//         |   indent
		//         start

		public static YamlLine atLineNumber(YamlDocument doc, int line) throws Exception {
			if (line < doc.getDocument().getNumberOfLines()) {
				IRegion l = doc.getLineInformation(line);
				int start = l.getOffset();
				int end = start + l.getLength();
				return new YamlLine(doc, start, doc.getLineIndentation(line), end);
			}
			return null;
		}

		private YamlDocument doc;
		private int start;
		private int indent;
		private int end;

		private YamlLine(YamlDocument doc, int start, int indent, int end) {
			this.doc = doc;
			this.start = start;
			this.indent = indent;
			this.end = end;
		}
		public int getIndent() {
			return indent;
		}
		public int getEnd() {
			return end;
		}
		public int getStart() {
			return start;
		}
		public boolean matches(Pattern pat, boolean stripiIndentation) throws Exception {
			CharSequence text = stripiIndentation ? getTextWithoutIndent() : getText();
			return pat.matcher(text).matches();
		}
		public boolean matches(Pattern pat) throws Exception {
			return matches(pat, true);
		}
		public String getTextWithoutIndent() throws Exception {
			return doc.textBetween(getStart()+getIndent(), getEnd());
		}
		public String getText() throws Exception {
			return doc.textBetween(getStart(), getEnd());
		}
		@Override
		public String toString() {
			try {
				return "YamlLine("+getLineNumber()+": "+getText()+")";
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		private int getLineNumber() throws Exception {
			return doc.getLineOfOffset(start);
		}
		public YamlLine moveIndentMark(int moveBy) throws Exception {
			return new YamlLine(doc, start, Math.min(indent+moveBy, getLineLength()), end);
		}
		private int getLineLength() throws Exception {
			return getEnd()-getStart();
		}
		public YamlDocument getDocument() {
			return doc;
		}
	}

	public class YamlLineReader {
		private final YamlDocument doc;
		private int nextLine = 0; //next line to read

		public YamlLineReader(YamlDocument doc) {
			this.doc = doc;
		}

		public YamlLine peek() throws Exception {
			if (nextLine < doc.getDocument().getNumberOfLines()) {
				return YamlLine.atLineNumber(doc, nextLine);
			}
			return null; //means EOF
		}

		public YamlLine read() throws Exception {
			if (nextLine < doc.getDocument().getNumberOfLines()) {
				return YamlLine.atLineNumber(doc, nextLine++);
			}
			return null; //means EOF
		}

		public YamlDocument getDocument() {
			return doc;
		}
	}

	public YamlStructureParser(YamlDocument doc, KeyAliases keyAliases) {
		this.input = new YamlLineReader(doc);
		this.keyAliases = keyAliases;
	}

	private static void indent(Writer out, int indent) throws Exception {
		for (int i = 0; i < indent; i++) {
			out.write("  ");
		}
	}

	public static abstract class SNode implements YamlNavigable<SNode> {
		private SChildBearingNode parent;
		private int indent;
		private int start;
		private int end;
		protected final YamlDocument doc;

		public SNode(SChildBearingNode parent, YamlDocument doc, int indent, int start, int end) {
			Assert.isLegal(this instanceof SRootNode || parent != null);
			this.parent = parent;
			this.doc = doc;
			this.indent = indent;
			this.start = start;
			this.end = end;
			if (parent != null) {
				parent.addChild(this);
			}
		}
		public SChildBearingNode getParent() {
			return parent;
		}
		public int getStart() {
			return start;
		}
		public int getNodeEnd() {
			return end;
		}
		public abstract int getTreeEnd();
		public final int getIndent() {
			return indent;
		}

		@Override
		public final String toString() {
			StringWriter out = new StringWriter();
			try {
				dump(out, 0);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return out.toString();
		}

		public abstract SNodeType getNodeType();
		public abstract SNode find(int offset);

		public boolean nodeContains(int offset) {
			return getStart()+Math.max(0, getIndent()) <= offset && offset <= getNodeEnd();
		}

		public boolean treeContains(int offset) {
			return getStart() <= offset && offset <= getTreeEnd();
		}

		public IDocument getDocument() {
			return doc.getDocument();
		}

		public String getText() throws Exception {
			return doc.textBetween(start, end);
		}

		/**
		 * Default implementation, doesn't support any type of traversal operation.
		 * Subclasses must override and implement where appropriate.
		 */
		@Override
		public Stream<SNode> traverseAmbiguously(YamlPathSegment s) {
			return Stream.empty();
		}

		/**
		 * Default implementation, actusllu the same as the default impl provided by
		 * the interface. This is only here to prevent subclasses from implementing
		 * this. They should implement traverseAmbiguously instead.
		 */
		@Override
		public SNode traverse(YamlPathSegment s) {
			return traverseAmbiguously(s).findFirst().orElse(null);
		}

		protected abstract void dump(Writer out, int indent) throws Exception;

		public ImmutableList<SNode> getPathNodes() throws Exception {
			ImmutableList.Builder<SNode> nodes = ImmutableList.builder();
			buildPath(this, nodes);
			return nodes.build();
		}

		private static void buildPath(SNode node, Builder<SNode> nodes) {
			if (node != null) {
				buildPath(node.getParent(), nodes);
				nodes.add(node);
			}
		}

		public YamlPath getPath() throws Exception {
			List<YamlPathSegment> path = new ArrayList<>();
			for (SNode node : getPathNodes()) {
				YamlPathSegment segment = node.getSegment();
				if (segment != null) {
					path.add(segment);
				}
			}
			return new YamlPath(path);
		}

		/**
		 * Determine a YamlPathSegment that corresponds to given node. This may be
		 * null because not all SNodes can be interpreted as 'step' in the yml
		 * structure (e.g. raw nodes will return null, as will the 'root' node).
		 */
		public YamlPathSegment getSegment() {
			try {
				SNode node = this;
				if (node != null) {
					SNodeType nodeType = node.getNodeType();
					if (nodeType == SNodeType.KEY) {
						String key = ((SKeyNode)node).getKey();
						return YamlPathSegment.valueAt(key);
					} else if (nodeType == SNodeType.SEQ) {
						int index = ((SSeqNode)node).getIndex();
						return YamlPathSegment.valueAt(index);
					} else if (nodeType == SNodeType.DOC) {
						int index = ((SDocNode)node).getIndex();
						return YamlPathSegment.valueAt(index);
					}
				}
			} catch (Exception e) {
				log.error("", e);
			}
			return null;
		}

		public SRootNode getRoot() {
			if (parent == null) {
				return (SRootNode) this;
			}
			return parent.getRoot();
		}

		public SDocNode getDocNode() {
			SNode it = this;
			while (it != null && !(it instanceof SDocNode)) {
				it = it.getParent();
			}
			return (SDocNode) it;
		}
	}

	public class SRootNode extends SChildBearingNode {

		public SRootNode(YamlDocument doc) {
			super(null, doc, 0,0,0);
		}

		@Override
		public SNodeType getNodeType() {
			return SNodeType.ROOT;
		}

		@Override
		public void addChild(SNode c) {
			Assert.isLegal(c.getNodeType() == SNodeType.DOC, ""+c.getNodeType());
			super.addChild(c);
		}

		@Override
		public Stream<SNode> traverseAmbiguously(YamlPathSegment s) {
			Integer index = s.toIndex();
			if (index != null) {
				List<SNode> cs = getChildren();
				if (index >= 0 && index < cs.size()) {
					return Streams.fromNullable(cs.get(index));
				}
			}
			return Stream.empty();
		}
	}

	public class SDocNode extends SChildBearingNode {

		private int index;

		/**
		 * If this SDocNode is started explicitly by '---' document separator
		 * then the start and end will be set according to its position.
		 * <p>
		 * If a document is started implicitly (at the start of the file/editor)
		 * then start and end are set to 0.
		 */
		public SDocNode(SRootNode parent, int start, int end) {
			super(parent, parent.doc, 0, start, end);
			this.index = parent.getChildren().size()-1;
		}

		public int getIndex() {
			return index;
		}

		@Override
		public SNodeType getNodeType() {
			return SNodeType.DOC;
		}

		public boolean exists(YamlPath path) throws Exception {
			return path.traverse((SNode)this) != null;
		}

	}

	public abstract class SChildBearingNode extends SNode {
		private List<SNode> children = null;
		private Multimap<Object, SNode> keyMap = null; //lazily constructed index of children.
		private int seqChildren = 0; //Keeps a tally of number of children of type Seq

		public SChildBearingNode(SChildBearingNode parent, YamlDocument doc, int indent, int start, int end) {
			super(parent, doc, indent, start, end);
		}

		public List<SNode> getChildren() {
			if (children != null) {
				return Collections.unmodifiableList(children);
			}
			return Collections.emptyList();
		}
		public void addChild(SNode c) {
			if (children == null) {
				children = new ArrayList<SNode>();
			}
			children.add(c);
			if (c instanceof SSeqNode) {
				seqChildren++;
			}
		}
		public SNode getLastChild() {
			List<SNode> cs = getChildren();
			if (!cs.isEmpty()) {
				return cs.get(cs.size()-1);
			}
			return null;
		}
		@Override
		public int getTreeEnd() {
			if (getChildren().isEmpty()) {
				return getNodeEnd();
			}
			return getLastChild().getTreeEnd();
		}
		@Override
		protected final void dump(Writer out, int indent) throws Exception {
			indent(out, indent);
			out.write(getNodeType().toString());
			out.write('(');
			int nodeIndent = getIndent();
			out.write(""+nodeIndent);
			out.write("): ");
			out.write(getText());
			out.write('\n');
			for (SNode child : getChildren()) {
				child.dump(out, indent+1);
			}
		}

		@Override
		public SNode find(int offset) {
			if (!treeContains(offset)) {
				return null;
			}
			for (SNode c : getChildren()) {
				SNode fromChild = c.find(offset);
				if (fromChild != null) {
					return fromChild;
				}
			}
			return this;
		}

		@Override
		public Stream<SNode> traverseAmbiguously(YamlPathSegment s) {
			switch (s.getType()) {
			case VAL_AT_KEY:
				return this.getChildrenWithKey(s.toPropString());
			case VAL_AT_INDEX:
				return Streams.fromNullable(this.getSeqChildWithIndex(s.toIndex()));
			default:
				return Stream.empty();
			}
		}


		private SSeqNode getSeqChildWithIndex(int index) {
			if (index >= 0) {
				Collection<SNode> children = keyMap().get(index);
				if (!children.isEmpty()) {
					SNode child = children.iterator().next();
					if (child instanceof SSeqNode) {
						return (SSeqNode) child;
					}
				}
			}
			return null;
		}

		public Stream<SNode> getChildrenWithKey(String key) {
			if (CollectionUtil.hasElements(children)) {
				Stream<SNode> allChildren = Stream.empty();
				Collection<SNode> plainChildren = keyMap().get(key);
				if (CollectionUtil.hasElements(plainChildren)) {
					allChildren = plainChildren.stream();
				}
				Iterable<String> keyAliases = getKeyAliases(key);
				if (keyAliases != null) {
					for (String keyAlias : keyAliases) {
						Collection<SNode> aliasChildren = keyMap().get(keyAlias);
						if (CollectionUtil.hasElements(aliasChildren)) {
							allChildren = Stream.concat(allChildren, aliasChildren.stream());
						}
					}
				}
				return allChildren;
			}
			return Stream.empty();
		}

		public SKeyNode getChildWithKey(String key) {
			return (SKeyNode)getChildrenWithKey(key).findFirst().orElse(null);
		}

		private Multimap<Object, SNode> keyMap() {
			if (keyMap == null) {
				ListMultimap<Object, SNode> index = MultimapBuilder.hashKeys().arrayListValues().build();
				for (SNode node: getChildren()) {
					try {
						if (node.getNodeType() == SNodeType.KEY) {
							SKeyNode keyNode = (SKeyNode)node;
							String key = keyNode.getKey();
							index.put(key, keyNode);
						} else if (node.getNodeType() == SNodeType.SEQ) {
							SSeqNode seqNode = (SSeqNode) node;
							int key = seqNode.index;
							index.put(key, seqNode);
						}
					} catch (Exception e) {
						log.error("", e);
					}
				}
				keyMap = index;
			}
			return keyMap;
		}

		public SNode getFirstRealChild() {
			for (SNode c : getChildren()) {
				if (c.getIndent() >= 0) {
					return c;
				}
			}
			return null;
		}

		public SNode getLastRealChild() {
			for (SNode c : Lists.reverse(getChildren())) {
				if (c.getIndent() >= 0) {
					return c;
				}
			}
			return null;
		}

		public int seqChildrenCount() {
			return seqChildren;
		}

	}

	public abstract class SLeafNode extends SNode {

		public SLeafNode(SChildBearingNode parent, YamlDocument doc,
				int indent, int start, int end) {
			super(parent, doc, indent, start, end);
		}

		@Override
		public int getTreeEnd() {
			return getNodeEnd();
		}

		@Override
		protected final void dump(Writer out, int indent) throws Exception {
			indent(out, indent);
			out.write(getNodeType().toString());
			out.write('(');
			int nodeIndent = getIndent();
			out.write(""+nodeIndent);
			out.write("): ");
			out.write(getText());
			out.write('\n');
		}

		@Override
		public SNode find(int offset) {
			if (treeContains(offset)) {
				return this;
			}
			return null;
		}
	}

	public class SRawNode extends SLeafNode {

		public SRawNode(SChildBearingNode parent, YamlDocument doc, int indent,
				int start, int end) {
			super(parent, doc, indent, start, end);
		}

		@Override
		public SNodeType getNodeType() {
			return SNodeType.RAW;
		}
	}

	public SRootNode parse() throws Exception {
		SRootNode root = new SRootNode(input.getDocument());
		SChildBearingNode parent = root;
		YamlLine line = input.peek();
		while (line != null && line.matches(SKIP_AT_START_OF_DOC, false)) {
			input.read();
			line = input.peek();
		}
		if (line != null && !line.matches(DOCUMENT_SEPERATOR)) {
			//document separator missing, create document implicitly
			parent = new SDocNode(root,0,0);
		}
		while (null != (line=input.read())) {
			int indent = line.getIndent();
			if (indent == -1) {
				createRawNode(parent, line);
			} else {
				parent = parseLine(parent, line, true);
			}
		}
		return root;
	}

	protected SChildBearingNode parseLine(SChildBearingNode parent, YamlLine line, boolean createRawNode) throws Exception {
		// A single line can carry an unbounded number of nested "- " sequence markers
		// (e.g. "- - - - ..."); walk them iteratively rather than recursing once per
		// marker, so a maliciously long line can't blow the stack.
		while (line.matches(SEQ_LINE)) {
			int currentIndent = line.getIndent();
			parent = dropToLevel(parent, (node) -> {
				int indent = node.getIndent();
				return indent < currentIndent || node.getNodeType() != SNodeType.SEQ && indent <= currentIndent;
			});
			parent = createSeqNode(parent, line);
			line = line.moveIndentMark(2); //parse from just after "- " for nested seq and key nodes
			createRawNode = false;
		}
		if (line.matches(DOCUMENT_SEPERATOR)) {
			parent = createDocNode(parent.getRoot(), line);
		} else if (line.matches(SIMPLE_KEY_LINE)) {
			int currentIndent = line.getIndent();
			parent = dropToLevel(parent, (node) -> node.getIndent() < currentIndent);
			parent = createKeyNode(parent, line);
		} else if (createRawNode) {
			createRawNode(parent, line);
		}
		return parent;
	}

	private SChildBearingNode dropToLevel(SChildBearingNode parent, Predicate<SNode> level) {
		while (parent.getNodeType() != SNodeType.DOC && parent.getSegment() != null && !level.test(parent)) {
			parent = parent.getParent();
		}
		return parent;
	}

	private SChildBearingNode createDocNode(SRootNode parent, YamlLine line) {
		int start = line.getStart();
		int end = line.getEnd();
		return new SDocNode(parent, start, end);
	}

	private SChildBearingNode createSeqNode(SChildBearingNode parent, YamlLine line) throws Exception {
		int indent = line.getIndent();
		int start = line.getStart() + line.getIndent(); //use + is okay because seq node never have 'indefined' indent
		int end = line.getEnd();
		return new SSeqNode(parent, line.getDocument(), indent, start, end);
	}

	private SChildBearingNode createKeyNode(SChildBearingNode parent, YamlLine line) throws Exception {
		int indent = line.getIndent();
		int start = line.getStart() + line.getIndent(); //use + is okay because key node never have 'indefined' indent
		int end = line.getEnd();
		return new SKeyNode(parent, line.getDocument(), indent, start, end);
	}

	private SRawNode createRawNode(SChildBearingNode parent, YamlLine line) {
		int indent = line.getIndent();
		int start = YamlIndentUtil.addToOffset(line.getStart(), indent);
		int end = line.getEnd();
		return new SRawNode(parent, line.getDocument(), indent, start, end);
	}


	public class SSeqNode extends SChildBearingNode {

		/**
		 * position of this in its parent. I.e. index is chosen such that
		 * parent.getChildren()[index] == this
		 */
		private int index;

		public SSeqNode(SChildBearingNode parent, YamlDocument doc, int indent, int start, int end) throws Exception {
			super(parent, doc, indent, start, end);
			this.index = parent.seqChildrenCount() - 1;
		}

		public int getIndex() {
			return index;
		}

		@Override
		public SNodeType getNodeType() {
			return SNodeType.SEQ;
		}

		public boolean isInValue(int offset) {
			int len = getNodeEnd() - getStart();
			//Careful, generally a seq node starts with a "- ". But... there's a special case
			// if the node text is only lenght 1. Thne its just a "-" alone (no space).
			int dashLen = len == 1 ? 1 : 2;
			return offset >= getStart()+dashLen
					&& offset <= getTreeEnd();
		}

		public String getTextWithoutChildren() {
			int end = getNodeEnd();
			Optional<SNode> child = getChildren().stream().findFirst();
			if (child.isPresent()) {
				end = Math.min(end, child.get().getStart());
			}
			return doc.textBetween(getStart(), end);
		}
	}

	public class SKeyNode extends SChildBearingNode {

		private int colonOffset;

		public SKeyNode(SChildBearingNode parent, YamlDocument doc, int indent, int start, int end) throws Exception {
			super(parent, doc, indent, start, end);
			int relativeColonOffset = doc.textBetween(start, end).indexOf(':');
			Assert.isLegal(relativeColonOffset >= 0);
			this.colonOffset = relativeColonOffset + start;
		}

		@Override
		public SNodeType getNodeType() {
			return SNodeType.KEY;
		}

		public String getKey() throws Exception {
			return doc.textBetween(getStart(), getColonOffset());
		}

		public String getSimpleValue() {
			return doc.textBetween(getColonOffset()+1, getNodeEnd());
		}

		/**
		 * Get the offset of the ':' character that separates the 'key' from the 'value' area.
		 * @return Absolute offset (from beginning of document).
		 */
		public int getColonOffset() {
			return colonOffset;
		}

		public boolean isInKey(int offset) throws Exception {
			return getStart() <= offset && offset <= getColonOffset();
		}

		public boolean isInValue(int offset) {
			return offset > getColonOffset() && offset <= getTreeEnd();
		}

		/**
		 * Gets the raw text of the 'stuff' assigned to the key in this node.
		 * This includes all the text starting from the ':' upto the very end of this node,
		 * including the text for this node's children (if any).
		 */
		public String getValueWithRelativeIndent() {
			int start = getColonOffset()+1;
			int end = getTreeEnd();
			String indentedText = StringUtil.trimEnd(doc.textBetween(start, end));
			int indent = getIndent();
			if (indent > 0) {
				return StringUtil.stripIndentation(indent, indentedText);
			}
			return indentedText;
		}


	}

	private Iterable<String> getKeyAliases(String key) {
		return keyAliases.getKeyAliases(key);
	}

}
