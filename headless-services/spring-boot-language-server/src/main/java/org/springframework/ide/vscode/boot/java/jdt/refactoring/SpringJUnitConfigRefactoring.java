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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * A JDT-based refactoring that replaces a test class's
 * {@code @ExtendWith(SpringExtension.class)} and {@code @ContextConfiguration}
 * annotations (both together) with {@code @SpringJUnitConfig}, which is itself
 * meta-annotated with exactly those two.
 * <p>
 * Every {@code @ContextConfiguration} attribute has an equivalent of the same name on
 * {@code @SpringJUnitConfig} and is carried over, with one exception: the default
 * attribute means something different on the two annotations. On
 * {@code @ContextConfiguration}, {@code value} aliases {@code locations}, whereas on
 * {@code @SpringJUnitConfig} it aliases {@code classes}. A default-attribute value is
 * therefore written out as an explicit {@code locations} attribute. Conversely, a lone
 * {@code classes} attribute is written out in the shorter default-attribute form.
 * <p>
 * Pass one or more type offsets to convert. When used with a single offset this
 * corresponds to a node-scoped quickfix; with multiple offsets (all occurrences in a file)
 * this corresponds to a file-scoped "fix all" quickfix.
 */
public class SpringJUnitConfigRefactoring implements JdtRefactoring {

	private static final String EXTEND_WITH_FQN = "org.junit.jupiter.api.extension.ExtendWith";
	private static final String SPRING_EXTENSION_FQN = "org.springframework.test.context.junit.jupiter.SpringExtension";
	private static final String CONTEXT_CONFIGURATION_FQN = "org.springframework.test.context.ContextConfiguration";
	private static final String SPRING_JUNIT_CONFIG_FQN = "org.springframework.test.context.junit.jupiter.SpringJUnitConfig";

	private static final String LOCATIONS = "locations";
	private static final String CLASSES = "classes";
	private static final String VALUE = "value";

	private final int[] typeOffsets;

	/**
	 * @param typeOffsets start positions of the type declarations to convert
	 */
	public SpringJUnitConfigRefactoring(int... typeOffsets) {
		this.typeOffsets = typeOffsets;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		boolean anyConverted = false;
		Set<ASTNode> exactRangeNodes = new HashSet<>();
		for (int offset : typeOffsets) {
			TypeDeclaration type = JdtRefactorUtils.findAncestorAtOffset(cu, offset, TypeDeclaration.class);
			if (type != null && convertType(rewrite, cu, type, exactRangeNodes)) {
				anyConverted = true;
			}
		}

		if (anyConverted) {
			// the two merged annotations are direct children of a rewritten list, so JDT's
			// default target source range would extend onto any unclaimed leading comment
			// (e.g. a line comment sitting between the Javadoc and the annotations) and delete
			// it along with the annotation it is replacing/removing
			rewrite.setTargetSourceRangeComputer(JdtRefactorUtils.exactRangeSourceComputer(exactRangeNodes));

			AST ast = cu.getAST();
			JdtRefactorUtils.addImport(rewrite, ast, cu,
					new ClassType(JdtRefactorUtils.extractPackageName(SPRING_JUNIT_CONFIG_FQN),
							JdtRefactorUtils.extractSimpleName(SPRING_JUNIT_CONFIG_FQN)));
			JdtRefactorUtils.removeImports(cu, rewrite, EXTEND_WITH_FQN, SPRING_EXTENSION_FQN,
					CONTEXT_CONFIGURATION_FQN);
		}
	}

	private boolean convertType(ASTRewrite rewrite, CompilationUnit cu, TypeDeclaration type,
			Set<ASTNode> exactRangeNodes) {
		AST ast = cu.getAST();

		Annotation extendWithAnnotation = findSpringExtensionAnnotation(type);
		Annotation contextConfigurationAnnotation = JdtRefactorUtils.findAnnotationByName(type,
				CONTEXT_CONFIGURATION_FQN);

		if (extendWithAnnotation == null || contextConfigurationAnnotation == null) {
			return false;
		}

		Annotation newAnnotation = createSpringJUnitConfigAnnotation(ast, contextConfigurationAnnotation);

		List<Annotation> merged = List.of(extendWithAnnotation, contextConfigurationAnnotation);
		JdtRefactorUtils.replaceWithMergedAnnotation(rewrite, type, TypeDeclaration.MODIFIERS2_PROPERTY, merged,
				newAnnotation, exactRangeNodes);

		return true;
	}

	/**
	 * Creates the replacement {@code @SpringJUnitConfig} annotation, carrying over the
	 * attributes of the given {@code @ContextConfiguration} annotation.
	 */
	private static Annotation createSpringJUnitConfigAnnotation(AST ast, Annotation contextConfiguration) {
		List<Attribute> attributes = new ArrayList<>();

		if (contextConfiguration.isSingleMemberAnnotation()) {
			// '@ContextConfiguration's default attribute aliases 'locations' while
			// '@SpringJUnitConfig's aliases 'classes', so it has to be spelled out
			attributes.add(new Attribute(LOCATIONS, ((SingleMemberAnnotation) contextConfiguration).getValue()));
		}
		else if (contextConfiguration instanceof NormalAnnotation normal) {
			for (Object o : normal.values()) {
				MemberValuePair pair = (MemberValuePair) o;
				String name = pair.getName().getIdentifier();
				attributes.add(new Attribute(VALUE.equals(name) ? LOCATIONS : name, pair.getValue()));
			}
		}

		if (attributes.isEmpty()) {
			MarkerAnnotation marker = ast.newMarkerAnnotation();
			marker.setTypeName(newTypeName(ast));
			return marker;
		}

		// a lone 'classes' attribute is '@SpringJUnitConfig's default attribute, so it can
		// be written out in the shorter default-attribute form
		if (attributes.size() == 1 && CLASSES.equals(attributes.get(0).name())) {
			SingleMemberAnnotation single = ast.newSingleMemberAnnotation();
			single.setTypeName(newTypeName(ast));
			single.setValue((Expression) ASTNode.copySubtree(ast, attributes.get(0).value()));
			return single;
		}

		NormalAnnotation annotation = ast.newNormalAnnotation();
		annotation.setTypeName(newTypeName(ast));
		@SuppressWarnings("unchecked")
		List<MemberValuePair> values = annotation.values();
		for (Attribute attribute : attributes) {
			MemberValuePair pair = ast.newMemberValuePair();
			pair.setName(ast.newSimpleName(attribute.name()));
			pair.setValue((Expression) ASTNode.copySubtree(ast, attribute.value()));
			values.add(pair);
		}
		return annotation;
	}

	private static Name newTypeName(AST ast) {
		return ast.newSimpleName(JdtRefactorUtils.extractSimpleName(SPRING_JUNIT_CONFIG_FQN));
	}

	/**
	 * An attribute of the annotation to create, still pointing at the expression node of
	 * the original {@code @ContextConfiguration} annotation.
	 */
	private record Attribute(String name, Expression value) {
	}

	/**
	 * Finds the (possibly repeated) {@code @ExtendWith} annotation on {@code type} whose
	 * only value is {@code SpringExtension.class}. Only such an annotation can be removed
	 * wholesale; an {@code @ExtendWith} listing further extensions has to stay in place.
	 * <p>
	 * Matches by simple name rather than resolved bindings so this also works against ASTs
	 * parsed without a classpath.
	 */
	private static Annotation findSpringExtensionAnnotation(TypeDeclaration type) {
		String extendWithSimpleName = JdtRefactorUtils.extractSimpleName(EXTEND_WITH_FQN);
		for (Object mod : type.modifiers()) {
			if (mod instanceof Annotation a) {
				String name = a.getTypeName().getFullyQualifiedName();
				if ((name.equals(EXTEND_WITH_FQN) || name.equals(extendWithSimpleName)) && isOnlySpringExtension(a)) {
					return a;
				}
			}
		}
		return null;
	}

	private static boolean isOnlySpringExtension(Annotation extendWith) {
		Expression value = null;
		if (extendWith.isSingleMemberAnnotation()) {
			value = ((SingleMemberAnnotation) extendWith).getValue();
		}
		else if (extendWith instanceof NormalAnnotation normal) {
			for (Object o : normal.values()) {
				MemberValuePair pair = (MemberValuePair) o;
				if (!VALUE.equals(pair.getName().getIdentifier())) {
					return false;
				}
				value = pair.getValue();
			}
		}

		if (value instanceof ArrayInitializer array) {
			if (array.expressions().size() != 1) {
				return false;
			}
			value = (Expression) array.expressions().get(0);
		}

		if (value instanceof TypeLiteral typeLiteral) {
			String typeName = typeLiteral.getType().toString();
			return typeName.equals(SPRING_EXTENSION_FQN)
					|| typeName.equals(JdtRefactorUtils.extractSimpleName(SPRING_EXTENSION_FQN));
		}
		return false;
	}

}
