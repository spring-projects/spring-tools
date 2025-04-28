/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.java.codeaction.JdtAstCodeActionProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ICollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Ignored (i.e. not shown) reconcile problems with QuickFixes are being shown as CodeActions(s) with this provider.
 * <b>Note:</b> Only Rewrite based QuickFixes are supported at the moment hence should be around only if Rewrite recipes are supported
 */
public class ReconcileProblemCodeActionProvider implements JdtAstCodeActionProvider {
	
	private final JdtReconciler reconciler;
	private final DiagnosticSeverityProvider severityProvider;
	
	
	public ReconcileProblemCodeActionProvider(JdtReconciler reconciler, DiagnosticSeverityProvider severityProvider) {
		this.reconciler = reconciler;
		this.severityProvider = severityProvider;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		if (reconciler.config.isJavaSourceReconcileEnabled()) {
			return Arrays.stream(reconciler.reconcilers).anyMatch(r -> r.isApplicable(project));
		}
		return false;
	}

	@Override
	public ASTVisitor createVisitor(CancelChecker cancelToken, IJavaProject project, URI docURI, CompilationUnit cu, TextDocument doc,
			IRegion region, ICollector<CodeAction> collector) {
		IProblemCollector problemCollector = new IProblemCollector() {
			
			@Override
			public void endCollecting() {
			}
			
			@Override
			public void beginCollecting() {
			}
			
			@Override
			public void accept(ReconcileProblem p) {
				if (p.getOffset() <= region.getOffset() && p.getOffset() + p.getLength() >= region.getOffset() + region.getLength() && severityProvider.getDiagnosticSeverity(p) == null) {
					for (QuickfixData<?> qf :  p.getQuickfixes()) {
						if (qf.params instanceof FixDescriptor) {
							collector.accept(createCodeActionFromScope((FixDescriptor) qf.params));
						}
					}
				}
			}
		};
		CompositeASTVisitor v = new CompositeASTVisitor();
		ReconcilingContext ctx = new ReconcilingContext(docURI.toASCIIString(), problemCollector, true, true, List.of());
		for (JdtAstReconciler r : reconciler.reconcilers) {
			if (r.isApplicable(project) && severityProvider.getDiagnosticSeverity(r.getProblemType()) == null) {
				v.add(r.createVisitor(project, docURI, cu, ctx));
			}
		}
		return v;
	}
	
	private CodeAction createCodeActionFromScope(FixDescriptor d) {
		CodeAction ca = new CodeAction();
		ca.setKind(CodeActionKind.Refactor);
		ca.setTitle(d.getLabel());
		ca.setData(d);
		return ca;
	}


}
