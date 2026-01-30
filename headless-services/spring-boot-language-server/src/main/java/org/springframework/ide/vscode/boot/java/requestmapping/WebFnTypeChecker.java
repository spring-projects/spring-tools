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
import org.eclipse.jdt.core.dom.MethodInvocation;

public interface WebFnTypeChecker {
	
	boolean isRouteMethodInvocation(MethodInvocation method);
	boolean isBuilderMethodInvocation(MethodInvocation method);
	boolean isStaticNestInvocation(MethodInvocation method);
	boolean isStaticAndInvocation(MethodInvocation method);
	
	void extractHandlerInfo(Expression expression, WebFnRouteDefinition routeInfo);

}
