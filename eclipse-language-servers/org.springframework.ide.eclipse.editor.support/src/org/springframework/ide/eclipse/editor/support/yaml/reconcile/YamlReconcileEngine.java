/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.reconcile;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.reconcile.IProblemCollector;
import org.springframework.ide.eclipse.editor.support.reconcile.IReconcileEngine;
import org.springframework.ide.eclipse.editor.support.reconcile.ReconcileProblem;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlASTProvider;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlFileAST;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

/**
 * @author Kris De Volder
 */
public abstract class YamlReconcileEngine implements IReconcileEngine {

	protected final YamlASTProvider parser;

	public YamlReconcileEngine(YamlASTProvider parser) {
		this.parser = parser;
	}

	@Override
	public void reconcile(IDocument doc, IProblemCollector problemCollector, IProgressMonitor mon) {
		problemCollector.beginCollecting();
		try {
			YamlFileAST ast = parser.getAST(doc);
			YamlASTReconciler reconciler = getASTReconciler(doc, problemCollector);
			if (reconciler != null) {
				reconciler.reconcile(ast, mon);
			}
		} catch (ParserException e) {
			String msg = e.getProblem();
			Mark mark = e.getProblemMark();
			problemCollector.accept(syntaxError(msg, mark.getIndex(), 1));
		} catch (ScannerException e) {
			String msg = e.getProblem();
			Mark mark = e.getProblemMark();
			problemCollector.accept(syntaxError(msg, mark.getIndex(), 1));
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		} finally {
			problemCollector.endCollecting();
		}
	}

	protected abstract ReconcileProblem syntaxError(String msg, int offset, int length);
	protected abstract YamlASTReconciler getASTReconciler(IDocument doc, IProblemCollector problemCollector);
}
