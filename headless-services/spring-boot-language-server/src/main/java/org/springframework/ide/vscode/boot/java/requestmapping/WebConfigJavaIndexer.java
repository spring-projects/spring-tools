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

import org.apache.commons.lang3.function.TriConsumer;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Range;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.Builder;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class WebConfigJavaIndexer {
	
	public static final String CONFIGURE_API_VERSIONING_METHOD = "configureApiVersioning";

	public static final String CONFIGURE_MVC_PATH_MATCHING_METHOD = "configurePathMatch";
	public static final String CONFIGURE_FLUX_PATH_MATCHING_METHOD = "configurePathMatching";

	public static final String ADD_PATH_PREFIX = "addPathPrefix";
	public static final String SET_VERSION_PARSER = "setVersionParser";
	public static final String USE_QUERY_PARAM = "useQueryParam";
	public static final String USE_PATH_SEGMENT = "usePathSegment";
	public static final String USE_REQUEST_HEADER = "useRequestHeader";
	public static final String ADD_SUPPORTED_VERSIONS = "addSupportedVersions";
	
	public static final Set<String> VERSIONING_CONFIG_METHODS = Set.of(
			USE_PATH_SEGMENT,
			USE_QUERY_PARAM,
			USE_REQUEST_HEADER
	);

	
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
		MethodDeclaration configurePathMethod = findMethod(type, webConfigType, Set.of(CONFIGURE_MVC_PATH_MATCHING_METHOD, CONFIGURE_FLUX_PATH_MATCHING_METHOD));

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
						invocationExtractor.extractParameters(doc, methodInvocation, builder);
					}
				}
				
				return super.visit(methodInvocation);
			}
		});

	}
	
	public static MethodDeclaration findMethod(TypeDeclaration type, ITypeBinding webConfigurerType, String methodName) {
		return findMethod(type, webConfigurerType, Set.of(methodName));
	}
	
	private static MethodDeclaration findMethod(TypeDeclaration type, ITypeBinding webConfigurerType, Set<String> methodNames) {
		IMethodBinding[] webConfigurerMethods = webConfigurerType.getDeclaredMethods();
		if (webConfigurerMethods == null) {
			return null;
		}
		
		IMethodBinding configureVersioningMethod = null;
		for (IMethodBinding method : webConfigurerMethods) {
			if (methodNames.contains(method.getName())) {
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
		
		result.put(ADD_SUPPORTED_VERSIONS, new MultipleArgumentsExtractor(apiConfigurerInterfaces, (doc, expression, webconfigBuilder) -> {
			String[] expressionValueAsArray = ASTUtils.getExpressionValueAsArray(expression, (dep) -> {});
			for (String supportedVersion : expressionValueAsArray) {
				webconfigBuilder.supportedVersion(supportedVersion);
			}
		}));
		
		result.put(USE_REQUEST_HEADER, new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (doc, expression, webconfigBuilder) -> {
			try {
				String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
				if (value != null) {
					ASTNode parent = expression.getParent();
					Range range = doc.toRange(parent.getStartPosition(), parent.getLength());
					webconfigBuilder.versionStrategy("Request Header: " + value, range);
				}
			}
			catch (BadLocationException e) {
			}
		}));

		result.put(USE_PATH_SEGMENT, new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (doc, expression, webconfigBuilder) -> {
			try {
				String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
				if (value != null) {
					ASTNode parent = expression.getParent();
					Range range = doc.toRange(parent.getStartPosition(), parent.getLength());
					webconfigBuilder.versionStrategy("Path Segment: " + value, range);
				}
			}
			catch (BadLocationException e) {
			}
		}));

		result.put(USE_QUERY_PARAM, new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (doc, expression, webconfigBuilder) -> {
			try {
				String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
				if (value != null) {
					ASTNode parent = expression.getParent();
					Range range = doc.toRange(parent.getStartPosition(), parent.getLength());
					webconfigBuilder.versionStrategy("Query Param: " + value, range);
				}
			}
			catch (BadLocationException e) {
			}
		}));

		result.put(SET_VERSION_PARSER, new SingleArgumentExtractor(apiConfigurerInterfaces, 0, (doc, expression, webconfigBuilder) -> {
			ITypeBinding typeBinding = expression.resolveTypeBinding();
			if (typeBinding != null) {
				webconfigBuilder.versionParser(typeBinding.getQualifiedName());
			}
		}));


		Set<String> pathConfigurerInterfaces = Set.of(
				Annotations.WEB_MVC_PATH_MATCH_CONFIGURER_INTERFACE,
				Annotations.WEB_FLUX_PATH_MATCH_CONFIGURER_INTERFACE
		);
		
		result.put(ADD_PATH_PREFIX, new SingleArgumentExtractor(pathConfigurerInterfaces, 0, (doc, expression, webconfigBuilder) -> {
			String value = ASTUtils.getExpressionValueAsString(expression, (d) -> {});
			if (value != null) {
				webconfigBuilder.pathPrefix(value);
			}
		}));

		
		return result;
	}
	
	interface MethodInvocationExtractor {
		Set<String> getTargetInvocationType();
		void extractParameters(TextDocument doc, MethodInvocation methodInvocation, WebConfigIndexElement.Builder builder);
	}

	record SingleArgumentExtractor (Set<String> invocationTargetType, int argumentNo, TriConsumer<TextDocument, Expression, WebConfigIndexElement.Builder> consumer) implements MethodInvocationExtractor {
		
		@Override
		public Set<String> getTargetInvocationType() {
			return invocationTargetType;
		}
		
		public void extractParameters(TextDocument doc, MethodInvocation methodInvocation, WebConfigIndexElement.Builder builder) {
			@SuppressWarnings("unchecked")
			List<Expression> arguments = methodInvocation.arguments();
			Expression expression = arguments.get(argumentNo);
			consumer.accept(doc, expression, builder);
		}
		
	}
	
	record MultipleArgumentsExtractor (Set<String> invocationTargetType, TriConsumer<TextDocument, Expression, WebConfigIndexElement.Builder> consumer) implements MethodInvocationExtractor {
		
		@Override
		public Set<String> getTargetInvocationType() {
			return invocationTargetType;
		}
		
		public void extractParameters(TextDocument doc, MethodInvocation methodInvocation, WebConfigIndexElement.Builder builder) {
			@SuppressWarnings("unchecked")
			List<Expression> arguments = methodInvocation.arguments();
			for (Expression expression : arguments) {
				consumer.accept(doc, expression, builder);
			}
		}
		
	}
	
}
