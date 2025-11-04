/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;

/**
 * @author Martin Lippert
 */
public class WebfluxUtils {

	public static final String ROUTER_FUNCTION_TYPE = "org.springframework.web.reactive.function.server.RouterFunction";
	public static final String ROUTER_FUNCTIONS_TYPE = "org.springframework.web.reactive.function.server.RouterFunctions";
	public static final String REQUEST_PREDICATES_TYPE = "org.springframework.web.reactive.function.server.RequestPredicates";
	
	public static final String MVC_ROUTER_FUNCTION_TYPE = "org.springframework.web.servlet.function.RouterFunction";
	public static final String MVC_ROUTER_FUNCTIONS_TYPE = "org.springframework.web.servlet.function.RouterFunctions";

	public static final String MVC_REQUEST_PREDICATES_TYPE = "org.springframework.web.servlet.function.RequestPredicates";

	public static final String REQUEST_PREDICATE_PATH_METHOD = "path";
	public static final String REQUEST_PREDICATE_METHOD_METHOD = "method";
	public static final String REQUEST_PREDICATE_ACCEPT_TYPE_METHOD = "accept";
	public static final String REQUEST_PREDICATE_CONTENT_TYPE_METHOD = "contentType";
	public static final String REQUEST_PREDICATE_NEST_METHOD = "nest";

	public static final Set<String> REQUEST_PREDICATE_HTTPMETHOD_METHODS = new HashSet<>(Arrays.asList("GET", "POST", "DELETE", "PUT", "PATCH", "HEAD", "OPTIONS"));
	public static final Set<String> REQUEST_PREDICATE_ALL_PATH_METHODS = new HashSet<>(Arrays.asList(REQUEST_PREDICATE_PATH_METHOD, "GET", "POST", "DELETE", "PUT", "PATCH", "HEAD", "OPTIONS"));


	public static boolean isFunctionalWebRouterBean(MethodDeclaration method) {
		Type returnType = method.getReturnType2();
		if (returnType != null) {
			ITypeBinding resolvedBinding = returnType.resolveBinding();
			if (resolvedBinding != null && (
					WebfluxUtils.ROUTER_FUNCTION_TYPE.equals(resolvedBinding.getBinaryName())
					|| WebfluxUtils.MVC_ROUTER_FUNCTION_TYPE.equals(resolvedBinding.getBinaryName()))) {
				return true;
			}
		}
		return false;
	}
	
	public static StringLiteral extractStringLiteralArgument(MethodInvocation node) {
		List<?> arguments = node.arguments();
		if (arguments != null && arguments.size() > 0) {
			Object object = arguments.get(0);
			if (object instanceof StringLiteral) {
				return (StringLiteral) object;
			}
		}
		return null;
	}
	
	public static QualifiedName extractQualifiedNameArgument(MethodInvocation node) {
		List<?> arguments = node.arguments();
		if (arguments != null && arguments.size() > 0) {
			Object object = arguments.get(0);
			if (object instanceof QualifiedName) {
				return (QualifiedName) object;
			}
		}
		return null;
	}
	
	public static SimpleName extractSimpleNameArgument(MethodInvocation node) {
		List<?> arguments = node.arguments();
		if (arguments != null && arguments.size() > 0) {
			Object object = arguments.get(0);
			if (object instanceof SimpleName sn) {
				return sn;
			}
			else if (object instanceof QualifiedName qn) {
				return qn.getName();
			}
		}
		return null;
	}

	public static boolean isRouteMethodInvocation(IMethodBinding methodBinding) {
		if (ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {
			String name = methodBinding.getName();
			if ("route".equals(name)) {
				return true;
			}
		}
		else if (ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {
			String name = methodBinding.getName();
			if ("andRoute".equals(name)) {
				return true;
			}
		}
		
		return false;
	}
	
	public static String getMediaType(String constantRep) {
		if (constantRep == null) {
			return null;
		}
		
		try {
			if (constantRep.endsWith("_VALUE")) {
				constantRep = constantRep.substring(0, constantRep.lastIndexOf("_VALUE"));
			}
			
			MediaTypeMapping mediaType = MediaTypeMapping.valueOf(constantRep);
			return mediaType.getMediaType();
		}
		catch (IllegalArgumentException e) {
			return constantRep;
		}
	}
	
	public static String getStringRep(String[] multipleTypes, Function<String, String> valueConverter) {
		if (multipleTypes == null || multipleTypes.length == 0) return null;
		
		StringBuilder result = new StringBuilder(valueConverter.apply(multipleTypes[0]));
		for (int i = 1; i < multipleTypes.length; i++) {
			result.append(",");
			result.append(valueConverter.apply(multipleTypes[i]));
		}
		
		return result.toString();
	}


	

}
