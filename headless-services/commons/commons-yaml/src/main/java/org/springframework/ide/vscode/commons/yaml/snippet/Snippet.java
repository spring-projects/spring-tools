/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.snippet;

import java.util.function.Predicate;

import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.languageserver.util.PlaceHolderString;
import org.springframework.ide.vscode.commons.languageserver.util.PlaceHolderString.PlaceHolder;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;

public class Snippet {

	private final String name;
	private final PlaceHolderString snippet;
	private final Predicate<DynamicSchemaContext> applicability;

	public Snippet(String name, PlaceHolderString snippet, Predicate<DynamicSchemaContext> applicability) {
		super();
		this.name = name;
		this.snippet = snippet;
		this.applicability = applicability;
	}
	public String getName() {
		return name;
	}
	public String getSnippet() {
		return snippet.toString();
	}

	@Override
	public String toString() {
		return "Snippet [ name="+name+",\n" +snippet +"\n]";
	}
	public Predicate<DynamicSchemaContext> getApplicability() {
		return applicability;
	}
	public boolean isApplicable(DynamicSchemaContext dc) {
		return applicability == null || applicability.test(dc);
	}
	public PlaceHolder getPlaceHolder(Object id) {
		return snippet.getPlaceHolder(id);
	}
}
