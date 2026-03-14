/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.javadoc;

import java.net.URI;
import java.util.function.Function;

import org.eclipse.jdt.core.IJavaElement;
import org.springframework.tooling.jdt.ls.commons.java.JavaData;

public class JavadocUtils {

	public static final String javadoc(Function<IJavaElement, String> contentProvider, URI projectUri, String bindingKey, boolean lookInOtherProjects) throws Exception {
		IJavaElement element = JavaData.findElement(projectUri, bindingKey, lookInOtherProjects);
		return element == null ? null : contentProvider.apply(element);
	}

	public static String alternateBinding(String bindingKey) {
		int idxStartParams = bindingKey.indexOf('(');
		if (idxStartParams >= 0) {
			int idxEndParams = bindingKey.indexOf(')', idxStartParams);
			if (idxEndParams > idxStartParams) {
				String params = bindingKey.substring(idxStartParams, idxEndParams);
				return bindingKey.substring(0, idxStartParams) + params.replace('/', '.') + bindingKey.substring(idxEndParams);
			}
		}
		return null;
	}


}