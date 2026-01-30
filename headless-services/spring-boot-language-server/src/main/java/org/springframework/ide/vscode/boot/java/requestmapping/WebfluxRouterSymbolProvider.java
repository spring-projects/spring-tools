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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class WebfluxRouterSymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(WebfluxRouterSymbolProvider.class);
	
	public static void createWebfluxElements(Bean beanDefinition, MethodDeclaration methodDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		Block methodBody = methodDeclaration.getBody();
		if (methodBody != null && methodBody.statements() != null && methodBody.statements().size() > 0) {
			addSymbolsForRouterFunction(beanDefinition, methodBody, context, doc);
		}
	}

//	public void addSymbols(MethodDeclaration methodDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
//		Type returnType = methodDeclaration.getReturnType2();
//		if (returnType != null) {
//
//			ITypeBinding resolvedBinding = returnType.resolveBinding();
//
//			if (resolvedBinding != null && WebfluxUtils.ROUTER_FUNCTION_TYPE.equals(resolvedBinding.getBinaryName())) {
//
//				Block methodBody = methodDeclaration.getBody();
//				if (methodBody != null && methodBody.statements() != null && methodBody.statements().size() > 0) {
//					addSymbolsForRouterFunction(methodBody, context, doc, new ArrayList<>());
//				}
//				else if (SCAN_PASS.ONE.equals(context.getPass())) {
//					context.getNextPassFiles().add(context.getFile());
//				}
//
//			}
//		}
//	}
//

	private static void addSymbolsForRouterFunction(Bean beanDefinition, Block methodBody, SpringIndexerJavaContext context, TextDocument doc) {
		methodBody.accept(new ASTVisitor() {

			@Override
			public boolean visit(MethodInvocation node) {

                // Check if this is the outermost invocation (no parent MethodInvocation)
                ASTNode parent = node.getParent();
                while (parent != null && !(parent instanceof ReturnStatement)) {
                    if (parent instanceof MethodInvocation) {
                        // This is not the outermost invocation
                        return super.visit(node);
                    }
                    parent = parent.getParent();
                }
                
                PreciseWebFnTypeChecker typeChecker = new PreciseWebFnTypeChecker();
	            if (typeChecker.isBuilderMethodInvocation(node)
	            		|| typeChecker.isRouteMethodInvocation(node)
	            		|| typeChecker.isStaticNestInvocation(node)
	            		|| typeChecker.isStaticAndInvocation(node)) {

	            	extractMappingDefinitions(beanDefinition, node, doc, context, typeChecker);
	            }
	            
				return super.visit(node);
			}

		});
	}
	
	protected static void extractMappingDefinitions(Bean beanDefinition, MethodInvocation node, TextDocument doc, SpringIndexerJavaContext context, WebFnTypeChecker typeChecker) {
		
		List<WebFnRouteDefinition> allRoutes = new WebFnRouteExtractor(typeChecker).extractAllRoutes(node, doc);
		if (allRoutes == null || allRoutes.size() == 0) {
			return;
		}
		
		try {
			for (WebFnRouteDefinition routeInfo : allRoutes) {

				WebfluxRouteElement[] pathElements = routeInfo.getPathElements();
				WebfluxRouteElement[] httpMethods = routeInfo.getHttpMethods();
				WebfluxRouteElement[] contentTypes = routeInfo.getContentTypes();
				WebfluxRouteElement[] acceptTypes = routeInfo.getAcceptTypes();
				WebfluxRouteElement version = routeInfo.getVersion();
				String handlerClass = routeInfo.getHandlerClass();
				String handlerMethod = routeInfo.getHandlerMethod();

				StringBuilder pathBuilder = new StringBuilder();
				for (WebfluxRouteElement pathElement : pathElements) {
					pathBuilder.append(pathElement.getElement());
				}

				String path = pathBuilder.toString();

				Location location = new Location(doc.getUri(), routeInfo.getRange());

				WorkspaceSymbol symbol = RouteUtils.createRouteSymbol(location, path, getElementStrings(httpMethods),
						getElementStrings(contentTypes), getElementStrings(acceptTypes), null);

				context.getGeneratedSymbols()
						.add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));

				WebfluxHandlerMethodIndexElement handler = extractHandlerInformation(handlerClass, handlerMethod, path, httpMethods,
						contentTypes, acceptTypes, version, location.getRange(), symbol.getName());

				WebfluxRouteElementRangesIndexElement elements = extractElementsInformation(pathElements, httpMethods,
						contentTypes, acceptTypes);

				if (handler != null) beanDefinition.addChild(handler);
				if (elements != null) beanDefinition.addChild(elements);

			}
		} catch (Exception e) {
			log.error("bad location while extracting mapping symbol for " + doc.getUri(), e);
		}
	}

	private static WebfluxRouteElementRangesIndexElement extractElementsInformation(WebfluxRouteElement[] path, WebfluxRouteElement[] methods,
			WebfluxRouteElement[] contentTypes, WebfluxRouteElement[] acceptTypes) {
		List<Range> allRanges = new ArrayList<>();

		WebfluxRouteElement[][] allElements = new WebfluxRouteElement[][] {path, methods, contentTypes, acceptTypes};
		for (int i = 0; i < allElements.length; i++) {
			for (int j = 0; j < allElements[i].length; j++) {
				allRanges.add(allElements[i][j].getElementRange());
			}
		}

		return new WebfluxRouteElementRangesIndexElement((Range[]) allRanges.toArray(new Range[allRanges.size()]));
	}

	private static WebfluxHandlerMethodIndexElement extractHandlerInformation(String handlerClass, String handlerMethod, String path, WebfluxRouteElement[] httpMethods,
			WebfluxRouteElement[] contentTypes, WebfluxRouteElement[] acceptTypes, WebfluxRouteElement version, Range range, String symbolLabel) {

		return new WebfluxHandlerMethodIndexElement(handlerClass, handlerMethod, path, getElementStrings(httpMethods), getElementStrings(contentTypes),
								getElementStrings(acceptTypes), version != null ? version.getElement() : null, range, symbolLabel);
	}

	private static String[] getElementStrings(WebfluxRouteElement[] routeElements) {
		List<String> result = new ArrayList<>();

		for (int i = 0; i < routeElements.length; i++) {
			result.add(routeElements[i].getElement());
		}

		return (String[]) result.toArray(new String[result.size()]);
	}

}
