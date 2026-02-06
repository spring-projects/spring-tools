/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.AutowiredFieldIntoConstructorParameterVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;

public class ConvertAutowiredFieldIntoConstructorParameter extends Recipe {
	
	private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

	private String classFqName;
	private String fieldName;
	
	@JsonCreator
	public ConvertAutowiredFieldIntoConstructorParameter(String classFqName, String fieldName) {
		this.classFqName = classFqName;
		this.fieldName = fieldName;
	}

	@Override
	public String getDisplayName() {
		return "Convert autowired field into constructor parameter";
	}
	
	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(new UsesType<ExecutionContext>(AUTOWIRED, false), new AutowiredFieldIntoConstructorParameterVisitor(classFqName, fieldName));
	}

	@Override
	public String getDescription() {
		return "Converts autowired fields into constructor parameters";
	}
	
}