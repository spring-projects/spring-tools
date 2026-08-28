/*******************************************************************************
 * Copyright (c) 2013, 2018 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

public class BootJavaElementPropertyTester extends PropertyTester {

	public BootJavaElementPropertyTester() {
	}

	@Override
	public boolean test(Object je, String property, Object[] args, Object expectedValue) {
		if (je instanceof IJavaElement && "isInBootProject".equals(property)) {
			return isInBootProject((IJavaElement) je);
		}
		if (je instanceof IResource && "isInBootProject".equals(property)) {
			return isInBootProject((IJavaElement) je);
		}
		if (je instanceof IJavaElement && "isInBootProjectWithDevTools".equals(property)) {
			return hasBootDevTools((IJavaElement) je);
		}
		if (je instanceof IJavaElement && "isInBoot2Project".equals(property)) {
			return isInBoot2Project((IJavaElement) je);
		}
		return false;
	}

	private boolean isInBoot2Project(IJavaElement je) {
		if (je != null) {
			IJavaProject jp = je.getJavaProject();
			if (jp != null) {
				return BootPropertyTester.isBoot2Project(jp.getProject());
			}
		}
		return false;
	}

	private boolean isInBootProject(IJavaElement je) {
		if (je != null) {
			IJavaProject jp = je.getJavaProject();
			if (jp != null) {
				return BootPropertyTester.isBootProject(jp.getProject());
			}
		}
		return false;
	}

	private static boolean hasBootDevTools(IJavaElement element) {
		IJavaProject javaProject = element.getJavaProject();
		if (javaProject != null) {
			return BootPropertyTester.hasBootDevTools(javaProject.getProject());
		}
		return false;
	}


}
