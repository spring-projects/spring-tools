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
import java.util.Map;
import java.util.Optional;

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
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.Streams;

public class WebConfigCodeLensProvider implements CodeLensProvider {

//	private static final Logger log = LoggerFactory.getLogger(WebConfigCodeLensProvider.class);

	private final SpringMetamodelIndex springIndex;
	private final BootJavaConfig config;
	private final JavaProjectFinder projectFinder;

	public WebConfigCodeLensProvider(JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex, BootJavaConfig config) {
		this.projectFinder = projectFinder;
		this.springIndex = springIndex;
		this.config = config;
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

		if (!pathPrefixApplies && !hasVersioning && !hasSupportedVersions) {
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
		
		command.setTitle(label);
		command.setCommand("vscode.open");
		command.setArguments(List.of(targetLocation.getUri(),
				Map.of("selection", Map.of(
						"start", Map.of("line", targetRange.getStart().getLine(), "character", targetRange.getStart().getCharacter()),
						"end", Map.of("line", targetRange.getEnd().getLine(), "character", targetRange.getEnd().getCharacter()))
				)
			)
		);
		
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
