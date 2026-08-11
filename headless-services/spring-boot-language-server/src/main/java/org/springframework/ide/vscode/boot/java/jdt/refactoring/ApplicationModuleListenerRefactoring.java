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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * A JDT-based refactoring that replaces a method's {@code @Async}, {@code @Transactional}
 * and {@code @TransactionalEventListener} annotations (all three together) with Spring
 * Modulith's {@code @ApplicationModuleListener}.
 * <p>
 * Only the attributes that {@code @ApplicationModuleListener} actually has an equivalent
 * for are carried over ({@code @Transactional#readOnly()} -&gt; {@code readOnlyTransaction()},
 * {@code @Transactional#propagation()} -&gt; {@code propagation()},
 * {@code @TransactionalEventListener#id()} -&gt; {@code id()},
 * {@code @TransactionalEventListener#condition()} -&gt; {@code condition()}). If any of the
 * three annotations uses an attribute without an equivalent, or the required combination of
 * annotations isn't present, the method is left unchanged to avoid silently dropping behavior.
 * <p>
 * Pass one or more method offsets to convert. When used with a single offset this
 * corresponds to a node-scoped quickfix; with multiple offsets (all occurrences in a file)
 * this corresponds to a file-scoped "fix all" quickfix.
 */
public class ApplicationModuleListenerRefactoring implements JdtRefactoring {

	private static final String ASYNC_FQN = "org.springframework.scheduling.annotation.Async";
	private static final String TRANSACTIONAL_FQN = "org.springframework.transaction.annotation.Transactional";
	private static final String TRANSACTIONAL_EVENT_LISTENER_FQN = "org.springframework.transaction.event.TransactionalEventListener";
	private static final String APPLICATION_MODULE_LISTENER_FQN = "org.springframework.modulith.events.ApplicationModuleListener";

	private static final Set<String> SUPPORTED_TRANSACTIONAL_ATTRIBUTES = Set.of("readOnly", "propagation");
	private static final Set<String> SUPPORTED_EVENT_LISTENER_ATTRIBUTES = Set.of("id", "condition");

	private final int[] methodOffsets;

	/**
	 * @param methodOffsets start positions of the method declarations to convert
	 */
	public ApplicationModuleListenerRefactoring(int... methodOffsets) {
		this.methodOffsets = methodOffsets;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		boolean anyConverted = false;
		for (int offset : methodOffsets) {
			MethodDeclaration method = findMethodAtOffset(cu, offset);
			if (method != null && convertMethod(rewrite, cu, method)) {
				anyConverted = true;
			}
		}

		if (anyConverted) {
			AST ast = cu.getAST();
			JdtRefactorUtils.addImport(rewrite, ast, cu,
					new ClassType(JdtRefactorUtils.extractPackageName(APPLICATION_MODULE_LISTENER_FQN),
							JdtRefactorUtils.extractSimpleName(APPLICATION_MODULE_LISTENER_FQN)));
			JdtRefactorUtils.removeImports(cu, rewrite, ASYNC_FQN, TRANSACTIONAL_FQN, TRANSACTIONAL_EVENT_LISTENER_FQN);
		}
	}

	private boolean convertMethod(ASTRewrite rewrite, CompilationUnit cu, MethodDeclaration method) {
		AST ast = cu.getAST();

		Annotation asyncAnnotation = findAnnotation(method, ASYNC_FQN);
		Annotation transactionalAnnotation = findAnnotation(method, TRANSACTIONAL_FQN);
		Annotation eventListenerAnnotation = findAnnotation(method, TRANSACTIONAL_EVENT_LISTENER_FQN);

		if (asyncAnnotation == null || transactionalAnnotation == null || eventListenerAnnotation == null) {
			return false;
		}

		if (hasArguments(asyncAnnotation)) {
			// '@Async#value()' (executor qualifier) has no equivalent
			return false;
		}

		List<MemberValuePair> newValues = new ArrayList<>();
		if (!collectSupportedAttributes(ast, transactionalAnnotation, SUPPORTED_TRANSACTIONAL_ATTRIBUTES, newValues)) {
			return false;
		}
		if (!collectSupportedAttributes(ast, eventListenerAnnotation, SUPPORTED_EVENT_LISTENER_ATTRIBUTES, newValues)) {
			return false;
		}

		Annotation newAnnotation;
		if (newValues.isEmpty()) {
			newAnnotation = ast.newMarkerAnnotation();
		} else {
			NormalAnnotation normal = ast.newNormalAnnotation();
			@SuppressWarnings("unchecked")
			List<MemberValuePair> values = normal.values();
			values.addAll(newValues);
			newAnnotation = normal;
		}
		newAnnotation.setTypeName(ast.newSimpleName(JdtRefactorUtils.extractSimpleName(APPLICATION_MODULE_LISTENER_FQN)));

		List<Annotation> merged = List.of(asyncAnnotation, transactionalAnnotation, eventListenerAnnotation);
		Annotation earliest = merged.stream().min(Comparator.comparingInt(ASTNode::getStartPosition)).orElseThrow();

		ListRewrite modifiersRewrite = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		modifiersRewrite.replace(earliest, newAnnotation, null);
		for (Annotation a : merged) {
			if (a != earliest) {
				modifiersRewrite.remove(a, null);
			}
		}

		return true;
	}

	/**
	 * Copies the (single) attribute values found on the given annotation, renamed from their
	 * source name to their '@ApplicationModuleListener' target name, into {@code target}.
	 * Returns {@code false} if the annotation uses an attribute that has no equivalent on
	 * '@ApplicationModuleListener'.
	 */
	private boolean collectSupportedAttributes(AST ast, Annotation annotation, Set<String> supportedNames,
			List<MemberValuePair> target) {
		if (annotation.isMarkerAnnotation()) {
			return true;
		}
		if (annotation.isSingleMemberAnnotation()) {
			// bare single-value argument, e.g. '@TransactionalEventListener(SomeEvent.class)' -> unsupported 'classes'/'value'
			return false;
		}
		for (Object o : ((NormalAnnotation) annotation).values()) {
			MemberValuePair pair = (MemberValuePair) o;
			String sourceName = pair.getName().getIdentifier();
			if (!supportedNames.contains(sourceName)) {
				return false;
			}
			MemberValuePair newPair = ast.newMemberValuePair();
			newPair.setName(ast.newSimpleName("readOnly".equals(sourceName) ? "readOnlyTransaction" : sourceName));
			newPair.setValue((Expression) ASTNode.copySubtree(ast, pair.getValue()));
			target.add(newPair);
		}
		return true;
	}

	private boolean hasArguments(Annotation a) {
		if (a.isMarkerAnnotation()) {
			return false;
		}
		if (a.isNormalAnnotation()) {
			return !((NormalAnnotation) a).values().isEmpty();
		}
		return true;
	}

	private Annotation findAnnotation(MethodDeclaration method, String annotationFqn) {
		String simpleName = JdtRefactorUtils.extractSimpleName(annotationFqn);
		for (Object mod : method.modifiers()) {
			if (mod instanceof Annotation a) {
				String name = a.getTypeName().getFullyQualifiedName();
				if (name.equals(annotationFqn) || name.equals(simpleName)) {
					return a;
				}
			}
		}
		return null;
	}

	private static MethodDeclaration findMethodAtOffset(CompilationUnit cu, int offset) {
		ASTNode node = NodeFinder.perform(cu, offset, 0);
		while (node != null && !(node instanceof MethodDeclaration)) {
			node = node.getParent();
		}
		return (MethodDeclaration) node;
	}

}
