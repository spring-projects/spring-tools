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

import java.util.ArrayList;
import java.util.List;

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

import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.tree.KmpSyntaxNode;
import com.intellij.platform.syntax.tree.ParseKt;
import com.intellij.platform.syntax.tree.SyntaxNode;
import com.intellij.platform.syntax.util.language.SyntaxElementLanguageProvider;

import fleet.org.jetbrains.kotlin.kmp.KotlinLanguageKt;
import fleet.org.jetbrains.kotlin.kmp.lexer.KotlinLexer;
import fleet.org.jetbrains.kotlin.kmp.lexer.KtTokens;
import fleet.org.jetbrains.kotlin.kmp.parser.KotlinParser;
import fleet.org.jetbrains.kotlin.kmp.parser.KotlinParserKt;
import fleet.org.jetbrains.kotlin.kmp.parser.KtNodeTypes;
import kotlin.Unit;
import kotlin.sequences.SequencesKt;

/**
 * Reads Kotlin source files with the Kotlin parser that JetBrains wrote in Kotlin only
 * ({@code org.jetbrains:kotlin-syntax}, on top of {@code org.jetbrains:syntax-api}, the syntax
 * framework of the IntelliJ platform that does not need PSI) into {@link KotlinSyntax}. Nothing is
 * resolved: this is syntax only.
 * <p>
 * The syntax tree this parser creates has the shape of the PSI tree of the Kotlin compiler (the
 * same node types, the same grammar, the same binding of comments to declarations), but it is no
 * PSI: there are no classes with accessors for it, so what the PSI reader gets from those is
 * read from the nodes here. Offsets are offsets into the text that was given, whatever line
 * separators it uses.
 * <p>
 * The parser can create the bodies of functions lazily, which means they are not parsed unless
 * somebody asks for what is in them, which nothing does here (this is what PSI does, too). The
 * API of the parser is experimental.
 *
 * @author Martin Lippert
 */
public class KotlinSyntaxApiReader implements KotlinSyntaxReader {

	private static final SyntaxElementLanguageProvider NO_LANGUAGES = elementType -> SequencesKt.emptySequence();

	private final boolean skipFunctionBodies;

	/** a reader that does not parse the bodies of functions, which nothing needs yet */
	public KotlinSyntaxApiReader() {
		this(true);
	}

	/**
	 * @param skipFunctionBodies whether the bodies of functions are left unparsed, which is much
	 * faster and has no effect on what is read
	 */
	public KotlinSyntaxApiReader(boolean skipFunctionBodies) {
		this.skipFunctionBodies = skipFunctionBodies;
	}

	@Override
	public KotlinSyntax.File read(String fileName, String text) {
		// the parser works on text with '\n' as the only line separator, the ranges are mapped back
		OffsetMapper mapper = new OffsetMapper(text);
		KotlinParser parser = new KotlinParser(false, skipFunctionBodies);

		KmpSyntaxNode root = ParseKt.parse(mapper.normalizedText(), () -> new KotlinLexer(), builder -> {
			parser.parse(builder);
			return Unit.INSTANCE;
		}, KtTokens.INSTANCE.getWHITESPACES(), KtTokens.INSTANCE.getCOMMENTS(), KotlinLanguageKt.KotlinSyntaxLanguage, NO_LANGUAGES, null, null,
				ParseKt.defaultTokenizationPolicy(null), KotlinParserKt.getKotlinBindingPolicy());

		return new Reader(mapper, text.length(), root).read();
	}

	@Override
	public void dispose() {
		// nothing is held on to
	}

	/** the types of the nodes, by their index, which is what a node says its type is */
	private static final class Types {

		static final KtNodeTypes NODES = KtNodeTypes.INSTANCE;
		static final KtTokens TOKENS = KtTokens.INSTANCE;

		static final int PACKAGE_DIRECTIVE = NODES.getPACKAGE_DIRECTIVE().getIndex();
		static final int IMPORT_LIST = NODES.getIMPORT_LIST().getIndex();
		static final int IMPORT_DIRECTIVE = NODES.getIMPORT_DIRECTIVE().getIndex();
		static final int IMPORT_ALIAS = NODES.getIMPORT_ALIAS().getIndex();
		static final int CLASS = NODES.getCLASS().getIndex();
		static final int OBJECT_DECLARATION = NODES.getOBJECT_DECLARATION().getIndex();
		static final int CLASS_BODY = NODES.getCLASS_BODY().getIndex();
		static final int FUN = NODES.getFUN().getIndex();
		static final int PROPERTY = NODES.getPROPERTY().getIndex();
		static final int MODIFIER_LIST = NODES.getMODIFIER_LIST().getIndex();
		static final int ANNOTATION = NODES.getANNOTATION().getIndex();
		static final int ANNOTATION_ENTRY = NODES.getANNOTATION_ENTRY().getIndex();
		static final int ANNOTATION_TARGET = NODES.getANNOTATION_TARGET().getIndex();
		static final int CONSTRUCTOR_CALLEE = NODES.getCONSTRUCTOR_CALLEE().getIndex();
		static final int SUPER_TYPE_LIST = NODES.getSUPER_TYPE_LIST().getIndex();
		static final int SUPER_TYPE_ENTRY = NODES.getSUPER_TYPE_ENTRY().getIndex();
		static final int SUPER_TYPE_CALL_ENTRY = NODES.getSUPER_TYPE_CALL_ENTRY().getIndex();
		static final int DELEGATED_SUPER_TYPE_ENTRY = NODES.getDELEGATED_SUPER_TYPE_ENTRY().getIndex();
		static final int VALUE_PARAMETER_LIST = NODES.getVALUE_PARAMETER_LIST().getIndex();
		static final int VALUE_PARAMETER = NODES.getVALUE_PARAMETER().getIndex();
		static final int VALUE_ARGUMENT_LIST = NODES.getVALUE_ARGUMENT_LIST().getIndex();
		static final int VALUE_ARGUMENT = NODES.getVALUE_ARGUMENT().getIndex();
		static final int VALUE_ARGUMENT_NAME = NODES.getVALUE_ARGUMENT_NAME().getIndex();
		static final int TYPE_REFERENCE = NODES.getTYPE_REFERENCE().getIndex();
		static final int USER_TYPE = NODES.getUSER_TYPE().getIndex();
		static final int NULLABLE_TYPE = NODES.getNULLABLE_TYPE().getIndex();
		static final int FUNCTION_TYPE = NODES.getFUNCTION_TYPE().getIndex();
		static final int DYNAMIC_TYPE = NODES.getDYNAMIC_TYPE().getIndex();
		static final int INTERSECTION_TYPE = NODES.getINTERSECTION_TYPE().getIndex();
		static final int TYPE_ARGUMENT_LIST = NODES.getTYPE_ARGUMENT_LIST().getIndex();
		static final int TYPE_PROJECTION = NODES.getTYPE_PROJECTION().getIndex();
		static final int REFERENCE_EXPRESSION = NODES.getREFERENCE_EXPRESSION().getIndex();
		static final int DOT_QUALIFIED_EXPRESSION = NODES.getDOT_QUALIFIED_EXPRESSION().getIndex();
		static final int PARENTHESIZED = NODES.getPARENTHESIZED().getIndex();
		static final int BINARY_EXPRESSION = NODES.getBINARY_EXPRESSION().getIndex();
		static final int OPERATION_REFERENCE = NODES.getOPERATION_REFERENCE().getIndex();
		static final int STRING_TEMPLATE = NODES.getSTRING_TEMPLATE().getIndex();
		static final int LITERAL_STRING_TEMPLATE_ENTRY = NODES.getLITERAL_STRING_TEMPLATE_ENTRY().getIndex();
		static final int ESCAPE_STRING_TEMPLATE_ENTRY = NODES.getESCAPE_STRING_TEMPLATE_ENTRY().getIndex();
		static final int SHORT_STRING_TEMPLATE_ENTRY = NODES.getSHORT_STRING_TEMPLATE_ENTRY().getIndex();
		static final int LONG_STRING_TEMPLATE_ENTRY = NODES.getLONG_STRING_TEMPLATE_ENTRY().getIndex();
		static final int INTEGER_CONSTANT = NODES.getINTEGER_CONSTANT().getIndex();
		static final int FLOAT_CONSTANT = NODES.getFLOAT_CONSTANT().getIndex();
		static final int CHARACTER_CONSTANT = NODES.getCHARACTER_CONSTANT().getIndex();
		static final int BOOLEAN_CONSTANT = NODES.getBOOLEAN_CONSTANT().getIndex();
		static final int NULL = NODES.getNULL().getIndex();
		static final int CLASS_LITERAL_EXPRESSION = NODES.getCLASS_LITERAL_EXPRESSION().getIndex();
		static final int COLLECTION_LITERAL_EXPRESSION = NODES.getCOLLECTION_LITERAL_EXPRESSION().getIndex();
		static final int CALL_EXPRESSION = NODES.getCALL_EXPRESSION().getIndex();

		static final int IDENTIFIER = TOKENS.getIDENTIFIER().getIndex();
		static final int INTERFACE_KEYWORD = TOKENS.getINTERFACE_KEYWORD().getIndex();
		static final int ANNOTATION_MODIFIER = TOKENS.getANNOTATION_MODIFIER().getIndex();
		static final int ENUM_MODIFIER = TOKENS.getENUM_MODIFIER().getIndex();
		static final int COMPANION_MODIFIER = TOKENS.getCOMPANION_MODIFIER().getIndex();
		static final int CONST_MODIFIER = TOKENS.getCONST_MODIFIER().getIndex();
		static final int EQ = TOKENS.getEQ().getIndex();
		static final int MUL = TOKENS.getMUL().getIndex();
		static final int PLUS = TOKENS.getPLUS().getIndex();
		static final int LPAR = TOKENS.getLPAR().getIndex();
		static final int RPAR = TOKENS.getRPAR().getIndex();
		static final int LBRACKET = TOKENS.getLBRACKET().getIndex();
		static final int RBRACKET = TOKENS.getRBRACKET().getIndex();
		static final int COMMA = TOKENS.getCOMMA().getIndex();
		static final int COLONCOLON = TOKENS.getCOLONCOLON().getIndex();

		static final int WHITE_SPACE = TOKENS.getWHITE_SPACE().getIndex();
		static final int EOL_COMMENT = TOKENS.getEOL_COMMENT().getIndex();
		static final int BLOCK_COMMENT = TOKENS.getBLOCK_COMMENT().getIndex();
		static final int DOC_COMMENT = TOKENS.getDOC_COMMENT().getIndex();
		static final int SHEBANG_COMMENT = TOKENS.getSHEBANG_COMMENT().getIndex();
	}

	private static class Reader {

		private final OffsetMapper mapper;
		private final String text; // the text the parser read, with the line separators the parser needs
		private final int length; // of the original text
		private final SyntaxNode root;

		Reader(OffsetMapper mapper, int length, SyntaxNode root) {
			this.mapper = mapper;
			this.text = mapper.normalizedText();
			this.length = length;
			this.root = root;
		}

		KotlinSyntax.File read() {
			String packageName = "";
			Range packageRange = null;
			Range packageNameRange = null;
			List<Import> imports = new ArrayList<>();
			List<ClassDeclaration> classes = new ArrayList<>();
			List<Constant> constants = new ArrayList<>();

			for (SyntaxNode child : children(root)) {
				int type = type(child);

				if (type == Types.PACKAGE_DIRECTIVE) {
					SyntaxNode name = firstExpression(child);
					String qualified = name != null ? identifiers(name) : "";
					if (!qualified.isEmpty() && length(child) > 0) {
						packageName = qualified;
						packageRange = range(child);
						packageNameRange = range(name);
					}
				}
				else if (type == Types.IMPORT_LIST) {
					for (SyntaxNode directive : children(child)) {
						if (type(directive) == Types.IMPORT_DIRECTIVE) {
							readImport(directive, imports);
						}
					}
				}
				else if (type == Types.CLASS || type == Types.OBJECT_DECLARATION) {
					classes.add(readClass(child));
				}
				else if (type == Types.PROPERTY) {
					addConstant(child, constants);
				}
			}

			return new KotlinSyntax.File(packageName, packageRange, packageNameRange, imports, classes, constants, length);
		}

		private void readImport(SyntaxNode directive, List<Import> imports) {
			SyntaxNode name = firstExpression(directive);
			String qualified = name != null ? identifiers(name) : "";
			if (qualified.isEmpty()) {
				return;
			}

			String alias = null;
			boolean allUnder = false;
			for (SyntaxNode child : children(directive)) {
				if (type(child) == Types.IMPORT_ALIAS) {
					SyntaxNode identifier = firstChild(child, Types.IDENTIFIER);
					alias = identifier != null ? KotlinValueSyntax.unquote(textOf(identifier)) : null;
				}
				else if (type(child) == Types.MUL) {
					allUnder = true;
				}
			}
			imports.add(new Import(qualified, alias, allUnder, range(directive)));
		}

		private ClassDeclaration readClass(SyntaxNode declaration) {
			SyntaxNode modifiers = firstChild(declaration, Types.MODIFIER_LIST);

			ClassKind kind = ClassKind.CLASS;
			if (type(declaration) == Types.OBJECT_DECLARATION) {
				kind = hasModifier(modifiers, Types.COMPANION_MODIFIER) ? ClassKind.COMPANION : ClassKind.OBJECT;
			}
			else if (firstChild(declaration, Types.INTERFACE_KEYWORD) != null) {
				kind = ClassKind.INTERFACE;
			}
			else if (hasModifier(modifiers, Types.ANNOTATION_MODIFIER)) {
				kind = ClassKind.ANNOTATION;
			}
			else if (hasModifier(modifiers, Types.ENUM_MODIFIER)) {
				kind = ClassKind.ENUM;
			}

			SyntaxNode nameNode = firstChild(declaration, Types.IDENTIFIER);
			String name = nameNode != null ? KotlinValueSyntax.unquote(textOf(nameNode)) : "Companion";
			Range nameRange = nameNode != null ? range(nameNode) : range(declaration);

			List<TypeRef> supertypes = new ArrayList<>();
			SyntaxNode supertypeList = firstChild(declaration, Types.SUPER_TYPE_LIST);
			if (supertypeList != null) {
				for (SyntaxNode entry : children(supertypeList)) {
					SyntaxNode reference = typeReferenceOf(entry);
					if (reference != null) {
						supertypes.add(readType(reference));
					}
				}
			}

			List<Function> functions = new ArrayList<>();
			List<Constant> constants = new ArrayList<>();
			List<ClassDeclaration> nested = new ArrayList<>();

			SyntaxNode body = firstChild(declaration, Types.CLASS_BODY);
			if (body != null) {
				for (SyntaxNode member : children(body)) {
					int type = type(member);
					if (type == Types.FUN) {
						Function function = readFunction(member);
						if (function != null) {
							functions.add(function);
						}
					}
					else if (type == Types.PROPERTY) {
						addConstant(member, constants);
					}
					else if (type == Types.CLASS || type == Types.OBJECT_DECLARATION) {
						// the entries of an enum are no nested classes
						nested.add(readClass(member));
					}
				}
			}

			return new ClassDeclaration(kind, name, range(declaration), nameRange, readAnnotations(modifiers), supertypes, functions, constants, nested);
		}

		/** the type of a super type entry, which is written with a call of the constructor, as it is, or with a delegate */
		private SyntaxNode typeReferenceOf(SyntaxNode entry) {
			int type = type(entry);
			if (type == Types.SUPER_TYPE_CALL_ENTRY) {
				SyntaxNode callee = firstChild(entry, Types.CONSTRUCTOR_CALLEE);
				return callee != null ? firstChild(callee, Types.TYPE_REFERENCE) : null;
			}
			else if (type == Types.SUPER_TYPE_ENTRY || type == Types.DELEGATED_SUPER_TYPE_ENTRY) {
				return firstChild(entry, Types.TYPE_REFERENCE);
			}
			return null;
		}

		private void addConstant(SyntaxNode property, List<Constant> constants) {
			if (!hasModifier(firstChild(property, Types.MODIFIER_LIST), Types.CONST_MODIFIER)) {
				return;
			}

			SyntaxNode name = firstChild(property, Types.IDENTIFIER);
			SyntaxNode initializer = afterToken(property, Types.EQ);
			if (name != null && initializer != null) {
				constants.add(new Constant(KotlinValueSyntax.unquote(textOf(name)), readValue(initializer)));
			}
		}

		private Function readFunction(SyntaxNode function) {
			SyntaxNode name = firstChild(function, Types.IDENTIFIER);
			if (name == null) {
				return null;
			}

			List<Parameter> parameters = new ArrayList<>();
			SyntaxNode parameterList = firstChild(function, Types.VALUE_PARAMETER_LIST);
			if (parameterList != null) {
				for (SyntaxNode parameter : children(parameterList)) {
					if (type(parameter) == Types.VALUE_PARAMETER) {
						SyntaxNode parameterName = firstChild(parameter, Types.IDENTIFIER);
						SyntaxNode parameterType = firstChild(parameter, Types.TYPE_REFERENCE);
						if (parameterType != null) {
							parameters.add(new Parameter(parameterName != null ? KotlinValueSyntax.unquote(textOf(parameterName)) : null, readType(parameterType)));
						}
					}
				}
			}

			// the type that is returned is the one behind the parameters, the one in front of the name is the receiver
			TypeRef returnType = null;
			boolean afterParameters = false;
			for (SyntaxNode child : children(function)) {
				if (parameterList != null && child.equals(parameterList)) {
					afterParameters = true;
				}
				else if (afterParameters && type(child) == Types.TYPE_REFERENCE) {
					returnType = readType(child);
					break;
				}
			}

			return new Function(KotlinValueSyntax.unquote(textOf(name)), range(function), range(name), readAnnotations(firstChild(function, Types.MODIFIER_LIST)), parameters,
					returnType);
		}

		// ---- annotations and values

		private List<AnnotationUse> readAnnotations(SyntaxNode modifiers) {
			List<AnnotationUse> annotations = new ArrayList<>();
			if (modifiers != null) {
				for (SyntaxNode child : children(modifiers)) {
					if (type(child) == Types.ANNOTATION_ENTRY) {
						annotations.add(readAnnotation(child));
					}
					else if (type(child) == Types.ANNOTATION) {
						// several annotations in brackets after one at sign
						for (SyntaxNode entry : children(child)) {
							if (type(entry) == Types.ANNOTATION_ENTRY) {
								annotations.add(readAnnotation(entry));
							}
						}
					}
				}
			}
			return annotations;
		}

		private AnnotationUse readAnnotation(SyntaxNode entry) {
			SyntaxNode callee = firstChild(entry, Types.CONSTRUCTOR_CALLEE);
			SyntaxNode reference = callee != null ? firstChild(callee, Types.TYPE_REFERENCE) : null;
			TypeRef type = reference != null ? readType(reference) : new TypeRef("", false, List.of(), range(entry));

			SyntaxNode target = firstChild(entry, Types.ANNOTATION_TARGET);
			String useSiteTarget = target != null ? textOf(target) : null;

			List<Argument> arguments = new ArrayList<>();
			SyntaxNode argumentList = firstChild(entry, Types.VALUE_ARGUMENT_LIST);
			if (argumentList != null) {
				for (SyntaxNode argument : children(argumentList)) {
					if (type(argument) == Types.VALUE_ARGUMENT) {
						readArgument(argument, arguments);
					}
				}
			}

			return new AnnotationUse(type.name(), useSiteTarget, arguments, range(entry), type.range());
		}

		private void readArgument(SyntaxNode argument, List<Argument> arguments) {
			String name = null;
			SyntaxNode expression = null;

			for (SyntaxNode child : children(argument)) {
				int type = type(child);
				if (type == Types.VALUE_ARGUMENT_NAME) {
					name = KotlinValueSyntax.unquote(identifiers(child));
				}
				else if (type != Types.EQ && type != Types.MUL) {
					expression = child;
				}
			}

			if (expression != null) {
				arguments.add(new Argument(name, readValue(expression), range(argument), range(expression)));
			}
		}

		private Value readValue(SyntaxNode expression) {
			int type = type(expression);

			if (type == Types.PARENTHESIZED) {
				SyntaxNode inner = firstExpression(expression);
				if (inner != null) {
					return readValue(inner);
				}
			}
			else if (type == Types.STRING_TEMPLATE) {
				return readTemplate(expression);
			}
			else if (type == Types.BINARY_EXPRESSION) {
				Value concatenation = readConcatenation(expression);
				if (concatenation != null) {
					return concatenation;
				}
			}
			else if (type == Types.INTEGER_CONSTANT || type == Types.FLOAT_CONSTANT || type == Types.CHARACTER_CONSTANT || type == Types.BOOLEAN_CONSTANT
					|| type == Types.NULL) {
				String constant = textOf(expression);
				if ("true".equals(constant) || "false".equals(constant)) {
					return new BooleanValue(Boolean.parseBoolean(constant));
				}
				else if (!constant.isEmpty() && Character.isDigit(constant.charAt(0))) {
					return new NumberValue(constant);
				}
				return new UnsupportedValue(constant);
			}
			else if (type == Types.CLASS_LITERAL_EXPRESSION) {
				SyntaxNode receiver = firstExpression(expression);
				if (receiver != null) {
					return new ClassValue(textOf(receiver));
				}
			}
			else if (type == Types.COLLECTION_LITERAL_EXPRESSION) {
				List<Value> values = new ArrayList<>();
				List<Range> ranges = new ArrayList<>();
				for (SyntaxNode inner : children(expression)) {
					int innerType = type(inner);
					if (innerType != Types.LBRACKET && innerType != Types.RBRACKET && innerType != Types.COMMA) {
						values.add(readValue(inner));
						ranges.add(range(inner));
					}
				}
				return new ArrayValue(values, ranges);
			}
			else if (type == Types.CALL_EXPRESSION) {
				Value array = readArrayFunction(expression);
				if (array != null) {
					return array;
				}
			}

			String source = textOf(expression);
			return KotlinValueSyntax.DOTTED_NAME.matcher(source).matches() ? new ReferenceValue(source) : new UnsupportedValue(source);
		}

		/** @return null if this is no concatenation with a plus */
		private Value readConcatenation(SyntaxNode binary) {
			List<SyntaxNode> parts = children(binary);
			if (parts.size() != 3 || type(parts.get(1)) != Types.OPERATION_REFERENCE || firstChild(parts.get(1), Types.PLUS) == null) {
				return null;
			}

			List<Value> values = new ArrayList<>();
			addParts(readValue(parts.get(0)), values);
			addParts(readValue(parts.get(2)), values);
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
		private Value readArrayFunction(SyntaxNode call) {
			SyntaxNode callee = firstExpression(call);
			if (callee == null || type(callee) != Types.REFERENCE_EXPRESSION || !KotlinValueSyntax.ARRAY_FUNCTIONS.contains(textOf(callee))) {
				return null;
			}

			List<Value> values = new ArrayList<>();
			List<Range> ranges = new ArrayList<>();
			SyntaxNode arguments = firstChild(call, Types.VALUE_ARGUMENT_LIST);
			if (arguments != null) {
				for (SyntaxNode argument : children(arguments)) {
					if (type(argument) == Types.VALUE_ARGUMENT) {
						SyntaxNode expression = null;
						for (SyntaxNode child : children(argument)) {
							int type = type(child);
							if (type != Types.VALUE_ARGUMENT_NAME && type != Types.EQ && type != Types.MUL) {
								expression = child;
							}
						}
						if (expression != null) {
							values.add(readValue(expression));
							ranges.add(range(expression));
						}
					}
				}
			}
			return new ArrayValue(values, ranges);
		}

		/** a string, which is put together from text and the values of the templates in it */
		private Value readTemplate(SyntaxNode template) {
			List<Value> parts = new ArrayList<>();
			StringBuilder content = new StringBuilder();
			boolean hasTemplates = false;

			for (SyntaxNode entry : children(template)) {
				int type = type(entry);

				if (type == Types.LITERAL_STRING_TEMPLATE_ENTRY) {
					content.append(textOf(entry));
				}
				else if (type == Types.ESCAPE_STRING_TEMPLATE_ENTRY) {
					content.append(KotlinValueSyntax.unescape(textOf(entry)));
				}
				else if (type == Types.SHORT_STRING_TEMPLATE_ENTRY || type == Types.LONG_STRING_TEMPLATE_ENTRY) {
					SyntaxNode expression = firstExpression(entry);
					if (expression == null) {
						return new UnsupportedValue(textOf(template));
					}
					hasTemplates = true;
					if (content.length() > 0) {
						parts.add(new StringValue(content.toString()));
						content.setLength(0);
					}
					parts.add(readValue(expression));
				}
				else if (!isQuote(entry)) {
					return new UnsupportedValue(textOf(template));
				}
			}

			if (!hasTemplates) {
				return new StringValue(content.toString());
			}
			if (content.length() > 0) {
				parts.add(new StringValue(content.toString()));
			}
			return new ConcatValue(parts);
		}

		/** the quotes around a string are nodes of their own */
		private boolean isQuote(SyntaxNode node) {
			return node.firstChild() == null && ("\"".equals(textOf(node)) || "\"\"\"".equals(textOf(node)));
		}

		// ---- types

		private TypeRef readType(SyntaxNode reference) {
			boolean nullable = false;
			SyntaxNode element = typeElement(reference);
			while (element != null && type(element) == Types.NULLABLE_TYPE) {
				nullable = true;
				element = typeElement(element);
			}

			if (element != null && type(element) == Types.USER_TYPE) {
				List<TypeRef> arguments = new ArrayList<>();
				SyntaxNode argumentList = firstChild(element, Types.TYPE_ARGUMENT_LIST);
				if (argumentList != null) {
					for (SyntaxNode projection : children(argumentList)) {
						if (type(projection) == Types.TYPE_PROJECTION) {
							SyntaxNode argument = firstChild(projection, Types.TYPE_REFERENCE);
							arguments.add(argument != null ? readType(argument) : new TypeRef("*", false, List.of(), range(projection)));
						}
					}
				}
				return new TypeRef(userTypeName(element), nullable, arguments, range(reference));
			}

			return new TypeRef(textOf(reference), nullable, List.of(), range(reference));
		}

		/** the type itself inside of a type reference or a nullable type, in front of it can be annotations and modifiers */
		private SyntaxNode typeElement(SyntaxNode node) {
			for (SyntaxNode child : children(node)) {
				int type = type(child);
				if (type == Types.USER_TYPE || type == Types.NULLABLE_TYPE || type == Types.FUNCTION_TYPE || type == Types.DYNAMIC_TYPE
						|| type == Types.INTERSECTION_TYPE) {
					return child;
				}
			}
			return null;
		}

		/** the name of a type as it is written, without type arguments: {@code a.b.C} */
		private String userTypeName(SyntaxNode userType) {
			String qualifier = null;
			String name = "";
			for (SyntaxNode child : children(userType)) {
				if (type(child) == Types.USER_TYPE) {
					qualifier = userTypeName(child);
				}
				else if (type(child) == Types.REFERENCE_EXPRESSION) {
					name = KotlinValueSyntax.unquote(textOf(child));
				}
			}
			return qualifier != null ? qualifier + "." + name : name;
		}

		// ---- the tree

		private static int type(SyntaxNode node) {
			return node.getType().getIndex();
		}

		/** the children that are no whitespace and no comments */
		private static List<SyntaxNode> children(SyntaxNode node) {
			List<SyntaxNode> children = new ArrayList<>();
			for (SyntaxNode child = node.firstChild(); child != null; child = child.nextSibling()) {
				if (!isTrivia(type(child))) {
					children.add(child);
				}
			}
			return children;
		}

		private static boolean isTrivia(int type) {
			return type == Types.WHITE_SPACE || type == Types.EOL_COMMENT || type == Types.BLOCK_COMMENT || type == Types.DOC_COMMENT
					|| type == Types.SHEBANG_COMMENT;
		}

		private static SyntaxNode firstChild(SyntaxNode node, int type) {
			for (SyntaxNode child = node.firstChild(); child != null; child = child.nextSibling()) {
				if (type(child) == type) {
					return child;
				}
			}
			return null;
		}

		/**
		 * The first child that is an expression, which is what comes after the keyword of a package or
		 * an import, in the parentheses, or in front of the double colon of a class literal. The
		 * tokens around it have no children, an expression always has some.
		 */
		private static SyntaxNode firstExpression(SyntaxNode node) {
			for (SyntaxNode child : children(node)) {
				if (child.firstChild() != null) {
					return child;
				}
			}
			return null;
		}

		/** the first child behind the first child of the given type */
		private static SyntaxNode afterToken(SyntaxNode node, int tokenType) {
			boolean found = false;
			for (SyntaxNode child : children(node)) {
				if (found) {
					return child;
				}
				found = type(child) == tokenType;
			}
			return null;
		}

		private static boolean hasModifier(SyntaxNode modifiers, int modifier) {
			return modifiers != null && firstChild(modifiers, modifier) != null;
		}

		/** the names in a name that is written in dots, like a.b.C */
		private String identifiers(SyntaxNode node) {
			StringBuilder name = new StringBuilder();
			collectIdentifiers(node, name);
			return name.toString();
		}

		private void collectIdentifiers(SyntaxNode node, StringBuilder name) {
			if (type(node) == Types.IDENTIFIER) {
				if (name.length() > 0) {
					name.append('.');
				}
				name.append(KotlinValueSyntax.unquote(textOf(node)));
				return;
			}
			for (SyntaxNode child = node.firstChild(); child != null; child = child.nextSibling()) {
				collectIdentifiers(child, name);
			}
		}

		private static int length(SyntaxNode node) {
			return node.getEndOffset() - node.getStartOffset();
		}

		private String textOf(SyntaxNode node) {
			return text.substring(node.getStartOffset(), node.getEndOffset());
		}

		/** the range in the original text */
		private Range range(SyntaxNode node) {
			int start = mapper.originalStart(node.getStartOffset());
			int end = mapper.originalEnd(node.getEndOffset());
			return new Range(start, end - start);
		}
	}

}
