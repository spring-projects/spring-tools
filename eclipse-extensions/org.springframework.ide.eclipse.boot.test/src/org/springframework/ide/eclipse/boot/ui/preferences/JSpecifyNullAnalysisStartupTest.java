/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.ui.preferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Test;

public class JSpecifyNullAnalysisStartupTest {

	@Test
	public void promptsWhenJSpecifyIsPresentAndNullAnalysisIsDisabled() throws Exception {
		assertTrue(JSpecifyNullAnalysisStartup.shouldPrompt(javaProject(JavaCore.DISABLED, true)));
	}

	@Test
	public void doesNotPromptWithoutJSpecify() throws Exception {
		assertFalse(JSpecifyNullAnalysisStartup.shouldPrompt(javaProject(JavaCore.DISABLED, false)));
	}

	@Test
	public void doesNotPromptWhenNullAnalysisIsAlreadyEnabled() throws Exception {
		assertFalse(JSpecifyNullAnalysisStartup.shouldPrompt(javaProject(JavaCore.ENABLED, true)));
	}

	private IJavaProject javaProject(String nullAnalysis, boolean hasJSpecify) {
		IType jspecifyType = (IType) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IType.class },
				(proxy, method, args) -> null);
		return (IJavaProject) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IJavaProject.class },
				(proxy, method, args) -> switch (method.getName()) {
					case "exists" -> true;
					case "getOption" -> nullAnalysis;
					case "findType" -> hasJSpecify ? jspecifyType : null;
					default -> null;
				});
	}

}
