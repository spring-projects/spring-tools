/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.yaml.reconcile;

import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.IndexNavigator;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtilProvider;
import org.springframework.ide.vscode.boot.properties.reconcile.PropertyPlaceholderChecker;
import org.springframework.ide.vscode.boot.yaml.quickfix.AppYamlQuickfixes;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlASTReconciler;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlReconcileEngine;

public class ApplicationYamlReconcileEngine extends YamlReconcileEngine {

	private SpringPropertyIndexProvider indexProvider;
	private TypeUtilProvider typeUtilProvider;
	private AppYamlQuickfixes quickFixes;
	private SourceLinks sourceLinks;
	private PropertyPlaceholderChecker placeholderChecker;

	public ApplicationYamlReconcileEngine(YamlASTProvider astProvider, SpringPropertyIndexProvider indexProvider, TypeUtilProvider typeUtilProvider, AppYamlQuickfixes quickFixes, SourceLinks sourceLinks,
			ProjectBasedPropertyIndexProvider adHocIndexProvider, JavaProjectFinder projectFinder) {
		super(astProvider);
		this.indexProvider = indexProvider;
		this.typeUtilProvider = typeUtilProvider;
		this.quickFixes = quickFixes;
		this.sourceLinks = sourceLinks;
		this.placeholderChecker = new PropertyPlaceholderChecker(ApplicationYamlProblemType.YAML_PLACEHOLDER_SYNTAX,
				ApplicationYamlProblemType.YAML_UNRESOLVED_PLACEHOLDER, indexProvider, adHocIndexProvider, projectFinder);
	}

	@Override
	protected YamlASTReconciler getASTReconciler(IDocument doc, IProblemCollector problemCollector) {
		FuzzyMap<PropertyInfo> index = indexProvider.getIndex(doc).getProperties();
		if (index != null && !index.isEmpty()) {
			IndexNavigator nav = IndexNavigator.with(index);
			return new ApplicationYamlASTReconciler(problemCollector, nav, typeUtilProvider.getTypeUtil(sourceLinks, doc), quickFixes, placeholderChecker);
		}
		return null;
	}

	@Override
	protected ReconcileProblem syntaxError(String msg, int offset, int len) {
		return ApplicationYamlProblems.problem(ApplicationYamlProblemType.YAML_SYNTAX_ERROR, msg, offset, len);
	}
}
