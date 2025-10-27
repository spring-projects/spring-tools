/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.security5;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

public class AuthorizeHttpRequests extends Recipe {

    private static final String MSG_ADD_COMMENT = "add-comment";

    private static final String AUTHORIZE_HTTP_REQUESTS = "authorizeHttpRequests";

    private static final MethodMatcher MATCH_AUTHORIZE_REQUESTS = new MethodMatcher(
            "org.springframework.security.config.annotation.web.builders.HttpSecurity authorizeRequests(..)");

    private static final MethodMatcher MATCH_ACCESS_DECISION_MANAGER = new MethodMatcher(
            "org.springframework.security.config.annotation.web.configurers.AbstractInterceptUrlConfigurer$AbstractInterceptUrlRegistry accessDecisionManager(..)");

    @Override
    public String getDisplayName() {
        return "Replace `HttpSecurity.authorizeRequests(...)` with `HttpSecurity.authorizeHttpRequests(...)` and `ExpressionUrlAuthorizationConfigurer`, `AbstractInterceptUrlConfigurer` with `AuthorizeHttpRequestsConfigurer`, etc";
    }

    @Override
    public String getDescription() {
        return "Replace `HttpSecurity.authorizeRequests(...)` deprecated in Spring Security 6 with `HttpSecurity.authorizeHttpRequests(...)` and all method calls on the resultant object respectively. Replace deprecated `AbstractInterceptUrlConfigurer` and its deprecated subclasses with `AuthorizeHttpRequestsConfigurer` and its corresponding subclasses.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private void changeTypesAfterVisit() {
                doAfterVisit(new ChangeType(
                        "org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.ExpressionInterceptUrlRegistry",
                        "org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry",
                        false).getVisitor());
                doAfterVisit(new ChangeType(
                        "org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer",
                        "org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer",
                        false).getVisitor());
                doAfterVisit(new ChangeType(
                        "org.springframework.security.config.annotation.web.configurers.AbstractInterceptUrlConfigurer",
                        "org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer",
                        false).getVisitor());
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(method, ctx);
                if (visited instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) visited;
                    JavaType.Method methodType = method.getMethodType();
                    if (methodType != null) {
                        if (MATCH_AUTHORIZE_REQUESTS.matches(methodType)) {
                            changeTypesAfterVisit();
                            return processAuthorizeRequests(m);
                        } else if (MATCH_ACCESS_DECISION_MANAGER.matches(methodType)) {
                            changeTypesAfterVisit();
                            return processAccessDecisionManager(m, ctx);
                        }
                    }
                    String commentToAdd = getCursor().pollMessage(MSG_ADD_COMMENT);
                    if (commentToAdd != null) {
                        return addTextCommentAfterSelect(m, commentToAdd);
                    }
                }
                return visited;
            }

            private J.MethodInvocation processAuthorizeRequests(J.MethodInvocation m) {
                JavaType.Method methodType = m.getMethodType();
                JavaType.Method newMethodType = methodType.getDeclaringType().getMethods().stream()
                        .filter(nm -> AUTHORIZE_HTTP_REQUESTS.equals(nm.getName()))
                        .filter(nm -> nm.getParameterTypes().size() == methodType.getParameterTypes().size())
                        .findFirst().orElse(null);
                if (newMethodType != null) {
                    m = m
                            .withName(m.getName().withSimpleName(AUTHORIZE_HTTP_REQUESTS))
                            .withMethodType(newMethodType);
                }
                return m;
            }

            private J processAccessDecisionManager(J.MethodInvocation m, ExecutionContext ctx) {
                StringBuilder commentText = new StringBuilder();
                commentText.append("TODO: replace removed '.");
                commentText.append(m.getSimpleName());
                commentText.append('(');
                commentText.append(String.join(", ", m.getArguments().stream().map(a -> a.print(getCursor())).toArray(String[]::new)));
                commentText.append(");' with appropriate call to 'access(AuthorizationManager)' after antMatcher(...) call etc.");

                List<Comment> newComments = new ArrayList<>(m.getComments());
                newComments.addAll(m.getSelect().getComments());

                Expression selectExpr = m.getSelect();
                Cursor parentInvocationCursor = getCursor().getParent(2);
                if (parentInvocationCursor == null || !(parentInvocationCursor.getValue() instanceof J.MethodInvocation)) {
                    // top level method invocation
                    newComments.add(new TextComment(true, commentText.toString(), newComments.isEmpty() ? "\n" + m.getPrefix().getIndent() : newComments.get(0).getSuffix(), Markers.EMPTY));
                    selectExpr = selectExpr.withPrefix(m.getPrefix());
                } else {
                    // parent is method invocation
                    parentInvocationCursor.putMessage(MSG_ADD_COMMENT, commentText.toString());
                }
                return selectExpr.withComments(newComments);
            }

            private J.MethodInvocation addTextCommentAfterSelect(J.MethodInvocation m, String s) {
                J.MethodInvocation.Padding padding = m.getPadding();
                Space afterSelect = padding.getSelect().getAfter();
                List<Comment> newComments = new ArrayList<>(afterSelect.getComments());
                newComments.add(new TextComment(true, s, newComments.isEmpty() ? "\n" + afterSelect.getIndent() : newComments.get(0).getSuffix(), Markers.EMPTY));
                JRightPadded<Expression> paddedSelect = padding.getSelect().withAfter(afterSelect.withComments(newComments));
                return new J.MethodInvocation(m.getId(), m.getPrefix(), m.getMarkers(), paddedSelect, padding.getTypeParameters(), m.getName(), padding.getArguments(), m.getMethodType());
            }
        };
    }
}
