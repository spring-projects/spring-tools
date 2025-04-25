/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class DataRepositoryAotMetadataCodeLensProvider implements CodeLensProvider {

	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataCodeLensProvider.class);

	private final DataRepositoryAotMetadataService repositoryMetadataService;
	private final JavaProjectFinder projectFinder;

	public DataRepositoryAotMetadataCodeLensProvider(JavaProjectFinder projectFinder, DataRepositoryAotMetadataService repositoryMetadataService) {
		this.projectFinder = projectFinder;
		this.repositoryMetadataService = repositoryMetadataService;
	}

	@Override
	public void provideCodeLenses(CancelChecker cancelToken, TextDocument document, CompilationUnit cu, List<CodeLens> resultAccumulator) {
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				provideCodeLens(cancelToken, node, document, resultAccumulator);
				return super.visit(node);
			}
		});
	}

	protected void provideCodeLens(CancelChecker cancelToken, MethodDeclaration node, TextDocument document, List<CodeLens> resultAccumulator) {
		cancelToken.checkCanceled();

		IMethodBinding methodBinding = node.resolveBinding();

		// Don't show CodeLens if annotated with `@Query` or `@NativeQuery`
		for (IAnnotationBinding a : methodBinding.getAnnotations()) {
			ITypeBinding t = a.getAnnotationType();
			if (t != null 
					&& (Annotations.DATA_JPA_QUERY.equals(t.getQualifiedName()) || Annotations.DATA_JPA_NATIVE_QUERY.equals(t.getQualifiedName()))) {
				return;
			}
		}
		
		if (methodBinding == null || methodBinding.getDeclaringClass() == null
				|| methodBinding.getMethodDeclaration() == null
				|| methodBinding.getDeclaringClass().getBinaryName() == null
				|| methodBinding.getMethodDeclaration().toString() == null) {
			return;
		}

		cancelToken.checkCanceled();

		final String repositoryClass = methodBinding.getDeclaringClass().getBinaryName().trim();
		final IMethodBinding method = methodBinding.getMethodDeclaration();

		IJavaProject project = projectFinder.find(document.getId()).get();
		if (project == null) {
			return;
		}

		DataRepositoryAotMetadata metadata = repositoryMetadataService.getRepositoryMetadata(project, repositoryClass);

		if (metadata == null) {
			return;
		}

		cancelToken.checkCanceled();

		String queryStatement = repositoryMetadataService.getQueryStatement(metadata, method);
		if (queryStatement == null) {
			return;
		}

		CodeLens codeLens = createCodeLens(node, document, queryStatement);
		resultAccumulator.add(codeLens);
	}

	private CodeLens createCodeLens(MethodDeclaration node, TextDocument document, String queryStatement) {
		try {
			Command cmd = new Command();
			cmd.setTitle(queryStatement);

			CodeLens codeLens = new CodeLens();
			codeLens.setRange(document.toRange(node.getName().getStartPosition(), node.getName().getLength()));
			codeLens.setCommand(cmd);
			
			return codeLens;
			
		} catch (BadLocationException e) {
			log.error("bad location while calculating code lens for data repository query method", e);
			return null;
		}
	}

}
