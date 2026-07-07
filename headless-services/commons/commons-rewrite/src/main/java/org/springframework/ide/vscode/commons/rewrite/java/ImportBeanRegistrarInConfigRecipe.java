/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.ArrayList;
import java.util.List;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.NewArray;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.JavaType.ShallowClass;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ImportBeanRegistrarInConfigRecipe extends Recipe {
	
	private static final String IMPORT_FQN = "org.springframework.context.annotation.Import";
	
	@Option(description = "Configuration bean fully qualified type")
	private String configBeanFqn;
	
	@Option(description = "The bean fully qualified type")
	private String beanRegFqn;
	
	@JsonCreator
	public ImportBeanRegistrarInConfigRecipe(
			@JsonProperty("configBeanFqn") String configBeanFqn,
			@JsonProperty("beanRegFqn") String beanRegFqn) {
		this.configBeanFqn = configBeanFqn;
		this.beanRegFqn = beanRegFqn;
	}

	@Override
	public @DisplayName String getDisplayName() {
		return "Add `BeanRegistrar` with `@Import` in Configuration bean";
	}

	@Override
	public @Description String getDescription() {
		return "Add `BeanRegistrar` with `@Import` in Configuration bean.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		final AnnotationMatcher importAnnotationMatcher = new AnnotationMatcher("@" + IMPORT_FQN);
		final ShallowClass beanRegistrarType = JavaType.ShallowClass.build(beanRegFqn);
		return Preconditions.check(new DeclaresType<>(configBeanFqn), new JavaIsoVisitor<>() {

			@Override
			public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext p) {
				ClassDeclaration cd = classDecl;
				FullyQualified type = TypeUtils.asFullyQualified(cd.getType());
				
				if (type != null && configBeanFqn.equals(type.getFullyQualifiedName())) {
					J.Annotation importAnnotation = cd.getLeadingAnnotations().stream().filter(importAnnotationMatcher::matches).findFirst().orElse(null);
					if (importAnnotation == null) {
						ArrayList<J.Annotation> annotations = new ArrayList<>(cd.getLeadingAnnotations());
						JavaType.ShallowClass annotationType = JavaType.ShallowClass.build(IMPORT_FQN);
						J.Identifier typeName = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), annotationType.getClassName(), annotationType, null);
						Space indent = Space.build("\n" + cd.getPrefix().getIndent(), List.of());
						boolean noAnnotations = annotations.isEmpty();
						importAnnotation = new J.Annotation(
								Tree.randomId(),
								noAnnotations ? Space.EMPTY : indent,
								Markers.EMPTY,
								typeName,
								null);
						annotations.add(importAnnotation);
						cd = cd.withLeadingAnnotations(annotations);
						if (noAnnotations) {
							cd = cd.getPadding().withKind(cd.getPadding().getKind().withPrefix(indent));
						}
						maybeAddImport(IMPORT_FQN);
					}
				}
				return super.visitClassDeclaration(cd, p);
			}

			@Override
			public Annotation visitAnnotation(Annotation annotation, ExecutionContext p) {
				Annotation an = super.visitAnnotation(annotation, p);
				FullyQualified anType = TypeUtils.asFullyQualified(an.getType());
				if (anType != null && IMPORT_FQN.equals(anType.getFullyQualifiedName())) {
					Cursor parentCursor = getCursor().getParentTreeCursor();
					if (parentCursor != null && parentCursor.getValue() instanceof J.ClassDeclaration cd 
							&& cd.getType() != null && configBeanFqn.equals(cd.getType().getFullyQualifiedName())) {
						List<Expression> currentArgs = an.getArguments();
						if (currentArgs == null || currentArgs.isEmpty() || currentArgs.get(0) instanceof J.Empty) {
							an = an.withArguments(List.of(createBeanRegistrarClassFieldAccess(beanRegistrarType)));
							maybeAddImport(beanRegistrarType);
						} else {
							List<Expression> newArgs = ListUtils.map(currentArgs, (i, arg) -> {
								if (arg instanceof J.FieldAccess fa) {
									if (!isBeanRegistrar(fa)) {
										// Turn into array
										NewArray array = new J.NewArray(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, List.of(), null, null);
										array = array.withInitializer(List.of(
												arg,
												createBeanRegistrarClassFieldAccess(beanRegistrarType)
										));
										maybeAddImport(beanRegistrarType);
										return autoFormat(array, p, getCursor());
									}
								} else if (arg instanceof J.NewArray na) {
									List<Expression> cna = new ArrayList<>(na.getInitializer());
									boolean hasBeanRegistarar = cna.stream().filter(J.FieldAccess.class::isInstance)
											.map(J.FieldAccess.class::cast)
											.anyMatch(ImportBeanRegistrarInConfigRecipe.this::isBeanRegistrar);
									// Not found bean registrar - add it to the array
									if (!hasBeanRegistarar) {
										cna.add(createBeanRegistrarClassFieldAccess(beanRegistrarType));
										maybeAddImport(beanRegistrarType);
										return autoFormat(na.withInitializer(cna), p, getCursor());
									}
								} else if (arg instanceof J.Assignment assign && assign.getVariable() instanceof J.Identifier ident && "value".equals(ident.getSimpleName())) {
									if (assign.getAssignment() instanceof J.FieldAccess fa) {
										if (!isBeanRegistrar(fa)) {
											// Turn into array
											NewArray array = new J.NewArray(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, List.of(), null, null);
											array = array.withInitializer(List.of(
													fa,
													createBeanRegistrarClassFieldAccess(beanRegistrarType)
											));
											maybeAddImport(beanRegistrarType);
											return autoFormat(assign.withAssignment(array), p, getCursor());
										}
									} else if (assign.getAssignment() instanceof J.NewArray na) {
										List<Expression> cna = new ArrayList<>(na.getInitializer());
										boolean hasBeanRegistarar = cna.stream().filter(J.FieldAccess.class::isInstance)
												.map(J.FieldAccess.class::cast)
												.anyMatch(ImportBeanRegistrarInConfigRecipe.this::isBeanRegistrar);
										// Not found bean registrar - add it to the array
										if (!hasBeanRegistarar) {
											cna.add(createBeanRegistrarClassFieldAccess(beanRegistrarType));
											maybeAddImport(beanRegistrarType);
											return autoFormat(assign.withAssignment(na.withInitializer(cna)), p, getCursor());
										}
									}
								}
								return arg;
							});
							if (newArgs != currentArgs) {
								an = an.withArguments(newArgs);
							}
						}
					}
				}
				return an;
			}
			
		});
	}
		
	private boolean isBeanRegistrar(J.FieldAccess fa) {
		if ("class".equals(fa.getSimpleName()) && fa.getTarget() instanceof TypeTree tt) {
			FullyQualified t = TypeUtils.asFullyQualified(tt.getType());
			if (t != null && beanRegFqn.equals(t.getFullyQualifiedName())) {
				return true;
			}
		}
		return false;
	}
	
	private J.FieldAccess createBeanRegistrarClassFieldAccess(JavaType.FullyQualified t) {
		J.Identifier i = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), t.getClassName(), t, null);
		JavaType.Parameterized classType = new JavaType.Parameterized(0, JavaType.ShallowClass.build("java.lang.Class"), List.of(t));
		J.Identifier c = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), "class", classType, null);
		return new J.FieldAccess(Tree.randomId(), Space.EMPTY, Markers.EMPTY, i, JLeftPadded.build(c), classType);
	}
	
}
