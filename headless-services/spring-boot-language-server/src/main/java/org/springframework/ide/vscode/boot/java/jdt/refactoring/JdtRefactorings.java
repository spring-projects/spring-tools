/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixEdit;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixHandler;
import org.springframework.ide.vscode.commons.languageserver.util.CodeActionResolver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.LazyTextDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

/**
 * Execution engine for JDT-based refactoring quick fixes.
 * <p>
 * Implements both {@link CodeActionResolver} (lazy-resolve path) and
 * {@link QuickfixHandler} (command path) to support JDT refactorings
 * alongside the existing OpenRewrite infrastructure.
 * <p>
 * The flow:
 * <ol>
 *   <li>A reconciler creates a {@link JdtFixDescriptor} containing a
 *       {@link JdtRefactoring} and attaches it as quick fix data</li>
 *   <li>The descriptor is serialized into {@code CodeAction.data} via Gson
 *       (using {@link org.springframework.ide.vscode.commons.RuntimeTypeAdapterFactory}
 *       for polymorphic {@link JdtRefactoring} serialization)</li>
 *   <li>When the user triggers the quick fix, this class deserializes the
 *       descriptor, obtains the {@link CompilationUnit} from
 *       {@link CompilationUnitCache}, creates an {@link ASTRewrite},
 *       applies the refactoring, and converts the result to an LSP
 *       {@link WorkspaceEdit}</li>
 * </ol>
 */
public class JdtRefactorings implements CodeActionResolver, QuickfixHandler {

	public static final String JDT_QUICKFIX = "org.springframework.ide.vscode.jdt.refactoring";

	private static final Logger log = LoggerFactory.getLogger(JdtRefactorings.class);

	private final SimpleLanguageServer server;
	private final JavaProjectFinder projectFinder;
	private final CompilationUnitCache cuCache;

	public JdtRefactorings(SimpleLanguageServer server, JavaProjectFinder projectFinder,
			CompilationUnitCache cuCache) {
		this.server = server;
		this.projectFinder = projectFinder;
		this.cuCache = cuCache;
	}

	// ========== CodeActionResolver ==========

	@Override
	public CompletableFuture<WorkspaceEdit> resolve(CodeAction codeAction) {
		if (codeAction.getData() instanceof JsonElement je) {
			try {
				JdtFixDescriptor descriptor = server.getGson().fromJson(je, JdtFixDescriptor.class);
				if (descriptor != null && descriptor.refactoring() != null) {
					return perform(descriptor);
				}
			}
			catch (Exception e) {
				log.error("Failed to resolve JDT code action", e);
			}
		}
		return CompletableFuture.completedFuture(null);
	}

	// ========== QuickfixHandler ==========

	@Override
	public Mono<QuickfixEdit> createEdits(Object params) {
		Gson gson = server.getGson();
		JsonElement je = params instanceof JsonElement ? (JsonElement) params : gson.toJsonTree(params);
		JdtFixDescriptor descriptor = gson.fromJson(je, JdtFixDescriptor.class);
		if (descriptor != null && descriptor.refactoring() != null) {
			return Mono.fromFuture(perform(descriptor).thenApply(we -> new QuickfixEdit(we, null)));
		}
		return Mono.empty();
	}

	// ========== Execution ==========

	private CompletableFuture<WorkspaceEdit> perform(JdtFixDescriptor descriptor) {
		return CompletableFuture.supplyAsync(() -> {
			WorkspaceEdit workspaceEdit = new WorkspaceEdit();
			List<Either<TextDocumentEdit, org.eclipse.lsp4j.ResourceOperation>> documentChanges = new ArrayList<>();
			workspaceEdit.setDocumentChanges(documentChanges);

			for (String docUri : descriptor.docUris()) {
				try {
					IJavaProject project = projectFinder
							.find(new org.eclipse.lsp4j.TextDocumentIdentifier(docUri))
							.orElse(null);
					if (project == null) {
						log.warn("No project found for {}", docUri);
						continue;
					}

					URI uri = URI.create(docUri);
					Map<String, String> formatterOptions = project.getJavaCoreOptions();

					TextDocumentEdit docEdit = cuCache.withCompilationUnit(project, uri, cu -> {
						if (cu == null) {
							return null;
						}
						try {
							TextDocument lspDoc = getDocument(docUri);

							ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
							descriptor.refactoring().apply(rewrite, cu);

							Document jdtDoc = new Document(lspDoc.get());
							org.eclipse.text.edits.TextEdit jdtEdit = rewrite.rewriteAST(jdtDoc, formatterOptions);

							return JdtRefactorUtils.toLspTextDocumentEdit(jdtEdit, lspDoc);
						}
						catch (Exception e) {
							log.error("Failed to compute JDT refactoring edit for {}", docUri, e);
							return null;
						}
					});

					if (docEdit != null && !docEdit.getEdits().isEmpty()) {
						documentChanges.add(Either.forLeft(docEdit));
					}
				}
				catch (Exception e) {
					log.error("Failed to apply JDT refactoring to {}", docUri, e);
				}
			}

			return documentChanges.isEmpty() ? null : workspaceEdit;
		});
	}

	private TextDocument getDocument(String docUri) {
		SimpleTextDocumentService docService = server.getTextDocumentService();
		TextDocument doc = docService.getLatestSnapshot(docUri);
		if (doc != null) {
			return doc;
		}
		return new LazyTextDocument(docUri, LanguageId.JAVA);
	}

}
