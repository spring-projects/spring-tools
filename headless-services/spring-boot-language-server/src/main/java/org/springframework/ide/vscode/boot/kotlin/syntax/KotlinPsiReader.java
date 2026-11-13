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
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtParenthesizedExpression;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtClassLiteralExpression;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression;
import org.jetbrains.kotlin.psi.KtConstantExpression;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtEnumEntry;
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtNullableType;
import org.jetbrains.kotlin.psi.KtObjectDeclaration;
import org.jetbrains.kotlin.psi.KtPackageDirective;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.psi.KtStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeProjection;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;
import org.jetbrains.kotlin.psi.KtValueArgument;
import org.jetbrains.kotlin.psi.ValueArgument;
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

/**
 * Reads Kotlin source files with the Kotlin compiler's parser (PSI, from
 * {@code kotlin-compiler-embeddable}) into {@link KotlinSyntax}. Nothing is resolved: this is
 * syntax only.
 * <p>
 * Setting up the parser environment is expensive, so it is done once, when the first file is
 * read.
 *
 * @author Martin Lippert
 */
public class KotlinPsiReader implements KotlinSyntaxReader {

	private static final Logger log = LoggerFactory.getLogger(KotlinPsiReader.class);

	private static final Pattern DOTTED_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*(\\.[\\p{L}_][\\p{L}\\p{N}_]*)*");
	private static final Set<String> ARRAY_FUNCTIONS = Set.of("arrayOf", "intArrayOf", "longArrayOf", "shortArrayOf", "byteArrayOf",
			"charArrayOf", "booleanArrayOf", "floatArrayOf", "doubleArrayOf", "listOf", "setOf");

	private final Object lock = new Object();
	private Disposable disposable;
	private KtPsiFactory psiFactory;

	/**
	 * Parses the given source.
	 *
	 * @param fileName the name of the file, only used to name the parsed file
	 * @param text the source text
	 */
	@Override
	public KotlinSyntax.File read(String fileName, String text) {
		OffsetMapper mapper = new OffsetMapper(text);
		String normalized = mapper.normalizedText();

		KtFile ktFile;
		synchronized (lock) {
			ktFile = psiFactory().createFile(fileName, normalized);
		}

		return new Reader(mapper, text.length()).read(ktFile);
	}

	@Override
	public void dispose() {
		synchronized (lock) {
			if (disposable != null) {
				Disposer.dispose(disposable);
				disposable = null;
				psiFactory = null;
			}
		}
	}

	private KtPsiFactory psiFactory() {
		synchronized (lock) {
			if (psiFactory == null) {
				log.info("setting up the Kotlin parser");
				disposable = Disposer.newDisposable("kotlin-psi-reader");

				CompilerConfiguration configuration = new CompilerConfiguration();
				configuration.put(CommonConfigurationKeys.MODULE_NAME, "kotlin-source");
				configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.Companion.getNONE());

				KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES);
				psiFactory = new KtPsiFactory(environment.getProject(), false);
			}
			return psiFactory;
		}
	}

	private static class Reader {

		private final OffsetMapper mapper;
		private final int length;

		Reader(OffsetMapper mapper, int length) {
			this.mapper = mapper;
			this.length = length;
		}

		KotlinSyntax.File read(KtFile file) {
			String packageName = file.getPackageFqName().asString();
			Range packageRange = null;
			Range packageNameRange = null;

			KtPackageDirective packageDirective = file.getPackageDirective();
			if (packageDirective != null && !packageName.isEmpty() && packageDirective.getTextLength() > 0) {
				packageRange = range(packageDirective);
				KtExpression nameExpression = packageDirective.getPackageNameExpression();
				if (nameExpression != null) {
					packageNameRange = range(nameExpression);
				}
			}

			List<Import> imports = new ArrayList<>();
			for (KtImportDirective directive : file.getImportDirectives()) {
				if (directive.getImportedFqName() != null) {
					imports.add(new Import(directive.getImportedFqName().asString(), directive.getAliasName(), directive.isAllUnder(), range(directive)));
				}
			}

			List<ClassDeclaration> classes = new ArrayList<>();
			List<Constant> constants = new ArrayList<>();
			for (KtDeclaration declaration : file.getDeclarations()) {
				if (declaration instanceof KtClassOrObject classOrObject) {
					classes.add(readClass(classOrObject));
				}
				else if (declaration instanceof KtProperty property) {
					addConstant(property, constants);
				}
			}

			return new KotlinSyntax.File(packageName, packageRange, packageNameRange, imports, classes, constants, length);
		}

		private ClassDeclaration readClass(KtClassOrObject declaration) {
			ClassKind kind = ClassKind.CLASS;
			if (declaration instanceof KtObjectDeclaration object) {
				kind = object.isCompanion() ? ClassKind.COMPANION : ClassKind.OBJECT;
			}
			else if (declaration instanceof KtClass ktClass) {
				if (ktClass.isInterface()) {
					kind = ClassKind.INTERFACE;
				}
				else if (ktClass.isAnnotation()) {
					kind = ClassKind.ANNOTATION;
				}
				else if (ktClass.isEnum()) {
					kind = ClassKind.ENUM;
				}
			}

			String name = declaration.getName() != null ? declaration.getName() : "Companion";
			Range nameRange = declaration.getNameIdentifier() != null ? range(declaration.getNameIdentifier()) : range(declaration);

			List<AnnotationUse> annotations = new ArrayList<>();
			for (KtAnnotationEntry entry : declaration.getAnnotationEntries()) {
				annotations.add(readAnnotation(entry));
			}

			List<TypeRef> supertypes = new ArrayList<>();
			for (KtSuperTypeListEntry entry : declaration.getSuperTypeListEntries()) {
				if (entry.getTypeReference() != null) {
					supertypes.add(readType(entry.getTypeReference()));
				}
			}

			List<Function> functions = new ArrayList<>();
			List<Constant> constants = new ArrayList<>();
			List<ClassDeclaration> nested = new ArrayList<>();
			for (KtDeclaration member : declaration.getDeclarations()) {
				if (member instanceof KtNamedFunction function) {
					if (function.getName() != null) {
						functions.add(readFunction(function));
					}
				}
				else if (member instanceof KtProperty property) {
					addConstant(property, constants);
				}
				else if (member instanceof KtClassOrObject nestedClass && !(member instanceof KtEnumEntry)) {
					// the entries of an enum are the values of the enum, no classes declared in it
					nested.add(readClass(nestedClass));
				}
			}

			return new ClassDeclaration(kind, name, range(declaration), nameRange, annotations, supertypes, functions, constants, nested);
		}

		private void addConstant(KtProperty property, List<Constant> constants) {
			if (property.hasModifier(KtTokens.CONST_KEYWORD) && property.getName() != null && property.getInitializer() != null) {
				constants.add(new Constant(property.getName(), readValue(property.getInitializer())));
			}
		}

		private Function readFunction(KtNamedFunction function) {
			List<AnnotationUse> annotations = new ArrayList<>();
			for (KtAnnotationEntry entry : function.getAnnotationEntries()) {
				annotations.add(readAnnotation(entry));
			}

			List<Parameter> parameters = new ArrayList<>();
			for (KtParameter parameter : function.getValueParameters()) {
				if (parameter.getTypeReference() != null) {
					parameters.add(new Parameter(parameter.getName(), readType(parameter.getTypeReference())));
				}
			}

			TypeRef returnType = function.getTypeReference() != null ? readType(function.getTypeReference()) : null;
			Range nameRange = function.getNameIdentifier() != null ? range(function.getNameIdentifier()) : range(function);

			return new Function(function.getName(), range(function), nameRange, annotations, parameters, returnType);
		}

		private AnnotationUse readAnnotation(KtAnnotationEntry entry) {
			TypeRef type = entry.getTypeReference() != null ? readType(entry.getTypeReference()) : new TypeRef(entry.getShortName() != null ? entry.getShortName().asString() : "", false, List.of(), range(entry));
			String useSiteTarget = entry.getUseSiteTarget() != null ? entry.getUseSiteTarget().getText() : null;

			List<Argument> arguments = new ArrayList<>();
			for (ValueArgument valueArgument : entry.getValueArguments()) {
				KtExpression expression = valueArgument.getArgumentExpression();
				if (expression != null && valueArgument instanceof KtValueArgument ktArgument) {
					String name = ktArgument.isNamed() && ktArgument.getArgumentName() != null ? ktArgument.getArgumentName().getAsName().asString() : null;
					arguments.add(new Argument(name, readValue(expression), range(ktArgument), range(expression)));
				}
			}

			return new AnnotationUse(type.name(), useSiteTarget, arguments, range(entry), type.range());
		}

		private Value readValue(KtExpression expression) {
			if (expression instanceof KtParenthesizedExpression parenthesized && parenthesized.getExpression() != null) {
				return readValue(parenthesized.getExpression());
			}
			else if (expression instanceof KtStringTemplateExpression template) {
				return readTemplate(template);
			}
			else if (expression instanceof KtBinaryExpression binary && binary.getOperationToken() == KtTokens.PLUS
					&& binary.getLeft() != null && binary.getRight() != null) {
				List<Value> parts = new ArrayList<>();
				addParts(readValue(binary.getLeft()), parts);
				addParts(readValue(binary.getRight()), parts);
				return new ConcatValue(parts);
			}
			else if (expression instanceof KtConstantExpression constant) {
				String text = constant.getText();
				if ("true".equals(text) || "false".equals(text)) {
					return new BooleanValue(Boolean.parseBoolean(text));
				}
				else if (!text.isEmpty() && Character.isDigit(text.charAt(0))) {
					return new NumberValue(text);
				}
				return new UnsupportedValue(text);
			}
			else if (expression instanceof KtClassLiteralExpression classLiteral && classLiteral.getReceiverExpression() != null) {
				return new ClassValue(classLiteral.getReceiverExpression().getText());
			}
			else if (expression instanceof KtCollectionLiteralExpression collection) {
				List<Value> values = new ArrayList<>();
				List<Range> ranges = new ArrayList<>();
				for (KtExpression inner : collection.getInnerExpressions()) {
					values.add(readValue(inner));
					ranges.add(range(inner));
				}
				return new ArrayValue(values, ranges);
			}
			else if (expression instanceof KtCallExpression call && call.getCalleeExpression() != null && ARRAY_FUNCTIONS.contains(call.getCalleeExpression().getText())) {
				List<Value> values = new ArrayList<>();
				List<Range> ranges = new ArrayList<>();
				for (ValueArgument argument : call.getValueArguments()) {
					if (argument.getArgumentExpression() != null) {
						values.add(readValue(argument.getArgumentExpression()));
						ranges.add(range(argument.getArgumentExpression()));
					}
				}
				return new ArrayValue(values, ranges);
			}
			else if (DOTTED_NAME.matcher(expression.getText()).matches()) {
				return new ReferenceValue(expression.getText());
			}
			return new UnsupportedValue(expression.getText());
		}

		private static void addParts(Value value, List<Value> parts) {
			if (value instanceof ConcatValue concat) {
				parts.addAll(concat.parts());
			}
			else {
				parts.add(value);
			}
		}

		/** a string, which is put together from text and the values of the templates in it */
		private Value readTemplate(KtStringTemplateExpression template) {
			List<Value> parts = new ArrayList<>();
			StringBuilder text = new StringBuilder();
			boolean hasTemplates = false;

			for (KtStringTemplateEntry entry : template.getEntries()) {
				if (entry instanceof KtLiteralStringTemplateEntry) {
					text.append(entry.getText());
				}
				else if (entry instanceof KtEscapeStringTemplateEntry escape) {
					text.append(escape.getUnescapedValue());
				}
				else if (entry.getExpression() != null) {
					hasTemplates = true;
					if (text.length() > 0) {
						parts.add(new StringValue(text.toString()));
						text.setLength(0);
					}
					parts.add(readValue(entry.getExpression()));
				}
				else {
					return new UnsupportedValue(template.getText());
				}
			}

			if (!hasTemplates) {
				return new StringValue(text.toString());
			}
			if (text.length() > 0) {
				parts.add(new StringValue(text.toString()));
			}
			return new ConcatValue(parts);
		}

		private TypeRef readType(KtTypeReference reference) {
			boolean nullable = false;
			KtTypeElement element = reference.getTypeElement();
			while (element instanceof KtNullableType nullableType) {
				nullable = true;
				element = nullableType.getInnerType();
			}

			if (element instanceof KtUserType userType) {
				List<TypeRef> arguments = new ArrayList<>();
				for (KtTypeProjection projection : userType.getTypeArguments()) {
					arguments.add(projection.getTypeReference() != null ? readType(projection.getTypeReference()) : new TypeRef("*", false, List.of(), range(projection)));
				}
				return new TypeRef(userTypeName(userType), nullable, arguments, range(reference));
			}

			return new TypeRef(reference.getText(), nullable, List.of(), range(reference));
		}

		private String userTypeName(KtUserType type) {
			String name = type.getReferencedName() != null ? type.getReferencedName() : "";
			return type.getQualifier() != null ? userTypeName(type.getQualifier()) + "." + name : name;
		}

		private Range range(PsiElement element) {
			TextRange textRange = element.getTextRange();
			int start = mapper.originalStart(textRange.getStartOffset());
			int end = mapper.originalEnd(textRange.getEndOffset());
			return new Range(start, end - start);
		}
	}

}
