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
package org.springframework.ide.vscode.boot.java.handlers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxUtils;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

/**
 * Code lens provider for functional router methods that use static imports.
 * Provides AI-assisted conversion to the modern builder pattern.
 * 
 * Reuses the command and configuration from {@link CopilotCodeLensProvider}.
 * 
 * @author Martin Lippert
 */
public class RouterFunctionCodeLensProvider implements CodeLensProvider {
	
	protected static Logger logger = LoggerFactory.getLogger(RouterFunctionCodeLensProvider.class);

	@Override
	public void provideCodeLenses(CancelChecker cancelToken, TextDocument document, CompilationUnit cu, List<CodeLens> resultAccumulator) {
		if (!CopilotCodeLensProvider.isShowCodeLenses()) {
			return;
		}

		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				cancelToken.checkCanceled();
				
				// Check if this is a functional web router bean (WebFlux or WebMVC)
				if (WebfluxUtils.isFunctionalWebRouterBean(node)) {
					// Check if the method uses static imports (old style) instead of builder pattern
					if (usesStaticImports(node)) {
						provideCodeLensForRouterMethod(cancelToken, node, document, resultAccumulator);
					}
				}
				
				return super.visit(node);
			}
		});
	}
	
	/**
	 * Checks if the router method uses static imports (RouterFunctions.route(), RequestPredicates.GET(), etc.)
	 * instead of the builder pattern (RouterFunctions.route().GET().build()).
	 * 
	 * The detection looks for:
	 * - RouterFunctions.route() with 2 parameters (predicate, handler) - old style
	 * - RouterFunction.andRoute() method calls - old style
	 * 
	 * If we find RouterFunctions.route() with no parameters, that's the builder pattern, so we return false.
	 */
	private boolean usesStaticImports(MethodDeclaration method) {
		Block methodBody = method.getBody();
		if (methodBody == null) {
			return false;
		}
		
		AtomicBoolean foundStaticImportStyle = new AtomicBoolean(false);
		AtomicBoolean foundBuilderStyle = new AtomicBoolean(false);
		
		methodBody.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				IMethodBinding methodBinding = node.resolveMethodBinding();
				
				if (methodBinding != null) {
					String declaringClassName = methodBinding.getDeclaringClass().getBinaryName();
					String methodName = methodBinding.getName();
					
					// Check for static import style
					if (isStaticImportStyle(declaringClassName, methodName, node)) {
						foundStaticImportStyle.set(true);
					}
					
					// Check for builder style
					if (isBuilderStyle(declaringClassName, methodName, node)) {
						foundBuilderStyle.set(true);
					}
				}
				
				return super.visit(node);
			}
		});
		
		// Only show code lens if using static import style and NOT using builder style
		return foundStaticImportStyle.get() && !foundBuilderStyle.get();
	}
	
	/**
	 * Checks if a method invocation is using the static import style.
	 * This includes:
	 * - RouterFunctions.route() with 2+ parameters (predicate, handler)
	 * - RouterFunction.andRoute() with parameters
	 */
	private boolean isStaticImportStyle(String declaringClassName, String methodName, MethodInvocation node) {
		if ((WebfluxUtils.ROUTER_FUNCTIONS_TYPE.equals(declaringClassName) || 
				WebfluxUtils.MVC_ROUTER_FUNCTIONS_TYPE.equals(declaringClassName))) {
			if ("route".equals(methodName)) {
				// route() with parameters = static style, route() with no parameters = builder style
				List<?> arguments = node.arguments();
				return arguments != null && arguments.size() >= 2;
			}
		}
		
		if ((WebfluxUtils.ROUTER_FUNCTION_TYPE.equals(declaringClassName) ||
			 WebfluxUtils.MVC_ROUTER_FUNCTION_TYPE.equals(declaringClassName))) {
			if ("andRoute".equals(methodName)) {
				// andRoute() is only available in static import style
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Checks if a method invocation is using the builder pattern.
	 * This includes:
	 * - RouterFunctions.route() with no parameters
	 * - HTTP method calls like GET(), POST(), etc. on RouterFunction.Builder
	 * - build() call at the end
	 */
	private boolean isBuilderStyle(String declaringClassName, String methodName, MethodInvocation node) {
		// Check for RouterFunctions.route() with no parameters
		if ((WebfluxUtils.ROUTER_FUNCTIONS_TYPE.equals(declaringClassName) || 
				WebfluxUtils.MVC_ROUTER_FUNCTIONS_TYPE.equals(declaringClassName))) {
			if ("route".equals(methodName)) {
				List<?> arguments = node.arguments();
				return arguments == null || arguments.isEmpty();
			}
		}
		
		// Check for builder-specific methods
		String builderType = WebfluxUtils.ROUTER_FUNCTIONS_TYPE + "$Builder";
		String mvcBuilderType = WebfluxUtils.MVC_ROUTER_FUNCTIONS_TYPE + "$Builder";
		
		if (builderType.equals(declaringClassName) || mvcBuilderType.equals(declaringClassName)) {
			// Any method on the builder indicates builder pattern
			return true;
		}
		
		return false;
	}
	
	private void provideCodeLensForRouterMethod(CancelChecker cancelToken, MethodDeclaration node, 
			TextDocument document, List<CodeLens> resultAccumulator) {
		cancelToken.checkCanceled();
		
		if (node != null) {
			try {
				CodeLens codeLens = new CodeLens();
				codeLens.setRange(document.toRange(node.getStartPosition(), node.getLength()));
				
				// Get method name and line number
				SimpleName nameNode = node.getName();

				String methodName = nameNode.getIdentifier();
				int lineNumber = document.toPosition(nameNode.getStartPosition()).getLine() + 1; // Convert to 1-based line number
				
				// Replace placeholders in the prompt
				String prompt = QueryType.ROUTER_CONVERSION.getPrompt()
					.replace("$method_name$", methodName)
					.replace("$line_no$", String.valueOf(lineNumber));
				
				Command cmd = new Command();
				cmd.setTitle(QueryType.ROUTER_CONVERSION.getTitle());
				cmd.setCommand(CopilotCodeLensProvider.CMD_SEND_TO_AI_ASSISTANT);
				cmd.setArguments(ImmutableList.of(prompt));
				codeLens.setCommand(cmd);
				
				resultAccumulator.add(codeLens);
			} catch (BadLocationException e) {
				logger.error("Error providing code lens for router function: " + e.getMessage());
			}
		}
	}
}

