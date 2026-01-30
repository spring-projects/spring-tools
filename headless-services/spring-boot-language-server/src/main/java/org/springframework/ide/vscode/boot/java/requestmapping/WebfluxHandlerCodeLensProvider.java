/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class WebfluxHandlerCodeLensProvider implements CodeLensProvider {

	private final SpringMetamodelIndex springIndex;

	public WebfluxHandlerCodeLensProvider(SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
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

		if (methodBinding != null && methodBinding.getDeclaringClass() != null && methodBinding.getMethodDeclaration() != null
				&& methodBinding.getDeclaringClass().getBinaryName() != null && methodBinding.getMethodDeclaration().toString() != null) {

			final String handlerClass = methodBinding.getDeclaringClass().getBinaryName().trim();
			final String handlerMethod = methodBinding.getMethodDeclaration().toString().trim();
			
			cancelToken.checkCanceled();
			
			List<WebfluxHandlerMethodIndexElement> matchingHandlerMethods = findMatchingHandlerMethogs(handlerClass, handlerMethod);
			if (matchingHandlerMethods.size() > 0) {
				for (WebfluxHandlerMethodIndexElement handlerInfo : matchingHandlerMethods) {
					try {
						CodeLens codeLens = new CodeLens();
						codeLens.setRange(document.toRange(node.getName().getStartPosition(), node.getName().getLength()));

						String httpMethod = WebFnUtils.getStringRep(handlerInfo.getHttpMethods(), string -> string);
						String codeLensCommand = httpMethod != null ? httpMethod + " " : "";

						codeLensCommand += handlerInfo.getPath();

						String acceptType = WebFnUtils.getStringRep(handlerInfo.getAcceptTypes(), WebFnUtils::getMediaType);
						codeLensCommand += acceptType != null ? " - Accept: " + acceptType : "";

						String contentType = WebFnUtils.getStringRep(handlerInfo.getContentTypes(), WebFnUtils::getMediaType);
						codeLensCommand += contentType != null ? " - Content-Type: " + contentType : "";
						
						Command cmd = new Command();
						cmd.setTitle(codeLensCommand);
						codeLens.setCommand(cmd);

						resultAccumulator.add(codeLens);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	private List<WebfluxHandlerMethodIndexElement> findMatchingHandlerMethogs(String handlerClass, String handlerMethod) {
		Bean[] beans = springIndex.getBeans();
		
		return Arrays.stream(beans)
			.flatMap(bean -> bean.getChildren().stream())
			.filter(element -> element instanceof WebfluxHandlerMethodIndexElement)
			.map(element -> (WebfluxHandlerMethodIndexElement) element)
			.filter(webfluxElement -> webfluxElement.getHandlerClass() != null && webfluxElement.getHandlerClass().equals(handlerClass)
					&& webfluxElement.getHandlerMethod() != null && webfluxElement.getHandlerMethod().equals(handlerMethod))
			.toList();
	}

}
