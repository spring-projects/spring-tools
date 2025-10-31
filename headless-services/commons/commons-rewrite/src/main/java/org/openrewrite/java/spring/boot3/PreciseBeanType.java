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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

public class PreciseBeanType extends Recipe {
    private static final String BEAN = "org.springframework.context.annotation.Bean";

    private static final String MSG_KEY = "returnType";

    @Override
    public String getDisplayName() {
        return "Bean methods should return concrete types";
    }

    @Override
    public String getDescription() {
        return "Replace Bean method return types with concrete types being returned. This is required for Spring 6 AOT.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(BEAN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                Object o = getCursor().pollMessage(MSG_KEY);
                if (o != null && (method.getReturnTypeExpression() != null && !o.equals(method.getReturnTypeExpression().getType())) && isBeanMethod(m)) {
                    if (o instanceof JavaType.FullyQualified) {
                        JavaType.FullyQualified actualType = (JavaType.FullyQualified) o;
                        if (m.getReturnTypeExpression() instanceof J.Identifier) {
                            J.Identifier identifierReturnExpr = (J.Identifier) m.getReturnTypeExpression();
                            maybeAddImport(actualType);
                            if (identifierReturnExpr.getType() instanceof JavaType.FullyQualified) {
                                maybeRemoveImport((JavaType.FullyQualified) identifierReturnExpr.getType());
                            }
                            m = m.withReturnTypeExpression(identifierReturnExpr
                                    .withType(actualType)
                                    .withSimpleName(actualType.getClassName())
                            );
                        } else if (m.getReturnTypeExpression() instanceof J.ParameterizedType) {
                            J.ParameterizedType parameterizedType = (J.ParameterizedType) m.getReturnTypeExpression();
                            maybeAddImport(actualType);
                            if (parameterizedType.getType() instanceof JavaType.FullyQualified) {
                                maybeRemoveImport((JavaType.FullyQualified) parameterizedType.getType());
                            }
                            m = m.withReturnTypeExpression(parameterizedType
                                    .withType(actualType)
                                    .withClazz(TypeTree.build(actualType.getClassName()).withType(actualType))
                            );
                        }

                    } else if (o instanceof JavaType.Array) {
                        JavaType.Array actualType = (JavaType.Array) o;
                        if (m.getReturnTypeExpression() instanceof J.ArrayType && actualType.getElemType() instanceof JavaType.FullyQualified) {
                            JavaType.FullyQualified actualElementType = (JavaType.FullyQualified) actualType.getElemType();
                            J.ArrayType arrayType = (J.ArrayType) m.getReturnTypeExpression();
                            maybeAddImport(actualElementType);
                            if (arrayType.getElementType() instanceof JavaType.FullyQualified) {
                                maybeRemoveImport((JavaType.FullyQualified) arrayType.getElementType());
                            }
                            m = m.withReturnTypeExpression(arrayType
                                    .withElementType(TypeTree.build(actualElementType.getClassName()).withType(actualType))
                            );
                        }
                    }
                }
                return m;
            }

            private boolean isBeanMethod(J.MethodDeclaration m) {
                for (J.Annotation leadingAnnotation : m.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(leadingAnnotation.getType(), BEAN)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
                if (_return.getExpression() != null && _return.getExpression().getType() != null) {
                    Cursor methodCursor = getCursor();
                    while (methodCursor != null && !(methodCursor.getValue() instanceof J.Lambda || methodCursor.getValue() instanceof J.MethodDeclaration)) {
                        methodCursor = methodCursor.getParent();
                    }
                    if (methodCursor != null && methodCursor.getValue() instanceof J.MethodDeclaration) {
                        methodCursor.putMessage(MSG_KEY, _return.getExpression().getType());
                    }
                }
                return super.visitReturn(_return, ctx);
            }
        });
    }
}
