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
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;

public class SimpleWebFnTypeChecker implements WebFnTypeChecker {

	@Override
	public boolean isRouteMethodInvocation(MethodInvocation method) {
        String nodeName = method.getName().getIdentifier();
        return "route".equals(nodeName)
        		|| "andRoute".equals(nodeName);
	}

	@Override
	public boolean isBuilderMethodInvocation(MethodInvocation method) {
        String nodeName = method.getName().getIdentifier();
        return "build".equals(nodeName);
	}
	
	@Override
	public boolean isStaticNestInvocation(MethodInvocation method) {
        return "nest".equals(method.getName().getIdentifier());
	}

	@Override
	public boolean isStaticAndInvocation(MethodInvocation method) {
        return "and".equals(method.getName().getIdentifier());
	}

	@Override
	public void extractHandlerInfo(Expression expression, WebFnRouteDefinition routeInfo) {
        if (expression instanceof MethodReference) {
            MethodReference methodRef = (MethodReference) expression;
            String handlerStr = methodRef.toString();
            
            // Parse "handler::getPerson" format
            int separatorIndex = handlerStr.indexOf("::");
            if (separatorIndex > 0) {
                String className = handlerStr.substring(0, separatorIndex).trim();
                String methodName = handlerStr.substring(separatorIndex + 2).trim();
                routeInfo.setHandlerClass(className);
                routeInfo.setHandlerMethod(methodName);
            } else {
                // Fallback if format is unexpected
                routeInfo.setHandlerMethod(handlerStr);
            }
        } else if (expression instanceof LambdaExpression) {
            // For lambda expressions, we can't easily extract class/method info
            // Just store the lambda as the method
            routeInfo.setHandlerMethod(expression.toString());
        }
	}

}
