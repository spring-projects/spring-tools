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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.Builder;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class WebConfigIndexer {
	
	private static final String CONFIGURE_API_VERSIONING_METHOD = "configureApiVersioning";
	private static final String CONFIGURE_PATH_MATCHING_METHOD = "configurePathMatch";
	
	private static Map<String, MethodInvocationExtractor> methodExtractors = initializeMethodExtractors();
	
	public static void indexWebConfig(Bean beanDefinition, TypeDeclaration type, SpringIndexerJavaContext context, TextDocument doc) {
		ITypeBinding webConfigType = getWebConfig(type);
		if (webConfigType == null) {
			return;
		}
		
		if (!context.isFullAst()) { // needs full method bodies to continue
			throw new RequiredCompleteAstException();
		}
		
		MethodDeclaration configureVersioningMethod = findMethod(type, webConfigType, CONFIGURE_API_VERSIONING_METHOD);
		MethodDeclaration configurePathMethod = findMethod(type, webConfigType, CONFIGURE_PATH_MATCHING_METHOD);

		if (configureVersioningMethod != null || configurePathMethod != null) {
			Builder builder = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG);
			
			if (configureVersioningMethod != null) scanMethodBody(builder, configureVersioningMethod.getBody(), context, doc);
			if (configurePathMethod != null) scanMethodBody(builder, configurePathMethod.getBody(), context, doc);
		
			WebConfigIndexElement webConfigIndexElement = builder.buildFor(beanDefinition.getLocation());
			if (webConfigIndexElement != null) {
				beanDefinition.addChild(webConfigIndexElement);
			}
		}
		
	}

	public static ITypeBinding getWebConfig(TypeDeclaration type) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);
		
		ITypeBinding binding = type.resolveBinding();
		if (binding == null) return null;
		
		if (!annotationHierarchies.isAnnotatedWith(binding, Annotations.CONFIGURATION)) {
			return null;
		}
		
		return ASTUtils.findInTypeHierarchy(binding, Set.of(
				Annotations.WEB_MVC_CONFIGURER_INTERFACE,
				Annotations.WEB_FLUX_CONFIGURER_INTERFACE
		));
	}
	
	private static void scanMethodBody(Builder builder, Block body, SpringIndexerJavaContext context, TextDocument doc) {
		if (body == null) {
			return;
		}
		
		body.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				String methodName = methodInvocation.getName().toString();
				
				MethodInvocationExtractor invocationExtractor = methodExtractors.get(methodName);
				if (invocationExtractor != null) {

					IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
					ITypeBinding declaringClass = methodBinding.getDeclaringClass();

					if (declaringClass != null && invocationExtractor.getTargetInvocationType().contains(declaringClass.getQualifiedName())) {
						invocationExtractor.extractParameters(methodInvocation, builder);
					}
				}
				
				return super.visit(methodInvocation);
			}
		});

	}
	
	private static MethodDeclaration findMethod(TypeDeclaration type, ITypeBinding webmvcConfigurerType, String methodName) {
		IMethodBinding[] webConfigurerMethods = webmvcConfigurerType.getDeclaredMethods();
		if (webConfigurerMethods == null) {
			return null;
		}
		
		IMethodBinding configureVersioningMethod = null;
		for (IMethodBinding method : webConfigurerMethods) {
			if (methodName.equals(method.getName())) {
				configureVersioningMethod = method;
			}
		}
		
		if (configureVersioningMethod == null) {
			return null;
		}
		
		MethodDeclaration[] methods = type.getMethods();
		
		for (MethodDeclaration method : methods) {
			IMethodBinding binding = method.resolveBinding();
			boolean overrides = binding.overrides(configureVersioningMethod);
			if (overrides) {
				return method;
			}
		}

		return null;
	}
	
	private static Map<String, MethodInvocationExtractor> initializeMethodExtractors() {
		Map<String, MethodInvocationExtractor> result = new HashMap<>();
		
		Set<String> apiConfigurerInterfaces = Set.of(
				Annotations.WEB_MVC_API_VERSION_CONFIGURER_INTERFACE,
				Annotations.WEB_FLUX_API_VERSION_CONFIGURER_INTERFACE
		);
		
		result.put("addSupportedVersions", new MultipleArgumentsExtractor(apiConfigurerInterfaces, (expression, webconfigBuilder) -> {
			String[] expressionValueAsArray = ASTUtils.getExpressionValueAsArray(expression, (dep) -> {});
			for (String supportedVersion : expressionValueAsArray) {
				webconfigBuilder.supportedVersion(supportedVersion);
			}
		}));
		
		result.put("useRequestHeader", new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (expression, webconfigBuilder) -> {
			String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
			if (value != null) {
				webconfigBuilder.versionStrategy("Request Header: " + value);
			}
		}));

		result.put("usePathSegment", new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (expression, webconfigBuilder) -> {
			String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
			if (value != null) {
				webconfigBuilder.versionStrategy("Path Segment: " + value);
			}
		}));

		result.put("useQueryParam", new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (expression, webconfigBuilder) -> {
			String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
			if (value != null) {
				webconfigBuilder.versionStrategy("Query Param: " + value);
			}
		}));


		Set<String> pathConfigurerInterfaces = Set.of(
				Annotations.WEB_MVC_PATH_MATCH_CONFIGURER_INTERFACE,
				Annotations.WEB_FLUX_PATH_MATCH_CONFIGURER_INTERFACE
		);
		
		result.put("addPathPrefix", new SingleArgumentExtractor(pathConfigurerInterfaces, 0, (expression, webconfigBuilder) -> {
			String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
			if (value != null) {
				webconfigBuilder.pathPrefix(value);
			}
		}));

		
		return result;
	}
	
	interface MethodInvocationExtractor {
		Set<String> getTargetInvocationType();
		void extractParameters(MethodInvocation methodInvocation, WebConfigIndexElement.Builder builder);
	}

	record SingleArgumentExtractor (Set<String> invocationTargetType, int argumentNo, BiConsumer<Expression, WebConfigIndexElement.Builder> consumer) implements MethodInvocationExtractor {
		
		@Override
		public Set<String> getTargetInvocationType() {
			return invocationTargetType;
		}
		
		public void extractParameters(MethodInvocation methodInvocation, WebConfigIndexElement.Builder builder) {
			@SuppressWarnings("unchecked")
			List<Expression> arguments = methodInvocation.arguments();
			Expression expression = arguments.get(argumentNo);
			consumer.accept(expression, builder);
		}
		
	}
	
	record MultipleArgumentsExtractor (Set<String> invocationTargetType, BiConsumer<Expression, WebConfigIndexElement.Builder> consumer) implements MethodInvocationExtractor {
		
		@Override
		public Set<String> getTargetInvocationType() {
			return invocationTargetType;
		}
		
		public void extractParameters(MethodInvocation methodInvocation, WebConfigIndexElement.Builder builder) {
			@SuppressWarnings("unchecked")
			List<Expression> arguments = methodInvocation.arguments();
			for (Expression expression : arguments) {
				consumer.accept(expression, builder);
			}
		}
		
	}
	
}
