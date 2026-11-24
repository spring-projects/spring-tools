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
package org.springframework.ide.vscode.boot.kotlin.ast;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.SourceVersion;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinNodeBindings;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinSourceClass;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.jdt.JdtBindingResolverBridge;
import org.springframework.ide.vscode.boot.kotlin.jdt.KotlinJdtBindingSource;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Argument;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ArrayValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.BooleanValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassDeclaration;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassKind;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ConcatValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Constructor;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.NumberValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Parameter;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Property;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Range;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ReferenceValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.StringValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.TypeRef;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Value;

/**
 * Builds a JDT AST for a Kotlin source file, so that the indexing code that works on JDT ASTs can
 * be used for Kotlin sources as well.
 * <p>
 * The AST has the shape the JVM sees the Kotlin code in: classes, interfaces and annotation types
 * (a Kotlin {@code object} is a class), with their annotations, their functions as methods with
 * their parameters, their constructors, and the fields of their properties: the ones declared in
 * the body with a type, and the {@code val} and {@code var} parameters of the primary constructor.
 * Every node has the range of what it stands for in the Kotlin source, so locations and content
 * hashes that are taken from the AST point into the Kotlin file. What the nodes stand for is
 * recorded in the {@link KotlinNodeBindings}, and the JDT binding resolver of the AST answers
 * from there (see {@link JdtBindingResolverBridge}), so the nodes have JDT bindings.
 * <p>
 * The annotations of a parameter and a property go where Kotlin puts them for Java annotations:
 * an annotation without a use-site target on a parameter of the primary constructor is one of the
 * parameter (also if the parameter is a property), one on a property in the body is one of its
 * field, and {@code @field:} puts it on the field. Annotations for the getter, the setter or the
 * property itself are left out, nothing on the JVM side has them where the AST could show them.
 * <p>
 * Not built (yet): enum classes, top-level functions, function bodies, supertypes, the fields of
 * properties without a declared type, and properties are taken to have a field even if they have
 * none on the JVM (for example in an interface they are left out, but a property with a getter and
 * no backing field in a class is not told apart).
 *
 * @author Martin Lippert
 */
public class KotlinDomBuilder {

	/**
	 * The result of building: the AST, and what its nodes stand for in the Kotlin source.
	 */
	public record Result(CompilationUnit compilationUnit, KotlinNodeBindings bindings) {
	}

	/**
	 * The nodes of the AST resolve to JDT bindings, which are adapters over the Kotlin type
	 * resolution: code that works with JDT bindings runs on the AST as it is.
	 */
	public Result build(KotlinSyntax.File file, KotlinTypeResolver resolver) {
		AST ast = AST.newAST(AST.JLS25, false);
		CompilationUnit cu = ast.newCompilationUnit();
		cu.setSourceRange(0, file.length());

		KotlinNodeBindings bindings = new KotlinNodeBindings(resolver, file);

		if (file.packageRange() != null) {
			PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
			packageDeclaration.setName(name(ast, file.packageName(), file.packageNameRange()));
			range(packageDeclaration, file.packageRange());
			cu.setPackage(packageDeclaration);
		}

		for (ClassDeclaration declaration : file.classes()) {
			AbstractTypeDeclaration type = buildClass(ast, bindings, declaration, KotlinSourceClass.topLevel(file, declaration));
			if (type != null) {
				cu.types().add(type);
			}
		}

		JdtBindingResolverBridge.install(ast, new KotlinJdtBindingSource(bindings, resolver));
		return new Result(cu, bindings);
	}

	@SuppressWarnings("unchecked")
	private AbstractTypeDeclaration buildClass(AST ast, KotlinNodeBindings bindings, ClassDeclaration declaration, KotlinSourceClass source) {
		if (declaration.kind() == ClassKind.ENUM) {
			return null;
		}

		AbstractTypeDeclaration type;
		if (declaration.kind() == ClassKind.ANNOTATION) {
			type = ast.newAnnotationTypeDeclaration();
		}
		else {
			TypeDeclaration typeDeclaration = ast.newTypeDeclaration();
			typeDeclaration.setInterface(declaration.kind() == ClassKind.INTERFACE);
			type = typeDeclaration;
		}

		type.setName(simpleName(ast, declaration.name(), declaration.nameRange()));
		range(type, declaration.range());

		// annotations are resolved in the context the class is declared in
		for (AnnotationUse use : declaration.annotations()) {
			type.modifiers().add(buildAnnotation(ast, bindings, use, source.outer()));
		}

		// the members in the order they are written, which is the order the indexers see them in for Java
		List<BodyDeclaration> members = new ArrayList<>();
		if (declaration.kind() != ClassKind.ANNOTATION) {
			for (Constructor constructor : declaration.constructors()) {
				if (declaration.kind() == ClassKind.CLASS) {
					members.add(buildConstructor(ast, bindings, declaration, constructor, source));
				}
				if (constructor.primary() && declaration.kind() != ClassKind.INTERFACE) {
					for (Parameter parameter : constructor.parameters()) {
						if (parameter.property()) {
							members.add(buildField(ast, bindings, parameter.name(), parameter.type(), fieldAnnotations(parameter.annotations(), false),
									parameter.range(), parameter.nameRange(), source));
						}
					}
				}
			}
			if (declaration.kind() != ClassKind.INTERFACE) {
				for (Property property : declaration.properties()) {
					members.add(buildField(ast, bindings, property.name(), property.type(), fieldAnnotations(property.annotations(), true),
							property.range(), property.nameRange(), source));
				}
			}
		}
		for (KotlinSyntax.Function function : declaration.functions()) {
			members.add(buildFunction(ast, bindings, function, source));
		}
		members.sort((first, second) -> Integer.compare(first.getStartPosition(), second.getStartPosition()));
		type.bodyDeclarations().addAll(members);

		for (ClassDeclaration nested : declaration.nestedClasses()) {
			AbstractTypeDeclaration nestedType = buildClass(ast, bindings, nested, source.nested(nested));
			if (nestedType != null) {
				type.bodyDeclarations().add(nestedType);
			}
		}

		bindings.registerType(type, source);
		return type;
	}

	@SuppressWarnings("unchecked")
	private MethodDeclaration buildFunction(AST ast, KotlinNodeBindings bindings, KotlinSyntax.Function function, KotlinSourceClass declaringClass) {
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setConstructor(false);
		method.setName(simpleName(ast, function.name(), function.nameRange()));
		range(method, function.range());

		// the return type is not built, but the node that stands for it needs a place in the source, too
		org.eclipse.jdt.core.dom.PrimitiveType returnType = ast.newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.VOID);
		returnType.setSourceRange(function.nameRange().start(), 0);
		method.setReturnType2(returnType);

		for (AnnotationUse use : function.annotations()) {
			method.modifiers().add(buildAnnotation(ast, bindings, use, declaringClass));
		}

		addParameters(ast, bindings, method, function.parameters(), declaringClass);

		bindings.registerMethod(method, declaringClass, function);
		return method;
	}

	/**
	 * A constructor. Its name is the {@code constructor} keyword, or the name of the class for a
	 * primary constructor that is written without it (the constructor then reaches from the name
	 * of the class to the end of its parameters).
	 */
	@SuppressWarnings("unchecked")
	private MethodDeclaration buildConstructor(AST ast, KotlinNodeBindings bindings, ClassDeclaration declaration, Constructor constructor,
			KotlinSourceClass declaringClass) {
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setConstructor(true);
		method.setReturnType2(null);

		Range nameRange = constructor.keywordRange() != null ? constructor.keywordRange() : declaration.nameRange();
		method.setName(simpleName(ast, declaration.name(), nameRange));

		Range range = constructor.range();
		if (nameRange.start() < range.start()) {
			range = new Range(nameRange.start(), range.end() - nameRange.start());
		}
		range(method, range);

		for (AnnotationUse use : constructor.annotations()) {
			method.modifiers().add(buildAnnotation(ast, bindings, use, declaringClass));
		}

		addParameters(ast, bindings, method, constructor.parameters(), declaringClass);

		bindings.registerConstructor(method, declaringClass, constructor);
		return method;
	}

	@SuppressWarnings("unchecked")
	private void addParameters(AST ast, KotlinNodeBindings bindings, MethodDeclaration method, List<Parameter> parameters, KotlinSourceClass declaringClass) {
		for (Parameter parameter : parameters) {
			SingleVariableDeclaration variable = ast.newSingleVariableDeclaration();
			variable.setName(simpleName(ast, parameter.name(), parameter.nameRange()));
			variable.setType(typeNode(ast, bindings, parameter.type(), declaringClass));
			for (AnnotationUse use : parameter.annotations()) {
				if (use.useSiteTarget() == null || "param".equals(use.useSiteTarget())) {
					variable.modifiers().add(buildAnnotation(ast, bindings, use, declaringClass));
				}
			}
			range(variable, parameter.range());

			bindings.registerVariable(variable, parameter.name(), parameter.type(), declaringClass, true);
			method.parameters().add(variable);
		}
	}

	/**
	 * The annotations of a property that end up on its field: the ones for the field, and for a
	 * property in the body the ones without a use-site target as well.
	 */
	private static List<AnnotationUse> fieldAnnotations(List<AnnotationUse> annotations, boolean withoutTarget) {
		return annotations.stream()
				.filter(use -> "field".equals(use.useSiteTarget()) || (withoutTarget && use.useSiteTarget() == null))
				.toList();
	}

	/** the field of a property, its range is the one of the property */
	@SuppressWarnings("unchecked")
	private FieldDeclaration buildField(AST ast, KotlinNodeBindings bindings, String name, TypeRef typeRef, List<AnnotationUse> annotations, Range range,
			Range nameRange, KotlinSourceClass declaringClass) {
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(simpleName(ast, name, nameRange));
		range(fragment, new Range(nameRange.start(), range.end() - nameRange.start()));

		FieldDeclaration field = ast.newFieldDeclaration(fragment);
		field.setType(typeNode(ast, bindings, typeRef, declaringClass));
		for (AnnotationUse use : annotations) {
			field.modifiers().add(buildAnnotation(ast, bindings, use, declaringClass));
		}
		range(field, range);

		bindings.registerVariable(fragment, name, typeRef, declaringClass, false);
		return field;
	}

	/**
	 * The node of a type as written. It holds the name only, the binding it resolves to is the
	 * whole type, with its type arguments and nullability.
	 */
	private Type typeNode(AST ast, KotlinNodeBindings bindings, TypeRef typeRef, KotlinSourceClass scope) {
		Range range = typeRef.range();
		Range nameRange = range != null ? new Range(range.start(), Math.min(typeRef.name().length(), range.length())) : null;
		SimpleType type = ast.newSimpleType(name(ast, isDottedName(typeRef.name()) ? typeRef.name() : "Object", nameRange));
		if (range != null) {
			range(type, range);
		}
		bindings.registerTypeReference(type, typeRef, scope);
		return type;
	}

	@SuppressWarnings("unchecked")
	private Annotation buildAnnotation(AST ast, KotlinNodeBindings bindings, AnnotationUse use, KotlinSourceClass scope) {
		List<Argument> arguments = new ArrayList<>();
		List<Expression> values = new ArrayList<>();
		for (Argument argument : use.arguments()) {
			Expression value = expression(ast, bindings, argument.value(), argument.valueRange(), scope);
			if (value != null) {
				arguments.add(argument);
				values.add(value);
			}
		}

		Name typeName = name(ast, use.typeName(), use.typeNameRange());
		Annotation annotation;

		if (arguments.isEmpty()) {
			annotation = ast.newMarkerAnnotation();
			((org.eclipse.jdt.core.dom.MarkerAnnotation) annotation).setTypeName(typeName);
		}
		else if (arguments.size() == 1 && arguments.get(0).name() == null) {
			SingleMemberAnnotation single = ast.newSingleMemberAnnotation();
			single.setTypeName(typeName);
			single.setValue(values.get(0));
			annotation = single;
		}
		else {
			NormalAnnotation normal = ast.newNormalAnnotation();
			normal.setTypeName(typeName);
			for (int i = 0; i < arguments.size(); i++) {
				Argument argument = arguments.get(i);
				MemberValuePair pair = ast.newMemberValuePair();
				String pairName = argument.name() != null ? argument.name() : (i == 0 ? "value" : "arg" + i);
				pair.setName(simpleName(ast, pairName, new Range(argument.range().start(), argument.name() != null ? argument.name().length() : 0)));
				pair.setValue(values.get(i));
				range(pair, argument.range());
				normal.values().add(pair);
			}
			annotation = normal;
		}

		range(annotation, use.range());
		bindings.registerAnnotation(annotation, use, scope);
		return annotation;
	}

	/**
	 * Constants are evaluated: a reference to a constant, a concatenation and a string with templates
	 * become the literal with the value they have, as the value of a constant is what the JDT based
	 * indexing works with for Java sources as well.
	 *
	 * @param scope the class the value is written in, null for the top level
	 * @return null if the value cannot be represented
	 */
	@SuppressWarnings("unchecked")
	private Expression expression(AST ast, KotlinNodeBindings bindings, Value value, Range range, KotlinSourceClass scope) {
		Expression result = null;

		if (value instanceof StringValue string) {
			StringLiteral literal = ast.newStringLiteral();
			literal.setLiteralValue(string.value());
			result = literal;
		}
		else if (value instanceof NumberValue number) {
			try {
				NumberLiteral literal = ast.newNumberLiteral();
				literal.setToken(number.token());
				result = literal;
			}
			catch (IllegalArgumentException e) {
				// a number that is written in a way Java does not know
			}
		}
		else if (value instanceof BooleanValue bool) {
			result = ast.newBooleanLiteral(bool.value());
		}
		else if (value instanceof ClassValue classValue) {
			if (isDottedName(classValue.typeName())) {
				TypeLiteral literal = ast.newTypeLiteral();
				SimpleType type = ast.newSimpleType(name(ast, classValue.typeName(), new Range(range.start(), 0)));
				literal.setType(type);
				bindings.registerTypeReference(type, classValue.typeName(), scope);
				result = literal;
			}
		}
		else if (value instanceof ArrayValue array) {
			ArrayInitializer initializer = ast.newArrayInitializer();
			for (int i = 0; i < array.values().size(); i++) {
				Range elementRange = array.rangeOf(i) != null ? array.rangeOf(i) : range;
				Expression expression = expression(ast, bindings, array.values().get(i), elementRange, scope);
				if (expression != null) {
					initializer.expressions().add(expression);
				}
			}
			result = initializer;
		}
		else if (value instanceof ReferenceValue reference) {
			Object constant = bindings.constants().evaluate(value, bindings.file(), scope);
			result = constant != null ? literalOf(ast, constant) : null;
			if (result == null && isDottedName(reference.name())) {
				result = name(ast, reference.name(), range); // an enum constant, or a constant that is not known
			}
		}
		else if (value instanceof ConcatValue) {
			Object constant = bindings.constants().evaluate(value, bindings.file(), scope);
			result = constant != null ? literalOf(ast, constant) : null;
		}

		if (result != null) {
			range(result, range);
		}
		return result;
	}

	/** the literal for the value of a constant, null if there is none for it */
	private Expression literalOf(AST ast, Object constant) {
		try {
			if (constant instanceof String string) {
				StringLiteral literal = ast.newStringLiteral();
				literal.setLiteralValue(string);
				return literal;
			}
			else if (constant instanceof Boolean bool) {
				return ast.newBooleanLiteral(bool);
			}
			else if (constant instanceof Character character) {
				CharacterLiteral literal = ast.newCharacterLiteral();
				literal.setCharValue(character);
				return literal;
			}
			else if (constant instanceof Long number) {
				NumberLiteral literal = ast.newNumberLiteral();
				literal.setToken(number + "L");
				return literal;
			}
			else if (constant instanceof Number number) {
				NumberLiteral literal = ast.newNumberLiteral();
				literal.setToken(number.toString());
				return literal;
			}
		}
		catch (IllegalArgumentException e) {
			// a value that has no literal
		}
		return null;
	}

	private static boolean isDottedName(String text) {
		for (String segment : text.split("\\.", -1)) {
			if (segment.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A name, with the ranges of its parts if the name is written in one piece, without spaces.
	 */
	private Name name(AST ast, String dotted, Range range) {
		String[] segments = dotted.split("\\.", -1);

		int start = range != null ? range.start() : -1;
		int offset = 0;
		Name name = null;

		for (String segment : segments) {
			SimpleName simple = simpleName(ast, segment.isEmpty() ? "_" : segment, start >= 0 ? new Range(start + offset, segment.length()) : null);
			if (name == null) {
				name = simple;
			}
			else {
				name = ast.newQualifiedName(name, simple);
				if (start >= 0) {
					name.setSourceRange(start, offset + segment.length());
				}
			}
			offset += segment.length() + 1;
		}
		return name;
	}

	private SimpleName simpleName(AST ast, String identifier, Range range) {
		SimpleName name;
		try {
			name = ast.newSimpleName(javaIdentifier(identifier));
		}
		catch (IllegalArgumentException e) {
			name = ast.newSimpleName("_" + javaIdentifier(identifier));
		}
		if (range != null) {
			range(name, range);
		}
		return name;
	}

	/**
	 * Kotlin names can be written in backticks and then contain what Java names cannot. They are
	 * made into valid Java identifiers here, since that is what the nodes of the AST can hold.
	 */
	static String javaIdentifier(String name) {
		if (SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name)) {
			return name;
		}

		StringBuilder result = new StringBuilder(name.length() + 1);
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			result.append(i == 0 ? (Character.isJavaIdentifierStart(c) ? c : '_') : (Character.isJavaIdentifierPart(c) ? c : '_'));
		}
		if (result.length() == 0 || SourceVersion.isKeyword(result)) {
			result.append('_');
		}
		return result.toString();
	}

	private static void range(ASTNode node, Range range) {
		node.setSourceRange(range.start(), range.length());
	}

}
