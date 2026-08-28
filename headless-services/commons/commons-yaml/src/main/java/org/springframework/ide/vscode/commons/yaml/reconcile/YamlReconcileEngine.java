/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * @author Kris De Volder
 */
public abstract class YamlReconcileEngine implements IReconcileEngine {

	final static Logger logger = LoggerFactory.getLogger(YamlReconcileEngine.class);

	protected final YamlASTProvider parser;

	public YamlReconcileEngine(YamlASTProvider parser) {
		this.parser = parser;
	}

	@Override
	public void reconcile(IDocument doc, IProblemCollector problemCollector) {
		problemCollector.beginCollecting();
		try {
			YamlFileAST ast = parser.getAST(doc);
			YamlASTReconciler reconciler = getASTReconciler(doc, problemCollector);
			if (reconciler != null) {
				reconciler.reconcile(ast);
			}
		} catch (MarkedYAMLException e) {
			String msg = e.getProblem();
			Mark mark = e.getProblemMark();
			problemCollector.accept(syntaxError(msg, mark.getIndex(), 1));
		} catch (Exception e) {
			logger.error("unexpected error during reconcile", e);
		} finally {
			problemCollector.endCollecting();
		}
	}

	protected abstract ReconcileProblem syntaxError(String msg, int offset, int length);
	protected abstract YamlASTReconciler getASTReconciler(IDocument doc, IProblemCollector problemCollector);
}
