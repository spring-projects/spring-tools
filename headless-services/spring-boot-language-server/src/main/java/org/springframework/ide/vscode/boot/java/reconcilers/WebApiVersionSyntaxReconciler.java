/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigJavaIndexer;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxUtils;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.web.accept.SemanticApiVersionParser;

public class WebApiVersionSyntaxReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "API version cannot be parsed into a standard semantic version";

	private final SpringMetamodelIndex springIndex;

	public WebApiVersionSyntaxReconciler(SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(4, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.API_VERSION_SYNTAX_ERROR;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return new ASTVisitor() {

			/**
			 * this in the piece of the reconciler that validates the version attribute of annotations on controllers
			 */
			@Override
			public boolean visit(NormalAnnotation annotation) {
				IAnnotationBinding annotationBinding = annotation.resolveAnnotationBinding();
				if (annotationBinding == null) return super.visit(annotation);
				
				ITypeBinding annotationType = annotationBinding.getAnnotationType();
				if (annotationType == null) return super.visit(annotation);
				
				boolean isMappingAnnotated = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.SPRING_REQUEST_MAPPING);
				if (!isMappingAnnotated) return super.visit(annotation);
				
				@SuppressWarnings("unchecked")
				List<MemberValuePair> attributes = annotation.values();
				attributes.stream()
					.filter(pair -> "version".equals(pair.getName().toString()))
					.forEach(pair -> {
						if (!context.isIndexComplete()) {
							throw new RequiredCompleteIndexException();
						}
						
						if (!isApiVersioningConfiguredWithStanardVersionParser(project)) {
							return;
						}
						
						Expression valueExpression = pair.getValue();
						String versionValue = ASTUtils.getExpressionValueAsString(valueExpression, (d) -> {});

						validateVersion(context, valueExpression, versionValue);
					}
				);
				
				return super.visit(annotation);
			}

			@Override
			public boolean visit(MethodDeclaration method) {
				boolean isWebfluxRouter = WebfluxUtils.isFunctionalWebRouterBean(method);
				if (isWebfluxRouter) {
					if (!context.isCompleteAst()) {
						throw new RequiredCompleteAstException();
					}
				}
					
				return super.visit(method);
			}

			/**
			 * this is the piece of the reconciler that validates version in functional endpoint definitions
			 */
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
				if (methodBinding == null) return super.visit(methodInvocation);
				
				// Check if this is a call to RequestPredicates.version() method
				String methodName = methodBinding.getName();
				if (!"version".equals(methodName)) {
					return super.visit(methodInvocation);
				}
				
				// Check if the declaring class is RequestPredicates (for both WebMVC and WebFlux)
				ITypeBinding declaringClass = methodBinding.getDeclaringClass();
				if (declaringClass == null) {
					return super.visit(methodInvocation);
				}
				
				String declaringClassName = declaringClass.getQualifiedName();
				boolean isWebMvcVersion = WebfluxUtils.MVC_REQUEST_PREDICATES_TYPE.equals(declaringClassName);
				boolean isWebFluxVersion = WebfluxUtils.REQUEST_PREDICATES_TYPE.equals(declaringClassName);
				
				if (!isWebMvcVersion && !isWebFluxVersion) {
					return super.visit(methodInvocation);
				}
				
				// Extract the version argument
				@SuppressWarnings("unchecked")
				List<Expression> arguments = methodInvocation.arguments();
				if (arguments == null || arguments.isEmpty() || arguments.size() > 1) {
					return super.visit(methodInvocation);
				}
				
				if (!context.isIndexComplete()) {
					throw new RequiredCompleteIndexException();
				}
				
				if (!isApiVersioningConfiguredWithStanardVersionParser(project)) {
					return super.visit(methodInvocation);
				}
				
				Expression expression = arguments.get(0);
				String versionValue = ASTUtils.getExpressionValueAsString(expression,  (d) -> {});
				
				// Validate the version
				validateVersion(context, expression, versionValue);
				
				return super.visit(methodInvocation);
			}
			
			/**
			 * this is the piece of the reconciler that looks for changes to web configs and then marks all the potentially affected
			 * controller classes for re-indexing
			 */
			@Override
			public boolean visit(TypeDeclaration type) {
				if (WebConfigJavaIndexer.getWebConfig(type) == null) {
					return super.visit(type);
				}
				
				Arrays.stream(springIndex.getBeansOfProject(project.getElementName()))
					.filter(bean -> isAnnotatedWith(bean, Annotations.CONTROLLER))
					.map(bean -> UriUtil.toFileString(bean.getLocation().getUri()))
					.forEach(file -> context.markForAffetcedFilesIndexing(file));
					
				return super.visit(type);
			}
		};
	}
	
	private String updateVersion(String version) {
		boolean baselineVersion = version.endsWith("+");
		return (baselineVersion ? version.substring(0, version.length() - 1) : version);
	}
	
	private void validateVersion(ReconcilingContext context, ASTNode valueExpression, String versionValue) {
		versionValue = updateVersion(versionValue);

		SemanticApiVersionParser parser = new SemanticApiVersionParser();
		try {
			parser.parseVersion(versionValue);
		}
		catch (IllegalStateException e) {
			ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
					valueExpression.getStartPosition(), valueExpression.getLength());

			context.getProblemCollector().accept(problem);
		}
	}
	
	private boolean isApiVersioningConfiguredWithStanardVersionParser(IJavaProject project) {
		List<WebConfigIndexElement> webConfigs = springIndex.getNodesOfType(project.getElementName(), WebConfigIndexElement.class);
		for (WebConfigIndexElement webConfig : webConfigs) {
			if (!WebConfigIndexElement.DEFAULT_VERSION_PARSER.equals(webConfig.getVersionParser())) {
				return false;
			}
		}
		
		return webConfigs.size() > 0;
	}
	
	private boolean isAnnotatedWith(Bean bean, String annotationType) {
		return Arrays.stream(bean.getAnnotations())
				.filter(annotation -> annotation.getAnnotationType().equals(annotationType))
				.findAny()
				.isPresent();
	}

}
