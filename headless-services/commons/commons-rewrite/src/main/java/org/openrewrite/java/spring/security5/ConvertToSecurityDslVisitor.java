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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Markup;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class ConvertToSecurityDslVisitor<P> extends JavaIsoVisitor<P> {

    private static final String MSG_FLATTEN_CHAIN = "http-security-dsl-flatten-invocation-chain";
    private static final String MSG_TOP_INVOCATION = "top-method-invocation";

    private static final String FQN_CUSTOMIZER = "org.springframework.security.config.Customizer";
    private static final JavaType.FullyQualified CUSTOMIZER_SHALLOW_TYPE = JavaType.ShallowClass.build(FQN_CUSTOMIZER);

    private static final MethodMatcher XSS_PROTECTION_ENABLED = new MethodMatcher("org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.XXssConfig xssProtectionEnabled(boolean)");

    private final String securityFqn;
    private final Collection<String> convertableMethods;

    /**
     * Optionally used to determine the behavior for any convertableMethods which have an argument.
     * Each key should be a method name from convertableMethods.
     * A non-null value will be used to create a new methodInvocation with that name,
     * and the existing arg will be moved to that new methodInvocation.
     * A null value will keep the existing argument in the converted method.
     */
    private final Map<String, String> argReplacements;

    /**
     * Optionally used to specify replacement method names if they do not match the original method names
     */
    private final Map<String, String> methodRenames;

    public ConvertToSecurityDslVisitor(String securityFqn, Collection<String> convertableMethods) {
        this(securityFqn, convertableMethods, new HashMap<>());
    }

    public ConvertToSecurityDslVisitor(String securityFqn, Collection<String> convertableMethods,
                                       Map<String, String> argReplacements) {
        this(securityFqn, convertableMethods, argReplacements, new HashMap<>());
    }

    public ConvertToSecurityDslVisitor(String securityFqn, Collection<String> convertableMethods,
                                       Map<String, String> argReplacements, Map<String, String> methodRenames) {
        this.securityFqn = securityFqn;
        this.convertableMethods = convertableMethods;
        this.argReplacements = argReplacements;
        this.methodRenames = methodRenames;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation initialMethod, P executionContext) {
        J.MethodInvocation method = super.visitMethodInvocation(initialMethod, executionContext);
        if (isApplicableMethod(method)) {
            J.MethodInvocation m = method;
            method = createDesiredReplacement(method)
                    .map(newMethodType -> {
                        List<J.MethodInvocation> chain = computeAndMarkChain();
                        boolean keepArg = keepArg(m.getSimpleName());
                        String paramName = keepArg ? "configurer" : generateParamNameFromMethodName(m.getSimpleName());
                        return m
                                .withMethodType(newMethodType)
                                .withName(m.getName().withSimpleName(newMethodType.getName()))
                                .withArguments(ListUtils.concat(
                                                keepArg ? m.getArguments().get(0) : null,
                                                Collections.singletonList(chain.isEmpty() ?
                                                        createDefaultsCall() :
                                                        createLambdaParam(paramName, newMethodType.getParameterTypes().get(keepArg ? 1 : 0), chain))
                                        )
                                );
                    })
                    .orElse(method);
        }
        Boolean msg = getCursor().pollMessage(MSG_FLATTEN_CHAIN);
        if (Boolean.TRUE.equals(msg)) {
            method = requireNonNull(method.getSelect())
                    .withPrefix(method.getPrefix())
                    .withComments(method.getComments());
        }
        // Auto-format the top invocation call if anything has changed down the tree
        Cursor grandParent = getCursor().getParent(2);
        if (initialMethod != method && (grandParent == null || !(grandParent.getValue() instanceof J.MethodInvocation))) {
            method = autoFormat(method, executionContext);
        }
        return method;
    }

    private static String generateParamNameFromMethodName(String n) {
        int i = n.length() - 1;
        //noinspection StatementWithEmptyBody
        for (; i >= 0 && Character.isLowerCase(n.charAt(i)); i--) {}
        if (i >= 0) {
            return StringUtils.uncapitalize(i == 0 ? n : n.substring(i));
        }
        return n;
    }

    private J.Lambda createLambdaParam(String paramName, JavaType paramType, List<J.MethodInvocation> chain) {
        J.Identifier param = createIdentifier(paramName, paramType);
        J.MethodInvocation body = unfoldMethodInvocationChain(createIdentifier(paramName, paramType), chain);
        return new J.Lambda(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                new J.Lambda.Parameters(Tree.randomId(), Space.EMPTY, Markers.EMPTY, false, Collections.singletonList(new JRightPadded<>(param, Space.EMPTY, Markers.EMPTY))),
                Space.build(" ", emptyList()),
                body,
                JavaType.Primitive.Void
        );
    }

    private J.Identifier createIdentifier(String name, JavaType type) {
        return new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), name, type, null);
    }

    private J.MethodInvocation unfoldMethodInvocationChain(J.Identifier core, List<J.MethodInvocation> chain) {
        Expression select = core;
        J.MethodInvocation invocation = null;
        for (J.MethodInvocation inv : chain) {
            invocation = inv.withSelect(select);
            if (XSS_PROTECTION_ENABLED.matches(invocation)) {
                if (J.Literal.isLiteralValue(invocation.getArguments().get(0), false)) {
                    invocation = invocation.withName(invocation.getName().withSimpleName("disable")).withArguments(null);
                    JavaType.Method methodType = invocation.getMethodType();
                    if (methodType != null) {
                        methodType = methodType.withParameterNames(emptyList()).withParameterTypes(emptyList());
                        invocation = invocation.withMethodType(methodType).withName(invocation.getName().withType(methodType));
                    }
                } else {
                    // Enabled by default; but returning `null` will cause issues, so we use `and()` as a placeholder
                    invocation = invocation.withName(invocation.getName().withSimpleName("and")).withArguments(null);
                    JavaType.Method methodType = invocation.getMethodType();
                    if (methodType != null) {
                        methodType = methodType.withParameterNames(emptyList()).withParameterTypes(emptyList());
                        invocation = invocation.withMethodType(methodType).withName(invocation.getName().withType(methodType));
                    }
                }
            }
            select = invocation;
        }
        // Check if top-level invocation to remove the prefix as the prefix is space before the root call, i.e. before httpSecurity identifier. We don't want to have inside the lambda
        assert invocation != null;
        if (invocation.getMarkers().getMarkers().stream().filter(Markup.Info.class::isInstance).map(Markup.Info.class::cast).anyMatch(marker -> MSG_TOP_INVOCATION.equals(marker.getMessage()))) {
            invocation = invocation
                    .withMarkers(invocation.getMarkers().removeByType(Markup.Info.class))
                    .withPrefix(Space.EMPTY);
        }
        return invocation;
    }

    private boolean isApplicableMethod(J.MethodInvocation m) {
        JavaType.Method type = m.getMethodType();
        if (type != null) {
            JavaType.FullyQualified declaringType = type.getDeclaringType();
            return securityFqn.equals(declaringType.getFullyQualifiedName()) &&
                   (type.getParameterTypes().isEmpty() || hasHandleableArg(m)) &&
                   convertableMethods.contains(m.getSimpleName());
        }
        return false;
    }

    private boolean hasHandleableArg(J.MethodInvocation m) {
        return argReplacements.containsKey(m.getSimpleName()) &&
               m.getMethodType() != null &&
               m.getMethodType().getParameterTypes().size() == 1 &&
               !TypeUtils.isAssignableTo(FQN_CUSTOMIZER, m.getMethodType().getParameterTypes().get(0));
    }

    private Optional<JavaType.Method> createDesiredReplacement(J.MethodInvocation m) {
        JavaType.Method methodType = m.getMethodType();
        if (methodType == null) {
            return Optional.empty();
        }
        JavaType.Parameterized customizerArgType = new JavaType.Parameterized(null,
                CUSTOMIZER_SHALLOW_TYPE, Collections.singletonList(methodType.getReturnType()));
        boolean keepArg = keepArg(m.getSimpleName());
        List<String> paramNames = keepArg ? ListUtils.concat(methodType.getParameterNames(), "arg1") :
                Collections.singletonList("arg0");
        List<JavaType> paramTypes = keepArg ? ListUtils.concat(methodType.getParameterTypes(), customizerArgType) :
                Collections.singletonList(customizerArgType);
        return Optional.of(methodType.withReturnType(methodType.getDeclaringType())
                .withName(methodRenames.getOrDefault(methodType.getName(), methodType.getName()))
                .withParameterNames(paramNames)
                .withParameterTypes(paramTypes)
        );
    }

    private boolean keepArg(String methodName) {
        return argReplacements.containsKey(methodName) && argReplacements.get(methodName) == null;
    }

    private Optional<JavaType.Method> createDesiredReplacementForArg(J.MethodInvocation m) {
        JavaType.Method methodType = m.getMethodType();
        if (methodType == null || !hasHandleableArg(m) || keepArg(m.getSimpleName()) || !(methodType.getReturnType() instanceof JavaType.Class)) {
            return Optional.empty();
        }
        return Optional.of(
                methodType.withName(argReplacements.get(m.getSimpleName()))
                        .withDeclaringType((JavaType.FullyQualified) methodType.getReturnType())
        );
    }

    // this method is unused in this repo, but, useful in Spring Tool Suite integration
    @SuppressWarnings("unused")
    public boolean isApplicableTopLevelMethodInvocation(J.MethodInvocation m) {
        if (isApplicableMethod(m)) {
            return true;
        } else if (m.getSelect() instanceof J.MethodInvocation) {
            return isApplicableTopLevelMethodInvocation((J.MethodInvocation) m.getSelect());
        }
        return false;
    }

    private boolean isApplicableCallCursor(@Nullable Cursor c) {
        if (c == null) {
            return false;
        }

        if (!(c.getValue() instanceof J.MethodInvocation)) {
            return false;
        }

        J.MethodInvocation inv = c.getValue();
        return !isAndMethod(inv) && !isDisableMethod(inv);
    }

    private List<J.MethodInvocation> computeAndMarkChain() {
        List<J.MethodInvocation> chain = new ArrayList<>();
        Cursor cursor = getCursor();
        J.MethodInvocation initialMethodInvocation = cursor.getValue();
        createDesiredReplacementForArg(initialMethodInvocation).ifPresent(methodType ->
                chain.add(initialMethodInvocation.withName(
                        initialMethodInvocation.getName().withType(methodType).withSimpleName(methodType.getName()))));
        cursor = cursor.getParent(2);
        for (; isApplicableCallCursor(cursor); cursor = cursor.getParent(2)) {
            cursor.putMessage(MSG_FLATTEN_CHAIN, true);
            chain.add(cursor.getValue());
        }
        if (cursor != null && cursor.getValue() instanceof J.MethodInvocation) {
            if (isAndMethod(cursor.getValue())) {
                cursor.putMessage(MSG_FLATTEN_CHAIN, true);
                cursor = cursor.getParent(2);
            } else if (isDisableMethod(cursor.getValue())) {
                cursor.putMessage(MSG_FLATTEN_CHAIN, true);
                chain.add(cursor.getValue());
                cursor = cursor.getParent(2);
            }
        }
        if (cursor == null || chain.isEmpty()) {
            return emptyList();
        }
        if (!(cursor.getValue() instanceof J.MethodInvocation)) {
            // top invocation is at the end of the chain - mark it. We'd need to strip off prefix from this invocation later
            J.MethodInvocation topInvocation = chain.remove(chain.size() - 1);
            // removed above, now add it back with the marker
            chain.add(topInvocation.withMarkers(topInvocation.getMarkers().addIfAbsent(new Markup.Info(Tree.randomId(), MSG_TOP_INVOCATION, null))));
        }
        return chain;
    }

    private boolean isAndMethod(J.MethodInvocation method) {
        return "and".equals(method.getSimpleName()) &&
               (method.getArguments().isEmpty() || method.getArguments().get(0) instanceof J.Empty) &&
               TypeUtils.isAssignableTo(securityFqn, method.getType());
    }

    private boolean isDisableMethod(J.MethodInvocation method) {
        return new MethodMatcher("org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer disable()", true).matches(method);
    }

    private J.MethodInvocation createDefaultsCall() {
        JavaType.Method methodType = new JavaType.Method(null, 9, CUSTOMIZER_SHALLOW_TYPE, "withDefaults",
                new JavaType.GenericTypeVariable(null, "T", JavaType.GenericTypeVariable.Variance.INVARIANT, null),
                Collections.emptyList(), Collections.emptyList(), null, null, null, null);
        maybeAddImport(methodType.getDeclaringType().getFullyQualifiedName(), methodType.getName());
        return new J.MethodInvocation(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, null,
                new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "withDefaults", null, null),
                JContainer.empty(), methodType)
                .withSelect(null);
    }

}
