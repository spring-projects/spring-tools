/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.kotlin.syntax;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Argument;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ArrayValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.BooleanValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassDeclaration;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassKind;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ConcatValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Constant;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Function;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Import;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.NumberValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Parameter;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Range;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ReferenceValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.StringValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.TypeRef;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.UnsupportedValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Value;
import org.treesitter.TSInputEncoding;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSReader;
import org.treesitter.TSTree;
import org.treesitter.TSTreeCursor;
import org.treesitter.TreeSitterKotlin;

/**
 * Reads Kotlin source files with tree-sitter (the Kotlin grammar of {@code tree-sitter-kotlin}) into
 * {@link KotlinSyntax}. Nothing is resolved: this is syntax only, and it has to give the very same
 * result as the other readers do (see {@link KotlinSyntaxReaders}).
 * <p>
 * Things that are different with tree-sitter, and handled here:
 * <ul>
 * <li>The source is handed to the parser as UTF-16, so the offsets of the nodes are twice the
 * offsets into the Java string, whatever is in the source. The parser (not the JNI string
 * conversion) never sees anything else.</li>
 * <li>The grammar gets confused by comments in some places (a comment in front of an annotation
 * with arguments, for example, makes the annotation a loose expression), and comments end up as
 * children of whatever node was in front of them. Comments are of no interest here, except for
 * where doc comments are. So they are found with a small scanner first, and the parser gets the
 * source with all of them blanked out (which keeps all the offsets).</li>
 * <li>Syntax errors are nodes in the tree, which may contain declarations: they are searched, too.
 * A class that is not closed is no declaration in the tree, but its parts are recognized.</li>
 * <li>The grammar does not know {@code fun interface}: the {@code fun} is blanked out before
 * parsing, too.</li>
 * </ul>
 * <p>
 * Known limits of the grammar (which {@code KotlinTreeSitterReaderTest} keeps track of): a class
 * that delegates with {@code by} and has a body (as in {@code class A : B by c { ... }}) reads the
 * body as a lambda that is passed to {@code c}, and identifiers with letters of some scripts (Chinese,
 * for example) are not understood.
 *
 * @author Martin Lippert
 */
public class KotlinTreeSitterReader implements KotlinSyntaxReader {

	private static final Logger log = LoggerFactory.getLogger(KotlinTreeSitterReader.class);

	private static final int BUFFER_SIZE = 8192; // in bytes, has to be a multiple of 2

	private static final Pattern FUN_INTERFACE = Pattern.compile("(?<![\\p{L}\\p{N}_`])fun(?=\\s+interface(?![\\p{L}\\p{N}_]))");
	private static final Set<String> NUMBER_LITERALS = Set.of("integer_literal", "long_literal", "hex_literal", "bin_literal", "real_literal");

	/**
	 * Parsing is native and scales with the number of threads, asking the tree for its nodes does
	 * not (it gets slower with more than four threads at the same time), so only a few threads read
	 * trees at once.
	 */
	private static final int CONCURRENT_READS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));

	private final boolean skipFunctionBodies;
	private final Semaphore reads = new Semaphore(CONCURRENT_READS);
	private final Object lock = new Object();
	private TSLanguage language;
	private String[] symbolNames;
	private final Deque<TSParser> idleParsers = new ArrayDeque<>();

	/** a reader that does not parse the bodies of functions, which nothing needs yet */
	public KotlinTreeSitterReader() {
		this(true);
	}

	/**
	 * @param skipFunctionBodies whether the bodies of functions with a block are not parsed, which
	 * is much faster and has no effect on what is read
	 */
	public KotlinTreeSitterReader(boolean skipFunctionBodies) {
		this.skipFunctionBodies = skipFunctionBodies;
	}

	@Override
	public KotlinSyntax.File read(String fileName, String text) {
		TSParser parser = borrowParser();
		String[] names = symbolNames();
		boolean reusable = false;
		TSTree tree = null;

		try {
			// the parser does not see the comments, only the places where doc comments are are kept
			Scan scan = SourceScanner.scan(text, skipFunctionBodies);
			List<int[]> comments = scan.comments();
			Map<Integer, Integer> docComments = new HashMap<>();
			for (int[] comment : comments) {
				if (comment[1] - comment[0] >= 5 && text.startsWith("/**", comment[0]) && text.startsWith("*/", comment[1] - 2)) {
					docComments.put(comment[1], comment[0]);
				}
			}

			List<int[]> blanked = new ArrayList<>(comments);
			blanked.addAll(scan.bodies());
			tree = parse(parser, blankOut(blankOutFunInterface(text), blanked));
			reusable = true;
			reads.acquireUninterruptibly();
			try {
				return new Reader(text, tree.getRootNode(), names, docComments).read();
			}
			finally {
				reads.release();
			}
		}
		finally {
			if (tree != null) {
				tree.close();
			}
			returnParser(parser, reusable);
		}
	}

	/** the text without the comments, as long as it was: everything but the line breaks is replaced */
	static String blankOut(String text, List<int[]> comments) {
		char[] result = text.toCharArray();
		for (int[] comment : comments) {
			for (int i = Math.max(0, comment[0]); i < Math.min(result.length, comment[1]); i++) {
				if (result[i] != '\n' && result[i] != '\r') {
					result[i] = ' ';
				}
			}
		}
		return new String(result);
	}

	@Override
	public void dispose() {
		synchronized (lock) {
			idleParsers.forEach(TSParser::close);
			idleParsers.clear();
			language = null;
			symbolNames = null;
		}
	}

	private TSParser borrowParser() {
		synchronized (lock) {
			TSParser parser = idleParsers.poll();
			if (parser != null) {
				return parser;
			}
			if (language == null) {
				log.info("setting up the tree-sitter Kotlin parser");
				language = new TreeSitterKotlin();
				symbolNames = new String[language.symbolCount()];
				for (int i = 0; i < symbolNames.length; i++) {
					symbolNames[i] = language.symbolName(i);
				}
			}
			parser = new TSParser();
			if (!parser.setLanguage(language)) {
				parser.close();
				throw new IllegalStateException("the Kotlin grammar does not fit to the tree-sitter library");
			}
			return parser;
		}
	}

	private String[] symbolNames() {
		synchronized (lock) {
			return symbolNames;
		}
	}

	private void returnParser(TSParser parser, boolean reusable) {
		synchronized (lock) {
			if (reusable) {
				idleParsers.push(parser);
			}
			else {
				parser.close();
			}
		}
	}

	/**
	 * {@code fun interface} is not understood by the grammar. Without the {@code fun} it is an
	 * interface, and the text is as long as it was. Whatever is read from the source later, is
	 * read from the original text.
	 */
	static String blankOutFunInterface(String text) {
		Matcher matcher = FUN_INTERFACE.matcher(text);
		if (!matcher.find()) {
			return text;
		}

		StringBuilder result = new StringBuilder(text);
		do {
			result.replace(matcher.start(), matcher.end(), "   ");
		}
		while (matcher.find());
		return result.toString();
	}

	private static TSTree parse(TSParser parser, String text) {
		// offsets are in bytes, with two of them for every char of the string
		byte[] data = text.getBytes(StandardCharsets.UTF_16LE);

		TSReader reader = (buffer, offset, point) -> {
			if (offset < 0 || offset >= data.length) {
				return 0;
			}
			int count = Math.min(buffer.length, data.length - offset);
			System.arraycopy(data, offset, buffer, 0, count);
			return count;
		};

		TSTree tree = parser.parse(new byte[BUFFER_SIZE], null, reader, TSInputEncoding.TSInputEncodingUTF16LE);
		if (tree == null) {
			throw new IllegalStateException("tree-sitter did not return a syntax tree");
		}
		return tree;
	}

	/** what the scanner found: where the comments are, and where the bodies of functions are (what is between their braces) */
	record Scan(List<int[]> comments, List<int[]> bodies) {
	}

	/**
	 * Finds the comments of a Kotlin source: line comments up to the end of the line, and block
	 * comments, which can be nested. What looks like a comment in a string, a raw string, a character
	 * or a name in backticks is none. The templates of a string are code again, with comments and
	 * strings of their own.
	 * <p>
	 * It can also find the bodies of the functions that have a block as their body, from the keyword
	 * {@code fun} on: after the parameters and the type that is returned, the next thing that is no
	 * comment is the opening brace, or it is not a body of that kind. This is for skipping what is of
	 * no interest, which saves most of the work of the parser. A function it is not sure about is
	 * left alone, which only costs time.
	 */
	static class SourceScanner {

		// where the scanner is in the declaration of a function
		private static final int NONE = 0;
		private static final int BEFORE_PARAMETERS = 1;
		private static final int IN_PARAMETERS = 2;
		private static final int AFTER_PARAMETERS = 3;
		private static final int IN_RETURN_TYPE = 4;

		static List<int[]> comments(String text) {
			return scan(text, false).comments();
		}

		static Scan scan(String text, boolean findBodies) {
			Scan scan = new Scan(new ArrayList<>(), new ArrayList<>());
			scanCode(text, 0, false, findBodies, scan);
			return scan;
		}

		/**
		 * @param inBlock whether this is the code of a template or of a body, which end with the closing brace
		 * @param findBodies whether the bodies of functions are looked for
		 * @return the position after the code
		 */
		private static int scanCode(String text, int start, boolean inBlock, boolean findBodies, Scan scan) {
			int length = text.length();
			int depth = 0;
			int i = start;

			int function = NONE;
			int parentheses = 0;
			int angles = 0;

			while (i < length) {
				char c = text.charAt(i);

				if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
					int end = i + 2;
					while (end < length && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
						end++;
					}
					scan.comments().add(new int[] { i, end });
					i = end;
				}
				else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
					int nesting = 1;
					int end = i + 2;
					while (end < length && nesting > 0) {
						if (text.startsWith("/*", end)) {
							nesting++;
							end += 2;
						}
						else if (text.startsWith("*/", end)) {
							nesting--;
							end += 2;
						}
						else {
							end++;
						}
					}
					scan.comments().add(new int[] { i, end });
					i = end;
				}
				else if (c == '"') {
					i = text.startsWith("\"\"\"", i) ? skipRawString(text, i + 3, scan) : skipString(text, i + 1, scan);
				}
				else if (c == '\'') {
					i = skipCharacter(text, i);
				}
				else if (c == '`') {
					int end = i + 1;
					while (end < length && text.charAt(end) != '`' && text.charAt(end) != '\n') {
						end++;
					}
					i = Math.min(length, end + 1);
				}
				else if (Character.isJavaIdentifierStart(c)) {
					int end = i + 1;
					while (end < length && Character.isJavaIdentifierPart(text.charAt(end))) {
						end++;
					}
					if (findBodies && end - i == 3 && text.startsWith("fun", i) && !isFunInterface(text, end)) {
						function = BEFORE_PARAMETERS;
						parentheses = 0;
						angles = 0;
					}
					i = end;
				}
				else if (c == '{') {
					if (function == AFTER_PARAMETERS || function == IN_RETURN_TYPE) {
						if (parentheses == 0 && angles == 0) {
							// the block that is the body of the function
							int end = scanCode(text, i + 1, true, false, scan);
							if (end <= length && end - 1 > i && text.charAt(end - 1) == '}') {
								scan.bodies().add(new int[] { i + 1, end - 1 });
							}
							function = NONE;
							i = end;
							continue;
						}
					}
					else if (function == BEFORE_PARAMETERS) {
						function = NONE;
					}
					depth++;
					i++;
				}
				else if (c == '}') {
					if (depth == 0) {
						// the end of what the function is in, not of a lambda in its parameters
						function = NONE;
					}
					if (inBlock && depth == 0) {
						return i + 1;
					}
					depth--;
					i++;
				}
				else {
					if (function != NONE) {
						function = nextFunctionState(text, i, c, function, parentheses, angles);
						if (c == '(') {
							parentheses++;
						}
						else if (c == ')') {
							parentheses--;
						}
						else if (c == '<' && (function == BEFORE_PARAMETERS || function == IN_RETURN_TYPE)) {
							angles++;
						}
						else if (c == '>' && angles > 0 && text.charAt(i - 1) != '-') {
							angles--;
						}
					}
					i++;
				}
			}
			return length;
		}

		/** @return where the scanner is in the declaration of a function after the given character */
		private static int nextFunctionState(String text, int position, char c, int function, int parentheses, int angles) {
			switch (c) {
			case '(':
				return function == BEFORE_PARAMETERS && parentheses == 0 ? IN_PARAMETERS : function;
			case ')':
				return function == IN_PARAMETERS && parentheses == 1 ? AFTER_PARAMETERS : function;
			case ':':
				return function == AFTER_PARAMETERS && parentheses == 0 ? IN_RETURN_TYPE : function;
			case '=':
				// an expression as the body, or a default value in the parameters
				return function == BEFORE_PARAMETERS || ((function == AFTER_PARAMETERS || function == IN_RETURN_TYPE) && parentheses == 0 && angles == 0)
						? NONE : function;
			case ';':
				return parentheses == 0 ? NONE : function;
			case '\n':
			case '\r':
				if (function == BEFORE_PARAMETERS) {
					return NONE;
				}
				if ((function == AFTER_PARAMETERS || function == IN_RETURN_TYPE) && parentheses == 0 && angles == 0) {
					return continuesDeclaration(text, position + 1) ? function : NONE;
				}
				return function;
			default:
				return function;
			}
		}

		/** whether what follows is still the declaration of the function: its body, or the type it returns, or the constraints of its type parameters */
		private static boolean continuesDeclaration(String text, int position) {
			int i = position;
			while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
				i++;
			}
			if (i >= text.length()) {
				return false;
			}
			char c = text.charAt(i);
			return c == '{' || c == '=' || c == ':' || text.startsWith("where", i) && (i + 5 >= text.length() || !Character.isJavaIdentifierPart(text.charAt(i + 5)));
		}

		/** whether the word after the fun is interface */
		private static boolean isFunInterface(String text, int position) {
			int i = position;
			while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
				i++;
			}
			return text.startsWith("interface", i) && (i + 9 >= text.length() || !Character.isJavaIdentifierPart(text.charAt(i + 9)));
		}

		/** @return the position after the string, a string that is not closed ends with the line */
		private static int skipString(String text, int start, Scan scan) {
			int length = text.length();
			int i = start;
			while (i < length) {
				char c = text.charAt(i);
				if (c == '\\') {
					i += 2;
				}
				else if (c == '"') {
					return i + 1;
				}
				else if (c == '\n' || c == '\r') {
					return i;
				}
				else if (c == '$' && i + 1 < length && text.charAt(i + 1) == '{') {
					i = scanCode(text, i + 2, true, false, scan);
				}
				else {
					i++;
				}
			}
			return length;
		}

		private static int skipRawString(String text, int start, Scan scan) {
			int length = text.length();
			int i = start;
			while (i < length) {
				char c = text.charAt(i);
				if (c == '"' && text.startsWith("\"\"\"", i)) {
					i += 3;
					while (i < length && text.charAt(i) == '"') {
						i++;
					}
					return i;
				}
				else if (c == '$' && i + 1 < length && text.charAt(i + 1) == '{') {
					i = scanCode(text, i + 2, true, false, scan);
				}
				else {
					i++;
				}
			}
			return length;
		}

		/** @return the position after the character literal, or after the quote if this is none */
		private static int skipCharacter(String text, int start) {
			int length = text.length();
			if (start + 1 >= length) {
				return length;
			}

			if (text.charAt(start + 1) == '\\') {
				for (int i = start + 2; i < Math.min(length, start + 10); i++) {
					if (text.charAt(i) == '\'') {
						return i + 1;
					}
				}
				return start + 1;
			}

			int content = Character.isHighSurrogate(text.charAt(start + 1)) ? start + 3 : start + 2;
			return content < length && text.charAt(content) == '\'' ? content + 1 : start + 1;
		}
	}

	/**
	 * A node of the syntax tree. Every question to tree-sitter is a call into native code, so what is
	 * asked is asked once: the type, the position and the children are kept.
	 */
	private static final class Node {

		private static final int CURSOR_FROM = 24; // finding the n-th child by its index costs time with the number of children

		private final TSNode node;
		private final String[] symbolNames;
		private String type;
		private int start = -1;
		private int end = -1;
		private int named = -1;
		private List<Node> children;
		private List<Node> significant;
		private List<Node> significantNamed;

		Node(TSNode node, String[] symbolNames) {
			this.node = node;
			this.symbolNames = symbolNames;
		}

		String type() {
			if (type == null) {
				int symbol = node.getSymbol();
				// error nodes have a symbol that is no part of the language
				type = symbol >= 0 && symbol < symbolNames.length ? symbolNames[symbol] : "ERROR";
			}
			return type;
		}

		/** the position in bytes */
		int start() {
			if (start < 0) {
				start = node.getStartByte();
			}
			return start;
		}

		int end() {
			if (end < 0) {
				end = node.getEndByte();
			}
			return end;
		}

		boolean named() {
			if (named < 0) {
				named = node.isNamed() ? 1 : 0;
			}
			return named == 1;
		}

		List<Node> children() {
			if (children == null) {
				int count = node.getChildCount();
				if (count == 0) {
					children = List.of();
				}
				else {
					List<Node> result = new ArrayList<>(count);
					if (count >= CURSOR_FROM) {
						try (TSTreeCursor cursor = new TSTreeCursor(node)) {
							if (cursor.gotoFirstChild()) {
								do {
									result.add(new Node(cursor.currentNode(), symbolNames));
								}
								while (cursor.gotoNextSibling());
							}
						}
					}
					else {
						for (int i = 0; i < count; i++) {
							result.add(new Node(node.getChild(i), symbolNames));
						}
					}
					children = result;
				}
			}
			return children;
		}

		/** the children that are not empty */
		List<Node> significant() {
			if (significant == null) {
				List<Node> result = new ArrayList<>();
				for (Node child : children()) {
					if (child.end() > child.start()) {
						result.add(child);
					}
				}
				significant = result;
			}
			return significant;
		}

		List<Node> significantNamed() {
			if (significantNamed == null) {
				List<Node> result = new ArrayList<>();
				for (Node child : significant()) {
					if (child.named()) {
						result.add(child);
					}
				}
				significantNamed = result;
			}
			return significantNamed;
		}
	}

	private static class Reader {

		private final String text;
		private final Node root;
		private final Map<Integer, Integer> docComments;

		Reader(String text, TSNode root, String[] symbolNames, Map<Integer, Integer> docComments) {
			this.text = text;
			this.root = new Node(root, symbolNames);
			this.docComments = docComments;
		}

		KotlinSyntax.File read() {
			FileContent content = new FileContent();
			collect(root, content);

			return new KotlinSyntax.File(content.packageName, content.packageRange, content.packageNameRange, content.imports, content.members.classes,
					content.members.constants, text.length());
		}

		private static class FileContent {
			String packageName = "";
			Range packageRange;
			Range packageNameRange;
			final List<Import> imports = new ArrayList<>();
			final Members members = new Members();
		}

		/** what is declared in a file or in the body of a class */
		private static class Members {
			final List<Function> functions = new ArrayList<>();
			final List<Constant> constants = new ArrayList<>();
			final List<ClassDeclaration> classes = new ArrayList<>();
		}

		private void collect(Node container, FileContent content) {
			for (Node child : children(container)) {
				switch (child.type()) {
				case "package_header" -> readPackage(child, content);
				case "import_list" -> {
					for (Node header : children(child)) {
						if (header.type().equals("import_header")) {
							readImport(header, content);
						}
					}
				}
				case "import_header" -> readImport(child, content);
				case "class_declaration", "object_declaration", "companion_object" -> {
					ClassDeclaration declaration = readClass(child);
					if (declaration != null) {
						content.members.classes.add(declaration);
					}
				}
				case "property_declaration" -> addConstant(child, content.members.constants);
				case "ERROR" -> collectFromError(child, content.members);
				default -> {
				}
				}
			}
		}

		private void readPackage(Node header, FileContent content) {
			if (content.packageRange != null) {
				return;
			}
			Node identifier = firstChild(header, "identifier");
			String name = identifier != null ? dottedName(identifier, "simple_identifier") : "";
			if (name.isEmpty()) {
				return;
			}
			content.packageName = name;
			content.packageRange = range(header);
			content.packageNameRange = range(identifier);
		}

		private void readImport(Node header, FileContent content) {
			Node identifier = firstChild(header, "identifier");
			String name = identifier != null ? dottedName(identifier, "simple_identifier") : "";
			if (name.isEmpty()) {
				return;
			}

			Node aliasNode = firstChild(header, "import_alias");
			Node aliasName = aliasNode != null ? firstChild(aliasNode, "type_identifier") : null;
			String alias = aliasName != null ? KotlinValueSyntax.unquote(textOf(aliasName)) : null;

			content.imports.add(new Import(name, alias, firstChild(header, "wildcard_import") != null, range(header)));
		}

		/** @return null if this is no declaration, but something that is left of one by a syntax error */
		private ClassDeclaration readClass(Node node) {
			ClassKind kind = classKind(node);
			Node nameNode = firstChild(node, "type_identifier");

			if (nameNode == null && kind != ClassKind.COMPANION) {
				return null;
			}

			String name = nameNode != null ? KotlinValueSyntax.unquote(textOf(nameNode)) : "Companion";
			Range declarationRange = declarationRange(node);
			Range nameRange = nameNode != null ? range(nameNode) : declarationRange;

			List<AnnotationUse> annotations = readAnnotations(firstChild(node, "modifiers"));

			List<TypeRef> supertypes = new ArrayList<>();
			for (Node specifier : children(node)) {
				addSupertype(specifier, supertypes);
			}

			Members members = new Members();
			for (Node child : children(node)) {
				if (child.type().equals("class_body") || child.type().equals("enum_class_body")) {
					collectMembers(child, members);
				}
			}

			return new ClassDeclaration(kind, name, declarationRange, nameRange, annotations, supertypes, members.functions, members.constants, members.classes);
		}

		private ClassKind classKind(Node node) {
			if (node.type().equals("object_declaration")) {
				return ClassKind.OBJECT;
			}
			if (node.type().equals("companion_object")) {
				return ClassKind.COMPANION;
			}

			boolean isInterface = false;
			boolean isEnum = false;
			boolean isAnnotation = false;

			for (Node child : children(node)) {
				switch (child.type()) {
				case "interface" -> isInterface = true;
				case "enum" -> isEnum = true;
				case "modifiers" -> {
					for (Node modifier : children(child)) {
						if (modifier.type().equals("class_modifier")) {
							String keyword = textOf(modifier).trim();
							isAnnotation |= keyword.equals("annotation");
							isEnum |= keyword.equals("enum");
						}
					}
				}
				default -> {
				}
				}
			}

			return isInterface ? ClassKind.INTERFACE : isAnnotation ? ClassKind.ANNOTATION : isEnum ? ClassKind.ENUM : ClassKind.CLASS;
		}

		/** the type of a supertype entry: it is given as it is, with a constructor call, or with a delegate */
		private Node supertypeOf(Node specifier) {
			Node entry = firstSignificantNamed(specifier);
			if (entry != null && (entry.type().equals("constructor_invocation") || entry.type().equals("explicit_delegation"))) {
				entry = firstSignificantNamed(entry);
			}
			return entry;
		}

		private void addSupertype(Node specifier, List<TypeRef> supertypes) {
			if (specifier.type().equals("delegation_specifier")) {
				Node type = supertypeOf(specifier);
				if (type != null) {
					supertypes.add(readType(type));
				}
			}
		}

		private void collectMembers(Node body, Members members) {
			for (Node member : children(body)) {
				switch (member.type()) {
				case "function_declaration" -> {
					Function function = readFunction(member);
					if (function != null) {
						members.functions.add(function);
					}
				}
				case "property_declaration" -> addConstant(member, members.constants);
				case "class_declaration", "object_declaration", "companion_object" -> {
					ClassDeclaration declaration = readClass(member);
					if (declaration != null) {
						members.classes.add(declaration);
					}
				}
				case "ERROR" -> collectFromError(member, members);
				default -> {
				}
				}
			}
		}

		/**
		 * What tree-sitter makes of a part of the source that it could not parse is a flat error
		 * node. A class whose body is not closed, for example, is no class declaration then, but
		 * the modifiers, the keyword, the name, and the members that were read behind it, all as
		 * children of the error node. The classes that are recognized this way get what follows them
		 * as their members, until the next declaration that is complete.
		 */
		private void collectFromError(Node error, Members into) {
			List<Node> parts = significant(error);
			RecoveredClass open = null;

			for (int i = 0; i < parts.size(); i++) {
				Node part = parts.get(i);
				Members target = open != null ? open.members : into;

				switch (part.type()) {
				case "function_declaration" -> {
					Function function = readFunction(part);
					if (function != null) {
						target.functions.add(function);
						if (open != null) {
							open.end = endOf(part);
						}
					}
				}
				case "property_declaration" -> {
					addConstant(part, target.constants);
					if (open != null) {
						open.end = endOf(part);
					}
				}
				case "class_declaration", "object_declaration", "companion_object" -> {
					// a declaration that is complete is not a member of one that is not closed
					finish(open, into);
					open = null;
					ClassDeclaration declaration = readClass(part);
					if (declaration != null) {
						into.classes.add(declaration);
					}
				}
				case "delegation_specifier" -> {
					if (open != null) {
						addSupertype(part, open.supertypes);
						open.end = endOf(part);
					}
				}
				case "ERROR" -> collectFromError(part, target);
				case "class", "interface", "object" -> {
					Node name = i + 1 < parts.size() ? parts.get(i + 1) : null;
					if (name != null && (name.type().equals("simple_identifier") || name.type().equals("type_identifier"))) {
						finish(open, into);
						open = startRecoveredClass(parts, i);
					}
				}
				default -> {
				}
				}
			}

			finish(open, into);
		}

		private static class RecoveredClass {
			ClassKind kind;
			Node name;
			Node modifiers;
			int start;
			int end;
			final List<TypeRef> supertypes = new ArrayList<>();
			final Members members = new Members();
		}

		/** the class that starts with the keyword at the given position of the parts of an error node */
		private RecoveredClass startRecoveredClass(List<Node> parts, int keywordIndex) {
			Node keyword = parts.get(keywordIndex);
			RecoveredClass recovered = new RecoveredClass();
			recovered.name = parts.get(keywordIndex + 1);

			int first = keywordIndex;
			Node before = keywordIndex > 0 ? parts.get(keywordIndex - 1) : null;
			boolean enumKeyword = before != null && before.type().equals("enum");
			boolean companionKeyword = before != null && before.type().equals("companion");
			if (enumKeyword || companionKeyword) {
				first--;
				before = first > 0 ? parts.get(first - 1) : null;
			}
			if (before != null && before.type().equals("modifiers")) {
				recovered.modifiers = before;
				first--;
			}

			boolean isAnnotation = recovered.modifiers != null && children(recovered.modifiers).stream()
					.anyMatch(m -> m.type().equals("class_modifier") && textOf(m).trim().equals("annotation"));

			switch (keyword.type()) {
			case "interface" -> recovered.kind = ClassKind.INTERFACE;
			case "object" -> recovered.kind = companionKeyword ? ClassKind.COMPANION : ClassKind.OBJECT;
			default -> recovered.kind = isAnnotation ? ClassKind.ANNOTATION : enumKeyword ? ClassKind.ENUM : ClassKind.CLASS;
			}

			recovered.start = startOf(parts.get(first));
			recovered.end = endOf(recovered.name);
			return recovered;
		}

		private void finish(RecoveredClass recovered, Members into) {
			if (recovered == null) {
				return;
			}

			Range declarationRange = declarationRange(offset(recovered.start), offset(recovered.end));
			into.classes.add(new ClassDeclaration(recovered.kind, KotlinValueSyntax.unquote(textOf(recovered.name)), declarationRange, range(recovered.name),
					readAnnotations(recovered.modifiers), recovered.supertypes, recovered.members.functions, recovered.members.constants,
					recovered.members.classes));
		}

		private void addConstant(Node property, List<Constant> constants) {
			Node modifiers = firstChild(property, "modifiers");
			if (modifiers == null || children(modifiers).stream().noneMatch(m -> m.type().equals("property_modifier") && textOf(m).trim().equals("const"))) {
				return;
			}

			Node variable = firstChild(property, "variable_declaration");
			Node nameNode = variable != null ? firstChild(variable, "simple_identifier") : null;
			Node initializer = afterToken(property, "=");

			if (nameNode != null && initializer != null) {
				constants.add(new Constant(KotlinValueSyntax.unquote(textOf(nameNode)), readValue(initializer)));
			}
		}

		private Function readFunction(Node node) {
			Node nameNode = firstChild(node, "simple_identifier");
			if (nameNode == null) {
				return null;
			}

			List<Parameter> parameters = new ArrayList<>();
			Node parameterList = firstChild(node, "function_value_parameters");
			if (parameterList != null) {
				for (Node parameter : children(parameterList)) {
					if (parameter.type().equals("parameter")) {
						Node parameterName = firstChild(parameter, "simple_identifier");
						Node parameterType = afterToken(parameter, ":");
						if (parameterName != null && parameterType != null) {
							parameters.add(new Parameter(KotlinValueSyntax.unquote(textOf(parameterName)), readType(parameterType)));
						}
					}
				}
			}

			TypeRef returnType = null;
			if (parameterList != null) {
				boolean afterParameters = false;
				for (Node child : children(node)) {
					if (isSame(child, parameterList)) {
						afterParameters = true;
					}
					else if (afterParameters && child.type().equals(":")) {
						Node type = nextSignificantNamed(node, child);
						if (type != null) {
							returnType = readType(type);
						}
						break;
					}
					else if (afterParameters && child.type().equals("function_body")) {
						break;
					}
				}
			}

			return new Function(KotlinValueSyntax.unquote(textOf(nameNode)), declarationRange(node), range(nameNode), readAnnotations(firstChild(node, "modifiers")), parameters, returnType);
		}

		// ---- annotations and values

		private List<AnnotationUse> readAnnotations(Node modifiers) {
			List<AnnotationUse> annotations = new ArrayList<>();
			if (modifiers != null) {
				for (Node modifier : children(modifiers)) {
					if (modifier.type().equals("annotation")) {
						AnnotationUse annotation = readAnnotation(modifier);
						if (annotation != null) {
							annotations.add(annotation);
						}
					}
				}
			}
			return annotations;
		}

		private AnnotationUse readAnnotation(Node node) {
			String useSiteTarget = null;
			Node target = firstChild(node, "use_site_target");
			if (target != null) {
				useSiteTarget = textOf(target).replace(":", "").trim();
			}

			Node invocation = firstChild(node, "constructor_invocation");
			Node type = invocation != null ? firstChild(invocation, "user_type") : firstChild(node, "user_type");
			if (type == null) {
				return null;
			}

			List<Argument> arguments = new ArrayList<>();
			Node argumentList = invocation != null ? firstChild(invocation, "value_arguments") : null;
			if (argumentList != null) {
				for (Node argument : children(argumentList)) {
					if (argument.type().equals("value_argument")) {
						readArgument(argument, arguments);
					}
				}
			}

			return new AnnotationUse(userTypeName(type), useSiteTarget, arguments, range(node), range(type));
		}

		private void readArgument(Node node, List<Argument> arguments) {
			Node expression = argumentExpression(node);
			if (expression == null) {
				return;
			}

			String name = null;
			List<Node> parts = significant(node);
			if (parts.size() >= 3 && parts.get(0).type().equals("simple_identifier") && children(node).stream().anyMatch(c -> c.type().equals("="))) {
				name = KotlinValueSyntax.unquote(textOf(parts.get(0)));
			}

			arguments.add(new Argument(name, readValue(expression), range(node), range(expression)));
		}

		/**
		 * The expression of an argument, which is what is left after the name and the equals sign.
		 * It can be a token that is not a named node, like {@code null} or {@code true}.
		 */
		private Node argumentExpression(Node argument) {
			List<Node> parts = significant(argument);
			for (int i = 0; i < parts.size() - 1; i++) {
				if (parts.get(i).type().equals("=")) {
					return parts.get(i + 1);
				}
			}
			return parts.isEmpty() || parts.get(parts.size() - 1).type().equals("*") ? null : parts.get(parts.size() - 1);
		}

		private Value readValue(Node node) {
			String type = node.type();

			if (type.equals("parenthesized_expression")) {
				List<Node> inner = significantNamed(node);
				if (inner.size() == 1) {
					return readValue(inner.get(0));
				}
			}
			else if (type.equals("string_literal")) {
				return readTemplate(node);
			}
			else if (type.equals("additive_expression")) {
				Value concat = readConcatenation(node);
				if (concat != null) {
					return concat;
				}
			}
			else if (NUMBER_LITERALS.contains(type)) {
				return new NumberValue(textOf(node));
			}
			else if (type.equals("boolean_literal") || type.equals("true") || type.equals("false")) {
				return new BooleanValue(Boolean.parseBoolean(textOf(node).trim()));
			}
			else if (type.equals("null") || type.equals("character_literal")) {
				return new UnsupportedValue(textOf(node));
			}
			else if (type.equals("collection_literal")) {
				List<Value> values = new ArrayList<>();
				List<Range> ranges = new ArrayList<>();
				for (Node inner : significantNamed(node)) {
					values.add(readValue(inner));
					ranges.add(range(inner));
				}
				return new ArrayValue(values, ranges);
			}
			else if (type.equals("call_expression")) {
				Value array = readArrayFunction(node);
				if (array != null) {
					return array;
				}
			}
			else if (type.equals("callable_reference") || type.equals("navigation_expression")) {
				ClassValue classLiteral = readClassLiteral(node);
				if (classLiteral != null) {
					return classLiteral;
				}
			}

			// what is not understood: a name that is written in dots is a reference, the rest keeps its text
			String source = textOf(node);
			if (node.children().isEmpty() && !source.isEmpty() && Character.isDigit(source.charAt(0))) {
				return new NumberValue(source);
			}
			return KotlinValueSyntax.DOTTED_NAME.matcher(source).matches() ? new ReferenceValue(source) : new UnsupportedValue(source);
		}

		/** @return null if this is no concatenation with a plus */
		private Value readConcatenation(Node node) {
			List<Node> parts = significant(node);
			int operator = -1;
			for (int i = 0; i < parts.size(); i++) {
				if (parts.get(i).type().equals("+")) {
					operator = i;
				}
			}
			if (operator < 1 || operator != parts.size() - 2 || !parts.get(0).named() || !parts.get(parts.size() - 1).named()) {
				return null;
			}

			List<Value> values = new ArrayList<>();
			addParts(readValue(parts.get(0)), values);
			addParts(readValue(parts.get(parts.size() - 1)), values);
			return new ConcatValue(values);
		}

		private static void addParts(Value value, List<Value> parts) {
			if (value instanceof ConcatValue concat) {
				parts.addAll(concat.parts());
			}
			else {
				parts.add(value);
			}
		}

		/** @return null if this is no call of a function that creates an array */
		private Value readArrayFunction(Node call) {
			List<Node> parts = significantNamed(call);
			if (parts.size() < 2 || !parts.get(0).type().equals("simple_identifier") || !KotlinValueSyntax.ARRAY_FUNCTIONS.contains(textOf(parts.get(0)))) {
				return null;
			}

			Node suffix = firstChild(call, "call_suffix");
			Node arguments = suffix != null ? firstChild(suffix, "value_arguments") : null;
			if (arguments == null) {
				return null;
			}

			List<Value> values = new ArrayList<>();
			List<Range> ranges = new ArrayList<>();
			for (Node argument : children(arguments)) {
				if (argument.type().equals("value_argument")) {
					Node expression = argumentExpression(argument);
					if (expression != null) {
						values.add(readValue(expression));
						ranges.add(range(expression));
					}
				}
			}
			return new ArrayValue(values, ranges);
		}

		/** @return null if this is no class literal, like {@code Foo::class} */
		private ClassValue readClassLiteral(Node node) {
			List<Node> parts = significant(node);
			if (parts.size() < 2) {
				return null;
			}

			if (node.type().equals("callable_reference")) {
				Node last = parts.get(parts.size() - 1);
				Node operator = parts.get(parts.size() - 2);
				if (last.type().equals("class") && operator.type().equals("::") && parts.size() > 2) {
					return new ClassValue(text.substring(offset(startOf(node)), offset(startOf(operator))).trim());
				}
				return null;
			}

			Node suffix = parts.get(parts.size() - 1);
			if (suffix.type().equals("navigation_suffix")) {
				List<Node> suffixParts = significant(suffix);
				if (suffixParts.size() == 2 && suffixParts.get(0).type().equals("::") && suffixParts.get(1).type().equals("class")) {
					return new ClassValue(text.substring(offset(startOf(node)), offset(startOf(suffix))).trim());
				}
			}
			return null;
		}

		/** a string, which is put together from text and the values of the templates in it */
		private Value readTemplate(Node literal) {
			boolean raw = textOf(literal).startsWith("\"\"\"");

			List<Value> parts = new ArrayList<>();
			StringBuilder content = new StringBuilder();
			boolean hasTemplates = false;

			List<Node> entries = children(literal);
			for (int i = 0; i < entries.size(); i++) {
				Node entry = entries.get(i);

				switch (entry.type()) {
				case "string_content" -> content.append(raw ? textOf(entry) : KotlinValueSyntax.unescape(textOf(entry)));
				case "interpolated_expression" -> {
					List<Node> expression = significantNamed(entry);
					if (expression.isEmpty()) {
						return new UnsupportedValue(textOf(literal));
					}
					hasTemplates = true;
					flush(content, parts);
					parts.add(readValue(expression.get(0)));
				}
				case "interpolated_identifier" -> {
					hasTemplates = true;
					flush(content, parts);
					parts.add(new ReferenceValue(textOf(entry)));
				}
				case "$" -> {
					// a dollar sign that does not start a template
					if (i + 1 >= entries.size() || !entries.get(i + 1).type().equals("interpolated_identifier")) {
						content.append('$');
					}
				}
				case "\"", "\"\"\"", "${", "}" -> {
				}
				default -> {
					if (entry.type().contains("escape")) {
						content.append(KotlinValueSyntax.unescape(textOf(entry)));
					}
					else if (width(entry) > 0) {
						return new UnsupportedValue(textOf(literal));
					}
				}
				}
			}

			if (!hasTemplates) {
				return new StringValue(content.toString());
			}
			flush(content, parts);
			return new ConcatValue(parts);
		}

		private static void flush(StringBuilder content, List<Value> parts) {
			if (content.length() > 0) {
				parts.add(new StringValue(content.toString()));
				content.setLength(0);
			}
		}

		// ---- types

		private TypeRef readType(Node node) {
			boolean nullable = false;
			Node element = node;
			while (element.type().equals("nullable_type")) {
				nullable = true;
				Node inner = firstSignificantNamed(element);
				if (inner == null) {
					break;
				}
				element = inner;
			}

			if (element.type().equals("user_type")) {
				List<TypeRef> arguments = new ArrayList<>();
				Node typeArguments = firstChild(element, "type_arguments");
				if (typeArguments != null) {
					for (Node projection : children(typeArguments)) {
						if (projection.type().equals("type_projection")) {
							arguments.add(readTypeArgument(projection));
						}
					}
				}
				return new TypeRef(userTypeName(element), nullable, arguments, range(node));
			}

			return new TypeRef(textOf(node), nullable, List.of(), range(node));
		}

		private TypeRef readTypeArgument(Node projection) {
			Node type = null;
			for (Node child : significantNamed(projection)) {
				if (!child.type().equals("type_projection_modifiers")) {
					type = child;
				}
			}
			return type != null ? readType(type) : new TypeRef("*", false, List.of(), range(projection));
		}

		/** the name of a type as it is written, without type arguments: {@code a.b.C} */
		private String userTypeName(Node userType) {
			StringBuilder name = new StringBuilder();
			for (Node child : children(userType)) {
				if (child.type().equals("type_identifier")) {
					if (name.length() > 0) {
						name.append('.');
					}
					name.append(KotlinValueSyntax.unquote(textOf(child)));
				}
			}
			return name.toString();
		}

		private String dottedName(Node node, String partType) {
			StringBuilder name = new StringBuilder();
			for (Node child : children(node)) {
				if (child.type().equals(partType)) {
					if (name.length() > 0) {
						name.append('.');
					}
					name.append(KotlinValueSyntax.unquote(textOf(child)));
				}
			}
			return name.toString();
		}

		// ---- the tree

		private static List<Node> children(Node node) {
			return node.children();
		}

		private static Node firstChild(Node node, String type) {
			for (Node child : node.children()) {
				if (child.type().equals(type)) {
					return child;
				}
			}
			return null;
		}

		/** the children that are not empty (which are the leftovers of syntax errors) */
		private static List<Node> significant(Node node) {
			return node.significant();
		}

		private static List<Node> significantNamed(Node node) {
			return node.significantNamed();
		}

		private static Node firstSignificantNamed(Node node) {
			List<Node> named = significantNamed(node);
			return named.isEmpty() ? null : named.get(0);
		}

		/** the first named child after the (first) child of the given type */
		private static Node afterToken(Node node, String tokenType) {
			boolean found = false;
			for (Node child : significant(node)) {
				if (found && child.named()) {
					return child;
				}
				if (!found && child.type().equals(tokenType)) {
					found = true;
				}
			}
			return null;
		}

		private static Node nextSignificantNamed(Node parent, Node after) {
			boolean found = false;
			for (Node child : significant(parent)) {
				if (found && child.named()) {
					return child;
				}
				if (isSame(child, after)) {
					found = true;
				}
			}
			return null;
		}

		private static int width(Node node) {
			return node.end() - node.start();
		}

		private static boolean isSame(Node first, Node second) {
			return first == second;
		}

		// ---- positions

		/** a position in the text: tree-sitter counts bytes of UTF-16, which are two for every char */
		private static int offset(int byteOffset) {
			return byteOffset / 2;
		}

		private static int startOf(Node node) {
			return node.start();
		}

		private static int endOf(Node node) {
			return node.end();
		}

		private String textOf(Node node) {
			return text.substring(offset(startOf(node)), offset(endOf(node)));
		}

		private Range range(Node node) {
			int start = offset(startOf(node));
			return new Range(start, Math.max(0, offset(endOf(node)) - start));
		}

		/**
		 * The range of a declaration: it includes the doc comment in front of it, as the parser of
		 * the Kotlin compiler has it.
		 */
		private Range declarationRange(Node node) {
			return declarationRange(offset(startOf(node)), offset(endOf(node)));
		}

		private Range declarationRange(int start, int end) {
			int docStart = docCommentStart(start);
			return new Range(docStart, Math.max(0, end - docStart));
		}

		/** @return the start of the doc comment that ends right in front of the given offset, or the offset if there is none */
		private int docCommentStart(int start) {
			int end = start;
			while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
				end--;
			}
			Integer docStart = docComments.get(end);
			return docStart != null ? docStart : start;
		}
	}

}
