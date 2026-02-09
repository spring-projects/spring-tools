/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;

public class PreciseWebFnTypeChecker implements WebFnTypeChecker {
	
	@Override
	public boolean isRouteMethodInvocation(MethodInvocation method) {
		IMethodBinding methodBinding = method.resolveMethodBinding();
		
		if (WebFnUtils.FLUX_ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				||WebFnUtils.FLUX_ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {
			String name = methodBinding.getName();
			if ("route".equals(name) || "andRoute".equals(name)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isStaticNestInvocation(MethodInvocation method) {
		IMethodBinding methodBinding = method.resolveMethodBinding();

		if (WebFnUtils.FLUX_ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.FLUX_ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {
			String name = methodBinding.getName();
			if ("nest".equals(name)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean isStaticAndInvocation(MethodInvocation method) {
		IMethodBinding methodBinding = method.resolveMethodBinding();

		if (WebFnUtils.FLUX_ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.FLUX_ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_FUNCTION_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {
			String name = methodBinding.getName();
			if ("and".equals(name)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean isBuilderMethodInvocation(MethodInvocation method) {
		IMethodBinding methodBinding = method.resolveMethodBinding();

		if (WebFnUtils.FLUX_ROUTER_BUILDER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())
				|| WebFnUtils.MVC_ROUTER_BUILDER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {
			String name = methodBinding.getName();
			if ("build".equals(name)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void extractHandlerInfo(Expression expression, WebFnRouteDefinition routeInfo) {
		if (expression instanceof MethodReference) {
			MethodReference methodReference = (MethodReference) expression;
			IMethodBinding methodBinding = methodReference.resolveMethodBinding();
	
			if (methodBinding != null && methodBinding.getDeclaringClass() != null && methodBinding.getMethodDeclaration() != null) {
				String handlerClass = methodBinding.getDeclaringClass().getBinaryName();
				if (handlerClass != null) routeInfo.setHandlerClass(handlerClass.trim());
	
				String handlerMethod = methodBinding.getMethodDeclaration().toString();
				if (handlerMethod != null) routeInfo.setHandlerMethod(handlerMethod.trim());
			}
        } else if (expression instanceof LambdaExpression) {
            routeInfo.setHandlerMethod(expression.toString());
        }
	}

}
