/*******************************************************************************
 * Copyright (c) 2018 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

public class SpringJavaElementPropertyTester extends PropertyTester {

	@Override
	public boolean test(Object je, String property, Object[] args, Object expectedValue) {
		if (je instanceof IJavaElement && "isInSpringProject".equals(property)) {
			return isInSpringProject((IJavaElement) je);
		}
		if (je instanceof IResource && "isInSpringProject".equals(property)) {
			return isInSpringProject((IJavaElement) je);
		}
		if ("projectNature".equals(property)) {
			try {
				IJavaProject jp = ((IJavaElement) je).getJavaProject();
				if (jp != null) {
					IProject proj = jp.getProject();
					return proj != null && proj.isAccessible() && proj.hasNature(expectedValue instanceof String ? (String) expectedValue : "");
				}
			} catch (CoreException e) {
				// Ignore
			}
		}
		return false;
	}

	private boolean isInSpringProject(IJavaElement je) {
		if (je != null) {
			IJavaProject jp = je.getJavaProject();
			if (jp != null) {
				return SpringPropertyTester.isSpringProject(jp.getProject());
			}
		}
		return false;
	}
}
