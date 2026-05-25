/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.Optional;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.openrewrite.marker.Range;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.rewrite.java.NoRequestMappingAnnotation;

public class NoRequestMappingAnnotationReconciler implements JdtAstReconciler {
	
	private static final String PROBLEM_LABEL = "Replace with precise mapping annotation";
	
	private static final String UNSUPPORTED_REQUEST_METHOD = "UNSUPPORTED";
	
	private static final List<String> SUPPORTED_REQUEST_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
	
	private QuickfixRegistry registry;

	public NoRequestMappingAnnotationReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_PRECISE_REQUEST_MAPPING;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(MarkerAnnotation node) {
				processAnnotation(node);
				return false;
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				processAnnotation(node);
				return false;
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				processAnnotation(node);
				return false;
			}
			
			private void processAnnotation(Annotation a) {
				if (a.getParent() instanceof MethodDeclaration) {
					List<String> requestMethods = getRequestMethods(cu, a);
					if (!requestMethods.isEmpty() && !requestMethods.contains(UNSUPPORTED_REQUEST_METHOD)) {
						String uri = docUri.toASCIIString();
						Range range = ReconcileUtils.createOpenRewriteRange(cu, a, null);
						if (requestMethods.size() == 1 && SUPPORTED_REQUEST_METHODS.contains(requestMethods.get(0))) {
							ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, a.getStartPosition(), a.getLength());
							ReconcileUtils.setRewriteFixes(registry, problem, List.of(
									createFixDescriptor(uri, range, requestMethods.get(0)).withPreferred(true),
									new FixDescriptor(org.openrewrite.java.spring.NoRequestMappingAnnotation.class.getName(), List.of(uri),
											"Replace all `@RequestMapping` in file '%s' with `@GetMapping`, `@PostMapping`, etc.".formatted(Paths.get(docUri).getFileName().toString()))
										.withRecipeScope(RecipeScope.FILE),
									new FixDescriptor(org.openrewrite.java.spring.NoRequestMappingAnnotation.class.getName(), List.of(uri),
											"Replace all `@RequestMapping` in project '%s' with `@GetMapping`, `@PostMapping`, etc.".formatted(project.getElementName()))
										.withRecipeScope(RecipeScope.PROJECT)
							));
							context.getProblemCollector().accept(problem);
						} else if (SUPPORTED_REQUEST_METHODS == requestMethods) { // the case of no request methods specified
							ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, a.getStartPosition(), a.getLength());
							ReconcileUtils.setRewriteFixes(registry, problem, 
									requestMethods.stream().map(m -> createFixDescriptor(uri, range, m)).collect(Collectors.toList()));
							context.getProblemCollector().accept(problem);
						}
					}
				}
			}
			
		});
	}
	
	private static FixDescriptor createFixDescriptor(String uri, Range range, String requestMethod) {
		return new FixDescriptor(NoRequestMappingAnnotation.class.getName(), List.of(uri), "Replace with `@%s`".formatted(NoRequestMappingAnnotation.associatedRequestMapping(requestMethod)))
				.withRangeScope(range)
				.withParameters(Map.of("preferredMapping", requestMethod))
				.withRecipeScope(RecipeScope.NODE);
	}
	
	private static List<String> getRequestMethods(CompilationUnit cu, Annotation a) {
		String typeName = a.getTypeName().getFullyQualifiedName();
		if (Annotations.SPRING_REQUEST_MAPPING.equals(typeName)) {
			return getRequestMethods(a);
		} else if (typeName.endsWith("RequestMapping")) {
			ITypeBinding type = a.resolveTypeBinding();
			if (type != null && Annotations.SPRING_REQUEST_MAPPING.equals(type.getQualifiedName())) {
				return getRequestMethods(a);
			}
		}
		return Collections.emptyList();
	}
	
	@SuppressWarnings("unchecked")
	private static List<String> getRequestMethods(Annotation a) {
		if (a.isNormalAnnotation()) {
			for (Object o : ((NormalAnnotation) a).values()) {
				if (o instanceof MemberValuePair) {
					MemberValuePair pair = (MemberValuePair) o;
					if ("method".equals(pair.getName().getIdentifier())) {
						if (pair.getValue() instanceof ArrayInitializer) {
							List<Expression> expressions = ((ArrayInitializer) pair.getValue()).expressions();
							return expressions.stream().map(NoRequestMappingAnnotationReconciler::convertRequestMethodToString).collect(Collectors.toList());
						} else {
							return List.of(convertRequestMethodToString(pair.getValue()));
						}
					}
				}
			}
		}
		return SUPPORTED_REQUEST_METHODS;
	}
	
	private static String convertRequestMethodToString(Expression e) {
		String requestMethod = UNSUPPORTED_REQUEST_METHOD;
		if (e instanceof QualifiedName) {
			requestMethod = ((QualifiedName) e).getName().getIdentifier();
		} else if (e instanceof SimpleName) {
			requestMethod = ((SimpleName) e).getIdentifier();
		}
		return SUPPORTED_REQUEST_METHODS.contains(requestMethod) ? requestMethod : UNSUPPORTED_REQUEST_METHOD;
	}

}
