/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.jandex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.util.Assert;

import com.google.common.collect.ImmutableList;

public class TestDataProvider {

	private Map<String, List<String>> methodParameters = new HashMap<>();
	
	public List<String> getParameterNames(IMethod method) {
		String key = method.getBindingKey();
		List<String> l = methodParameters.get(method.getBindingKey());
		Assert.isLegal(l != null, "Test code should provide method parameter names for '"+key+"'");;
		return l;
	}

	public void methodParams(String bindingKey, String... names) {
		methodParameters.put(bindingKey, ImmutableList.copyOf(names));
	}

}
