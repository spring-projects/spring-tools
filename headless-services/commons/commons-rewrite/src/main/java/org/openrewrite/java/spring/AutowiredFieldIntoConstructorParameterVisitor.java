/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;


public class AutowiredFieldIntoConstructorParameterVisitor extends JavaVisitor<ExecutionContext> {
    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private final String classFqName;
    private final String fieldName;
    
    
    @JsonCreator
    public AutowiredFieldIntoConstructorParameterVisitor(String classFqName, String fieldName) {
		this.classFqName = classFqName;
		this.fieldName = fieldName;
	}

	@Override
    public J visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext ctx) {
        if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {
            List<MethodDeclaration> constructors = classDecl.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(MethodDeclaration::isConstructor)
                    .collect(Collectors.toList());
            boolean applicable = false;
            if (constructors.isEmpty()) {
                applicable = true;
            } else if (constructors.size() == 1) {
                MethodDeclaration c = constructors.get(0);
                getCursor().putMessage("applicableConstructor", c);
                applicable = isNotConstructorInitializingField(c, fieldName);
            } else {
                List<MethodDeclaration> autowiredConstructors = constructors.stream().filter(constr -> constr.getLeadingAnnotations().stream()
                                .map(a -> TypeUtils.asFullyQualified(a.getType()))
                                .filter(Objects::nonNull)
                                .map(FullyQualified::getFullyQualifiedName)
                                .anyMatch(AUTOWIRED::equals)
                        )
                        .limit(2)
                        .collect(Collectors.toList());
                if (autowiredConstructors.size() == 1) {
                    MethodDeclaration c = autowiredConstructors.get(0);
                    getCursor().putMessage("applicableConstructor", autowiredConstructors.get(0));
                    applicable = isNotConstructorInitializingField(c, fieldName);
                }
            }
            if (applicable) {
                // visit contents if there is applicable constructor
                return super.visitClassDeclaration(classDecl, ctx);
            }
        }
        return classDecl;
    }

    public static boolean isNotConstructorInitializingField(MethodDeclaration c, String fieldName) {
        return c.getBody() == null || c.getBody().getStatements().stream().filter(J.Assignment.class::isInstance).map(J.Assignment.class::cast).noneMatch(a -> {
            Expression expr = a.getVariable();
            if (expr instanceof J.FieldAccess) {
                J.FieldAccess fa = (J.FieldAccess) expr;
                if (fieldName.equals(fa.getSimpleName()) && fa.getTarget() instanceof J.Identifier) {
                    J.Identifier target = (J.Identifier) fa.getTarget();
                    if ("this".equals(target.getSimpleName())) {
                        return true;
                    }
                }
            }
            if (expr instanceof J.Identifier) {
                JavaType.Variable fieldType = c.getMethodType().getDeclaringType().getMembers().stream().filter(v -> fieldName.equals(v.getName())).findFirst().orElse(null);
                if (fieldType != null) {
                    J.Identifier identifier = (J.Identifier) expr;
                    return fieldType.equals(identifier.getFieldType());
                }
            }
            return false;
        });
    }

    @Override
    public J visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext p) {
        Cursor blockCursor = getCursor().dropParentUntil(it -> it instanceof J.Block || it == Cursor.ROOT_VALUE);
        if (!(blockCursor.getValue() instanceof J.Block)) {
            return multiVariable;
        }
        VariableDeclarations mv = multiVariable;
        if (blockCursor.getParent() != null && blockCursor.getParent().getValue() instanceof ClassDeclaration &&
                multiVariable.getVariables().size() == 1 &&
                fieldName.equals(multiVariable.getVariables().get(0).getName().getSimpleName())) {

            mv = (VariableDeclarations) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + AUTOWIRED)).visitNonNull(multiVariable, p);
            if (mv != multiVariable && multiVariable.getTypeExpression() != null) {
                if (mv.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
                    Space prefix = Space.firstPrefix(mv.getVariables());
                    J.Modifier m = new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Final, Collections.emptyList());
                    if (mv.getModifiers().isEmpty()) {
                        mv = mv.withTypeExpression(mv.getTypeExpression().withPrefix(prefix));
                    } else {
                        m = m.withPrefix(prefix);
                    }
                    mv = mv.withModifiers(ListUtils.concat(mv.getModifiers(), m));
                }
                maybeRemoveImport(AUTOWIRED);
                MethodDeclaration constructor = blockCursor.getParent().getMessage("applicableConstructor");
                ClassDeclaration c = blockCursor.getParent().getValue();
                if (constructor == null) {
                    doAfterVisit(new AddConstructorVisitor(c.getSimpleName(), fieldName, multiVariable.getTypeExpression()));
                } else {
                    doAfterVisit(new AddConstructorParameterAndAssignment(constructor, fieldName, multiVariable.getTypeExpression()));
                }
            }
        }
        return mv;
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
            if (getCursor().getParent() != null) {
                Object n = getCursor().getParent().getValue();
                if (n instanceof ClassDeclaration) {
                    ClassDeclaration classDecl = (ClassDeclaration) n;
                    JavaType.FullyQualified typeFqn = TypeUtils.asFullyQualified(type.getType());
                    if (typeFqn != null && classDecl.getKind() == ClassDeclaration.Kind.Type.Class && className.equals(classDecl.getSimpleName())) {
                        JavaTemplate.Builder template = JavaTemplate.builder("" +
                                classDecl.getSimpleName() + "(" + typeFqn.getClassName() + " " + fieldName + ") {\n" +
                                "this." + fieldName + " = " + fieldName + ";\n" +
                                "}\n"
                        ).contextSensitive();
                        FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
                        if (fq != null) {
                            template.imports(fq.getFullyQualifiedName());
                            maybeAddImport(fq);
                        }
                        Optional<Statement> firstMethod = block.getStatements().stream().filter(MethodDeclaration.class::isInstance).findFirst();

                        return firstMethod.map(statement ->
                                (J) template.build()
                                    .apply(getCursor(),
                                        statement.getCoordinates().before()
                                    )
                            )
                            .orElseGet(() ->
                                template.build()
                                    .apply(
                                        getCursor(),
                                        block.getCoordinates().lastStatement()
                                    )
                            );
                    }
                }
            }
            return block;
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
                methodType = fq.getClassName();
            } else {
                throw new IllegalArgumentException("Unable to determine parameter type");
            }
        }

        @Override
        public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, p);
            if (md == this.constructor && md.getBody() != null) {
                List<J> params = md.getParameters().stream().filter(s -> !(s instanceof J.Empty)).collect(Collectors.toList());
                String paramsStr = Stream.concat(params.stream()
                        .map(s -> "#{}"), Stream.of(methodType + " " + fieldName)).collect(Collectors.joining(", "));

                md = JavaTemplate.builder(paramsStr)
                    .contextSensitive()
                    .build()
                    .apply(
                        getCursor(),
                        md.getCoordinates().replaceParameters(),
                        params.toArray()
                    );
                updateCursor(md);

                //noinspection ConstantConditions
                md = JavaTemplate.builder("this." + fieldName + " = " + fieldName + ";")
                    .contextSensitive()
                    .build()
                    .apply(
                        getCursor(),
                        md.getBody().getCoordinates().lastStatement()
                    );
            }
            return md;
        }
    }
}
