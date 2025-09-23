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
import java.util.function.Consumer;

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
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class WebConfigIndexer {
	
	public static void indexWebConfig(Bean beanDefinition, TypeDeclaration type, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);
		
		ITypeBinding binding = type.resolveBinding();
		if (binding == null) return;
		
		if (!annotationHierarchies.isAnnotatedWith(binding, Annotations.CONFIGURATION)) {
			return;
		}
		
		ITypeBinding inTypeHierarchy = ASTUtils.findInTypeHierarchy(binding, Set.of(Annotations.WEB_MVC_CONFIGURER_INTERFACE));
		if (inTypeHierarchy == null) {
			return;
		}
		
		if (!context.isFullAst()) { // needs full method bodies to continue
			throw new RequiredCompleteAstException();
		}
		
		Builder builder = new WebConfigIndexElement.Builder();

		MethodDeclaration configureVersioningMethod = findConfigureVersioningMethod(type, inTypeHierarchy);
		if (configureVersioningMethod != null) {
			scanConfigureApiVersioningMethodBody(builder, configureVersioningMethod.getBody(), context, doc);
		}
		
		WebConfigIndexElement webConfigIndexElement = builder.buildFor(beanDefinition.getLocation());
		if (webConfigIndexElement != null) {
			beanDefinition.addChild(webConfigIndexElement);
		}
		
	}
	
	private static void scanConfigureApiVersioningMethodBody(Builder builder, Block body, SpringIndexerJavaContext context, TextDocument doc) {
		if (body == null) {
			return;
		}
		
		builder.isVersionSupported(true);
		
		Map<String, Consumer<MethodInvocation>> apiVersionConfigurerMethods = new HashMap<>();
		apiVersionConfigurerMethods.put("addSupportedVersions", (invocation) -> {

			@SuppressWarnings("unchecked")
			List<Expression> arguments = invocation.arguments();
			for (Expression arg : arguments) {
				String[] expressionValueAsArray = ASTUtils.getExpressionValueAsArray(arg, (dep) -> {});
				for (String supportedVersion : expressionValueAsArray) {
					builder.supportedVersion(supportedVersion);
				}
			}
		});

		apiVersionConfigurerMethods.put("useRequestHeader", (invocation) -> {

			@SuppressWarnings("unchecked")
			List<Expression> arguments = invocation.arguments();
			if (arguments != null && arguments.size() == 1) {
				String value = ASTUtils.getExpressionValueAsString(arguments.get(0), (d) -> {});
				if (value != null) {
					builder.versionStrategy("Request Header: " + value);
				}
			}
		});

		apiVersionConfigurerMethods.put("usePathSegment", (invocation) -> {

			@SuppressWarnings("unchecked")
			List<Expression> arguments = invocation.arguments();
			if (arguments != null && arguments.size() == 1) {
				String value = ASTUtils.getExpressionValueAsString(arguments.get(0), (d) -> {});
				if (value != null) {
					builder.versionStrategy("Path Segment: " + value);
				}
			}
		});

		apiVersionConfigurerMethods.put("useQueryParam", (invocation) -> {

			@SuppressWarnings("unchecked")
			List<Expression> arguments = invocation.arguments();
			if (arguments != null && arguments.size() == 1) {
				String value = ASTUtils.getExpressionValueAsString(arguments.get(0), (d) -> {});
				if (value != null) {
					builder.versionStrategy("Query Param: " + value);
				}
			}
		});

		body.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				String methodName = methodInvocation.getName().toString();
				if (apiVersionConfigurerMethods.containsKey(methodName)) {

					IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
					ITypeBinding declaringClass = methodBinding.getDeclaringClass();

					if (declaringClass != null && Annotations.WEB_MVC_API_VERSION_CONFIGURER_INTERFACE.equals(declaringClass.getQualifiedName())) {
						apiVersionConfigurerMethods.get(methodName).accept(methodInvocation);
					}
				}
				
				return super.visit(methodInvocation);
			}
		});
		
	}

	private static MethodDeclaration findConfigureVersioningMethod(TypeDeclaration type, ITypeBinding webmvcConfigurerType) {
		IMethodBinding[] webConfigurerMethods = webmvcConfigurerType.getDeclaredMethods();
		if (webConfigurerMethods == null) {
			return null;
		}
		
		IMethodBinding configureVersioningMethod = null;
		for (IMethodBinding method : webConfigurerMethods) {
			if ("configureApiVersioning".equals(method.getName())) {
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

}
