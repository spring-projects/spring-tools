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
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class WebConfigCodeLensProvider implements CodeLensProvider {

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
		
		for (WebConfigIndexElement webConfig : webConfigs) {
			CodeLens codeLens = createCodeLens(webConfig, node, doc);
			if (codeLens != null) {
				codeLenses.add(codeLens);
			}
		}
		
	}

	private CodeLens createCodeLens(WebConfigIndexElement webConfig, TypeDeclaration node, TextDocument doc) {
		Command command = new Command();
	
		// Display label
		String label = "Web Config";
		if (webConfig.getPathPrefix() != null) {
			label += " - " + webConfig.getPathPrefix();
		}
		
		if (webConfig.isVersioningSupported()) {
			label += " - Versioning via " + String.join(", ", webConfig.getVersionSupportStrategies());
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
		
		// Range
		
		SimpleName nameNode = node.getName();
		if (nameNode == null) return null;
		
		Range range;
		try {
			range = doc.toRange(node.getStartPosition(), node.getLength());
			return new CodeLens(range, command, null);

		} catch (BadLocationException e) {
			return null;
		}

	}


}
