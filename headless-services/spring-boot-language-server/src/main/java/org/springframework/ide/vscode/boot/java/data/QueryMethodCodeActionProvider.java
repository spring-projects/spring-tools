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
package org.springframework.ide.vscode.boot.java.data;

import java.net.URI;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.java.codeaction.JdtAstCodeActionProvider;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ICollector;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class QueryMethodCodeActionProvider implements JdtAstCodeActionProvider {
	
	private static final String TITLE = "Convert into `@Query`";
	
	private final DataRepositoryAotMetadataService repositoryMetadataService;
	private final RewriteRefactorings refactorings;

	public QueryMethodCodeActionProvider(DataRepositoryAotMetadataService repositoryMetadataService, RewriteRefactorings refactorings) {
		this.repositoryMetadataService = repositoryMetadataService;
		this.refactorings = refactorings;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersion(project, "spring-data-jpa");
		return version != null && version.getMajor() >= 4;
	}

	@Override
	public ASTVisitor createVisitor(CancelChecker cancelToken, IJavaProject project, URI docURI, CompilationUnit cu, TextDocument doc,
			IRegion region, ICollector<CodeAction> collector) {
		return new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
				cancelToken.checkCanceled();
				if (node.getStartPosition() <= region.getStart() && node.getStartPosition() + node.getLength() >= region.getEnd()) {
					int offset = node.getName().getStartPosition();
					int length = node.getName().getLength();
					if (offset <= region.getStart() && offset + length >= region.getEnd()) {
						IMethodBinding binding = node.resolveBinding();
						if (DataRepositoryAotMetadataCodeLensProvider.isDataQuaryNonAnnotatedMethodCandidate(binding)) {
							DataRepositoryAotMetadataCodeLensProvider.getDataQuery(repositoryMetadataService, project, binding)
									.map(query -> createCodeAction(binding, docURI, query)).ifPresent(collector::accept);
						}
					}
					return super.visit(node);
				}
				return false;
			}
			
		};
	}
	
	private CodeAction createCodeAction(IMethodBinding mb, URI docUri, String query) {
		CodeAction ca = new CodeAction();
		ca.setCommand(refactorings.createFixCommand(TITLE, DataRepositoryAotMetadataCodeLensProvider.createFixDescriptor(mb, docUri.toASCIIString(), query)));
		ca.setTitle(TITLE);
		ca.setKind(CodeActionKind.Refactor);
		return ca;
	}

}
