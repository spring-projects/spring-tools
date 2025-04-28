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
package org.springframework.ide.vscode.boot.java.codeaction;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionResolveSupportCapabilities;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.JavaCodeActionHandler;
import org.springframework.ide.vscode.boot.java.reconcilers.CompositeASTVisitor;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.BasicCollector;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient.Client;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class JdtCodeActionHandler implements JavaCodeActionHandler {
	
	private static Logger log = LoggerFactory.getLogger(JdtCodeActionHandler.class);
	
	final private CompilationUnitCache cuCache;
	final private Collection<JdtAstCodeActionProvider> providers;

	public JdtCodeActionHandler(CompilationUnitCache cuCache, Collection<JdtAstCodeActionProvider> providers) {
		this.cuCache = cuCache;
		this.providers = providers;
	}
	
	protected static boolean isResolve(CodeActionCapabilities capabilities, String property) {
		if (capabilities != null) {
			CodeActionResolveSupportCapabilities resolveSupport = capabilities.getResolveSupport();
			if (resolveSupport != null) {
				List<String> properties = resolveSupport.getProperties();
				return properties != null && properties.contains(property);
			}
		}
		return false;
	}
	
	private boolean isSupported(CodeActionCapabilities capabilities, CodeActionContext context) {
		// Default case is anything non-quick fix related.
		if (isResolve(capabilities, "edit")) {
			if (context.getOnly() != null) {
				return context.getOnly().contains(CodeActionKind.Refactor);
			} else {
				if (LspClient.currentClient() == Client.ECLIPSE) {
					// Eclipse would have no diagnostics in the context for QuickAssists refactoring. Diagnostics will be around for QuickFix only
					return context.getDiagnostics().isEmpty();
				} else {
					return true;
				}
				
			}
		}
		return false;
	}

	@Override
	public List<Either<Command, CodeAction>> handle(IJavaProject project, CancelChecker cancelToken,
			CodeActionCapabilities capabilities, CodeActionContext context, TextDocument doc, IRegion region) {
		try {

			URI uri = URI.create(doc.getUri());

			if (isSupported(capabilities, context)) {
				
				List<JdtAstCodeActionProvider> applicableProviders = providers.stream().filter(p -> p.isApplicable(project)).toList();
				if (!applicableProviders.isEmpty()) {
					List<CodeAction> codeActions = cuCache.withCompilationUnit(project, uri, cu -> {
						if (cu != null) {
							try {
								List<CodeAction> cas = new ArrayList<>();
								BasicCollector<CodeAction> codeActionsCollector = new BasicCollector<>(cas);
								
								CompositeASTVisitor v = new CompositeASTVisitor();
								for (JdtAstCodeActionProvider p : applicableProviders) {
									v.add(p.createVisitor(cancelToken, project, uri, cu, doc, region, codeActionsCollector));
								}
								
								codeActionsCollector.beginCollecting();
								
								cu.accept(v);
								
								codeActionsCollector.endCollecting();
								
								return cas;
							} catch (Exception e) {
								log.error("", e);
							}
						}
						return Collections.emptyList();
					});

					return codeActions.stream().map(ca -> Either.<Command, CodeAction>forRight(ca)).collect(Collectors.toList());
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Collections.emptyList();
	}

}
