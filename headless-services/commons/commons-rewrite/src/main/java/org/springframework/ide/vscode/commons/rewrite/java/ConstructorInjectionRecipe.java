/*******************************************************************************
 * Copyright (c) 2017, 2025 Broadcom, Inc.
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Assignment;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.JavaType.ShallowClass;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Udayani V
 */
public class ConstructorInjectionRecipe extends Recipe {

	@Override
	public @DisplayName String getDisplayName() {
		return "Add bean injection";
	}

	@Override
	public @Description String getDescription() {
		return "Add bean injection.";
	}

	@Option(description = "Fully qualified type of the field")
	@NonNull
	String fullyQualifiedName;

	@Option(description = "Name of the field")
	@NonNull
	String fieldName;

	@Option(description = "Fully qualified type of the container class")
	@NonNull
	String classFqName;

	@JsonCreator
	public ConstructorInjectionRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedName,
			@NonNull @JsonProperty("fieldName") String fieldName,
			@NonNull @JsonProperty("classFqName") String classFqName) {
		this.fullyQualifiedName = fullyQualifiedName;
		this.fieldName = fieldName;
		this.classFqName = classFqName;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {

		return new CustomFieldIntoConstructorParameterVisitor(classFqName, fieldName);
	}

	class CustomFieldIntoConstructorParameterVisitor extends JavaVisitor<ExecutionContext> {

		private final String classFqName;
		private final String fieldName;
		private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

		public CustomFieldIntoConstructorParameterVisitor(String classFqName, String fieldName) {
			this.classFqName = classFqName;
			this.fieldName = fieldName;
		}

		@Override
		public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

			if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {
				List<MethodDeclaration> constructors = classDecl.getBody().getStatements().stream()
						.filter(J.MethodDeclaration.class::isInstance).map(J.MethodDeclaration.class::cast)
						.filter(MethodDeclaration::isConstructor).collect(Collectors.toList());
				boolean applicable = false;
				if (constructors.isEmpty()) {
					applicable = true;
				} else if (constructors.size() == 1) {
					MethodDeclaration c = constructors.get(0);
					getCursor().putMessage("applicableConstructor", c);
					applicable = !isConstructorInitializingField(c, fieldName);
				} else {
					List<MethodDeclaration> autowiredConstructors = constructors.stream()
							.filter(constr -> constr.getLeadingAnnotations().stream()
									.map(a -> TypeUtils.asFullyQualified(a.getType())).filter(Objects::nonNull)
									.map(FullyQualified::getFullyQualifiedName).anyMatch(AUTOWIRED::equals))
							.limit(2).collect(Collectors.toList());
					if (autowiredConstructors.size() == 1) {
						MethodDeclaration c = autowiredConstructors.get(0);
						getCursor().putMessage("applicableConstructor", autowiredConstructors.get(0));
						applicable = !isConstructorInitializingField(c, fieldName);
					}
				}
				if (applicable) {
					return super.visitClassDeclaration(classDecl, ctx);
				}
			}
			return super.visitClassDeclaration(classDecl, ctx);
		}

		@Override
		public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
				ExecutionContext ctx) {

			Cursor blockCursor = getCursor().getParentTreeCursor();
			if (!(blockCursor.getValue() instanceof J.Block)) {
				return multiVariable;
			}
			Cursor typeDeclCursor = blockCursor.getParentTreeCursor();
			if (!(typeDeclCursor.getValue() instanceof J.ClassDeclaration)) {
				return multiVariable;
			}
			VariableDeclarations mv = multiVariable;
			if (multiVariable.getVariables().size() == 1
					&& fieldName.equals(multiVariable.getVariables().get(0).getName().getSimpleName())) {
				if (mv.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
					Space prefix = Space.firstPrefix(mv.getVariables());
					J.Modifier m = new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null,
							J.Modifier.Type.Final, Collections.emptyList());
					if (mv.getModifiers().isEmpty()) {
						mv = mv.withTypeExpression(mv.getTypeExpression().withPrefix(prefix));
					} else {
						m = m.withPrefix(prefix);
					}
					mv = mv.withModifiers(ListUtils.concat(mv.getModifiers(), m));
				}
				MethodDeclaration constructor = blockCursor.getParent().getMessage("applicableConstructor");
				ClassDeclaration c = blockCursor.getParent().getValue();
				TypeTree fieldType = TypeTree.build(fullyQualifiedName);
				if (constructor == null) {
					doAfterVisit(new AddConstructorVisitor(c.getSimpleName(), fieldName, fieldType));
				} else {
					doAfterVisit(new AddConstructorParameterAndAssignment(constructor, fieldName, fieldType));
				}
			}
			return mv;
		}
	}

	private static class AddConstructorVisitor extends JavaVisitor<ExecutionContext> {
		private final String className;
		private final String fieldName;
		private final TypeTree type;

		public AddConstructorVisitor(String className, String fieldName, TypeTree type) {
			this.className = className;
			this.fieldName = fieldName;
			this.type = type;
		}

		@Override
		public J visitBlock(Block block, ExecutionContext p) {
			J result = (Block) super.visitBlock(block, p);
			if (getCursor().getParent() != null) {
				Object n = getCursor().getParent().getValue();
				if (n instanceof ClassDeclaration) {
					ClassDeclaration classDecl = (ClassDeclaration) n;
					JavaType.FullyQualified typeFqn = TypeUtils.asFullyQualified(type.getType());
					if (typeFqn != null && classDecl.getKind() == ClassDeclaration.Kind.Type.Class
							&& className.equals(classDecl.getSimpleName())) {
						JavaTemplate.Builder template = JavaTemplate.builder(""
                                + classDecl.getSimpleName() + "(" + getFieldType(typeFqn) + " " + fieldName + ") {\n"
                                + "this." + fieldName + " = " + fieldName + ";\n"
                                + "}\n"
                        ).contextSensitive();
						FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
						if (fq != null) {
							template.imports(fq.getFullyQualifiedName());
							maybeAddImport(fq);
						}
						Optional<Statement> firstMethod = block.getStatements().stream()
								.filter(MethodDeclaration.class::isInstance).findFirst();

						return firstMethod
								.map(statement -> (J) template.build().apply(getCursor(),
										statement.getCoordinates().before()))
								.orElseGet(() -> template.build().apply(getCursor(),
										block.getCoordinates().lastStatement()));
					}
				}
			}
			return result;
		}
	}

	private static class AddConstructorParameterAndAssignment extends JavaIsoVisitor<ExecutionContext> {
		private final MethodDeclaration constructor;
		private final String fieldName;
		private final String methodType;

		public AddConstructorParameterAndAssignment(MethodDeclaration constructor, String fieldName, TypeTree type) {
			this.constructor = constructor;
			this.fieldName = fieldName;
			JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
			if (fq != null) {
				methodType = getFieldType(fq);
			} else {
				throw new IllegalArgumentException("Unable to determine parameter type");
			}
		}

		@Override
		public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
			J.MethodDeclaration md = super.visitMethodDeclaration(method, p);
			if (md.getId().equals(constructor.getId()) && md.getBody() != null) {
				
				boolean parameterExists = md.getParameters().stream().filter(J.VariableDeclarations.class::isInstance).map(J.VariableDeclarations.class::cast).filter(vd -> {
					if (vd.getVariables().stream().anyMatch(vn -> fieldName.equals(vn.getSimpleName()))) {
						FullyQualified fqType = vd.getTypeAsFullyQualified();
						if (fqType != null && methodType.equals(fqType.getClassName())) {
							return true;
						}
					}
					return false;
				}).findFirst().isPresent();
				if (!parameterExists) {
					List<Statement> newParams = new ArrayList<>(md.getParameters().stream().filter(s -> !(s instanceof J.Empty)).toList());
					J.VariableDeclarations vd = new J.VariableDeclarations(
							Tree.randomId(),
							newParams.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE,
							Markers.EMPTY,
							Collections.emptyList(),
							Collections.emptyList(),
							TypeTree.build(methodType),
							null,
							Collections.emptyList(),
							List.of(JRightPadded.build(new J.VariableDeclarations.NamedVariable(
									Tree.randomId(),
									Space.SINGLE_SPACE,
									Markers.EMPTY,
									createFieldNameIdentifier(),
									Collections.emptyList(),
									null,
									null
							)))
					);
					newParams.add(vd);
					md = md.withParameters(newParams);
					updateCursor(md);
				}

				if (!isConstructorInitializingField(md, fieldName)) {
					// noinspection ConstantConditions
					ShallowClass type = JavaType.ShallowClass.build(methodType);
					J.FieldAccess fa = new J.FieldAccess(Tree.randomId(), Space.EMPTY, Markers.EMPTY, new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "this", md.getMethodType().getDeclaringType(), null), JLeftPadded.build(createFieldNameIdentifier()), type);
					Assignment assign = new J.Assignment(Tree.randomId(), Space.build("\n", Collections.emptyList()), Markers.EMPTY, fa, JLeftPadded.build(createFieldNameIdentifier()), type);
					assign = autoFormat(assign, p, getCursor());
					List<Statement> newStatements = new ArrayList<>(md.getBody().getStatements());
					boolean empty = newStatements.isEmpty();
					if (empty) {
						newStatements.add(assign);
						md = md.withBody(autoFormat(md.getBody().withStatements(newStatements), p, getCursor()));
					} else {
						// Prefix is off otherwise even after autoFormat
						newStatements.add(assign.withPrefix(newStatements.get(newStatements.size() - 1).getPrefix()));
						md = md.withBody(md.getBody().withStatements(newStatements));
					}
				}
			}
			return md;
		}
		
		private J.Identifier createFieldNameIdentifier() {
			return new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), fieldName, JavaType.ShallowClass.build(methodType), null);
		}

	}
	
	private static String getFieldType(JavaType.FullyQualified fullyQualifiedType) {
		if (fullyQualifiedType.getOwningClass() != null) {
			String[] parts = fullyQualifiedType.getFullyQualifiedName().split("\\.");
			if (parts.length < 2) {
				return fullyQualifiedType.getClassName();
			}
			return parts[parts.length - 2] + "." + parts[parts.length - 1];
		}

		return fullyQualifiedType.getClassName();
	}
	
	private static boolean isConstructorInitializingField(MethodDeclaration c, String fieldName) {
		AtomicBoolean res = new AtomicBoolean();
		new JavaIsoVisitor<AtomicBoolean>() {

			@Override
			public Assignment visitAssignment(Assignment assignment, AtomicBoolean ab) {
				if (ab.get() || getCursor().firstEnclosing(MethodDeclaration.class) != c) {
					return assignment;
				}
				Assignment a = super.visitAssignment(assignment, ab);
				Expression expr = a.getVariable();
				if (expr instanceof J.FieldAccess) {
					J.FieldAccess fa = (J.FieldAccess) expr;
					if (fieldName.equals(fa.getSimpleName()) && fa.getTarget() instanceof J.Identifier) {
						J.Identifier target = (J.Identifier) fa.getTarget();
						if ("this".equals(target.getSimpleName())) {
							ab.set(true);
							return a;
						}
					}
				}
				return a;
			}

			@Override
			public Identifier visitIdentifier(Identifier identifier, AtomicBoolean ab) {
				if (ab.get() || getCursor().firstEnclosing(MethodDeclaration.class) != c) {
					return identifier;
				}
				Identifier id = super.visitIdentifier(identifier, ab);
				JavaType.Variable fieldType = c.getMethodType().getDeclaringType().getMembers().stream()
						.filter(v -> fieldName.equals(v.getName())).findFirst().orElse(null);
				if (fieldType != null && fieldType.equals(id.getFieldType())) {
					 ab.set(true);
				}
				return id;
			}	
		}.visit(c, res);
		return res.get();
	}


	
}