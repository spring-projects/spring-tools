/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.BootLanguageServerInitializer;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.Streams;

public class WebConfigCodeLensProvider implements CodeLensProvider {

//	private static final Logger log = LoggerFactory.getLogger(WebConfigCodeLensProvider.class);

	private static final long REFRESH_DEBOUNCE_MILLIS = 500;

	private final SpringMetamodelIndex springIndex;
	private final BootJavaConfig config;
	private final JavaProjectFinder projectFinder;

	private final ScheduledExecutorService refreshScheduler = Executors.newSingleThreadScheduledExecutor(
			r -> new Thread(r, "WebConfigCodeLensProvider-refresh"));
	private ScheduledFuture<?> pendingRefresh;

	public WebConfigCodeLensProvider(JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex, BootJavaConfig config,
			SimpleLanguageServer server, SpringSymbolIndex springSymbolIndex) {
		this.projectFinder = projectFinder;
		this.springIndex = springIndex;
		this.config = config;

		listenForIndexUpdates(server, springSymbolIndex);
		server.onShutdown(refreshScheduler::shutdownNow);
	}

	/**
	 * Web config data (path prefixes, API versioning strategies) is derived from the
	 * Spring index, which gets updated asynchronously (e.g. after a quick fix creates or
	 * modifies a web config class, or a properties file). Once that indexing finishes, ask
	 * the client to refresh code lenses so open controllers pick up the new data.
	 * <p>
	 * Indexing can complete many times in short succession (e.g. saving several files in
	 * a row, or a burst of file-watcher events from a branch switch), so the actual refresh
	 * request is debounced to collapse a burst of updates into a single refresh.
	 */
	private void listenForIndexUpdates(SimpleLanguageServer server, SpringSymbolIndex springSymbolIndex) {
		springSymbolIndex.onUpdate(v -> {
			if (config.isEnabledCodeLensForWebConfigs() && server.getWorkspaceService().supportsCodeLensRefresh()) {
				scheduleCodeLensRefresh(server);
			}
		});
	}

	private synchronized void scheduleCodeLensRefresh(SimpleLanguageServer server) {
		// Cancel the previous, still pending request to debounce the refresh.
		if (pendingRefresh != null) {
			pendingRefresh.cancel(false);
		}
		pendingRefresh = refreshScheduler.schedule(() -> server.getClient().refreshCodeLenses(),
				REFRESH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
	}

	@Override
	public void provideCodeLenses(CancelChecker cancelToken, TextDocument document, CompilationUnit cu, List<CodeLens> codeLenses) {
		if (!config.isEnabledCodeLensForWebConfigs()) {
			return;
		}
		
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				provideCodeLens(cancelToken, node, document, codeLenses);
				return super.visit(node);
			}
		});
		
	}
	
	private void provideCodeLens(CancelChecker cancelToken, TypeDeclaration node, TextDocument doc, List<CodeLens> codeLenses) {
		cancelToken.checkCanceled();
		
		ITypeBinding binding = node.resolveBinding();
		if (binding == null) return;
		
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(node);
		if (!annotationHierarchies.isAnnotatedWith(binding, Annotations.CONTROLLER)) return;
		
		Optional<IJavaProject> optional = projectFinder.find(doc.getId());
		if (optional.isEmpty()) return;
		
		IJavaProject project = optional.get();

		List<WebConfigIndexElement> webConfigs = springIndex.getNodesOfType(project.getElementName(), WebConfigIndexElement.class);
		List<WebConfigIndexElement> webConfigFromProperties = new WebConfigPropertiesIndexer().findWebConfigFromProperties(project);
		
		Streams.concat(webConfigs.stream(), webConfigFromProperties.stream())
			.map(webConfig -> createCodeLens(webConfig, node, binding, annotationHierarchies, doc))
			.filter(codeLens -> codeLens != null)
			.forEach(codeLens -> codeLenses.add(codeLens));
	}

	private CodeLens createCodeLens(WebConfigIndexElement webConfig, TypeDeclaration node,
			ITypeBinding binding, AnnotationHierarchies annotationHierarchies, TextDocument doc) {
		Command command = new Command();
	
		// Determine whether the path prefix applies to this specific class
		boolean pathPrefixApplies = webConfig.getPathPrefix() != null
				&& !webConfig.getPathPrefix().trim().isEmpty();
		if (pathPrefixApplies && webConfig.getPathPrefixPredicate() != null) {
			pathPrefixApplies = webConfig.getPathPrefixPredicate().matches(binding, annotationHierarchies);
		}

		boolean hasVersioning = webConfig.getVersionSupportStrategies() != null
				&& !webConfig.getVersionSupportStrategies().isEmpty();
		boolean hasSupportedVersions = webConfig.getSupportedVersions() != null
				&& !webConfig.getSupportedVersions().isEmpty();

		// once a web config class is detected, still offer the navigation shortcut even if
		// its summary hasn't been computed (or has nothing to report) yet
		if (!webConfig.isEmpty() && !pathPrefixApplies && !hasVersioning && !hasSupportedVersions) {
			return null;
		}

		// Display label
		String label = webConfig.getConfigType().getLabel();

		if (pathPrefixApplies) {
			label += " - Path Prefix: " + webConfig.getPathPrefix();
		}
		
		if (hasVersioning) {
			label += " - Versioning via " + String.join(", ", webConfig.getVersionSupportStrategies());
		}
		
		if (hasSupportedVersions) {
			label += " - Supported Versions: " + String.join(", ", webConfig.getSupportedVersions());
		}
		
		Location targetLocation = webConfig.getLocation();
		Range targetRange = targetLocation.getRange();

		ShowDocumentParams showDocParams = new ShowDocumentParams(targetLocation.getUri());
		showDocParams.setSelection(targetRange);

		command.setTitle(label);
		command.setCommand(BootLanguageServerInitializer.CMD_SHOW_DOC);
		command.setArguments(List.of(showDocParams));
		
		// Range — start above the first annotation/modifier (not above Javadoc)
		
		SimpleName nameNode = node.getName();
		if (nameNode == null) return null;
		
		try {
			int anchorOffset = ASTUtils.bodyDeclarationAnchorOffset(node);
			Position startPos = doc.toPosition(anchorOffset);
			Position endPos = doc.toPosition(nameNode.getStartPosition() + nameNode.getLength());
			Range range = new Range(startPos, endPos);
			return new CodeLens(range, command, null);

		} catch (BadLocationException e) {
			return null;
		}

	}
	
}
