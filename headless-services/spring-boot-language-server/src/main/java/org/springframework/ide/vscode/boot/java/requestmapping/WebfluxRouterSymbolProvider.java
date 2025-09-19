/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
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
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class WebfluxRouterSymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(WebfluxRouterSymbolProvider.class);
	
	public static boolean isWebfluxRouterBean(MethodDeclaration method) {
		Type returnType = method.getReturnType2();
		if (returnType != null) {
			ITypeBinding resolvedBinding = returnType.resolveBinding();
			if (resolvedBinding != null && WebfluxUtils.ROUTER_FUNCTION_TYPE.equals(resolvedBinding.getBinaryName())) {
				return true;
			}
		}
		return false;
	}
	
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
				IMethodBinding methodBinding = node.resolveMethodBinding();

				if (methodBinding != null && WebfluxUtils.isRouteMethodInvocation(methodBinding)) {
					extractMappingSymbol(beanDefinition, node, doc, context);
				}

				return super.visit(node);
			}

		});
	}

	protected static void extractMappingSymbol(Bean beanDefinition, MethodInvocation node, TextDocument doc, SpringIndexerJavaContext context) {
		WebfluxRouteElement[] pathElements = extractPath(node, doc);
		WebfluxRouteElement[] httpMethods = extractMethods(node, doc);
		WebfluxRouteElement[] contentTypes = extractContentTypes(node, doc);
		WebfluxRouteElement[] acceptTypes = extractAcceptTypes(node, doc);
		WebfluxRouteElement version = extractVersion(node, doc);
		
		// TODO: version

		int methodNameStart = node.getName().getStartPosition();
		int invocationStart = node.getStartPosition();

		StringBuilder pathBuilder = new StringBuilder();
		for (WebfluxRouteElement pathElement : pathElements) {
			pathBuilder.insert(0, pathElement.getElement());
		}

		String path = pathBuilder.toString();

		if (path.length() > 0) {
			try {

				Location location = new Location(doc.getUri(), doc.toRange(methodNameStart, node.getLength() - (methodNameStart - invocationStart)));
				
				WorkspaceSymbol symbol = RouteUtils.createRouteSymbol(location, path, getElementStrings(httpMethods),
						getElementStrings(contentTypes), getElementStrings(acceptTypes), null);

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));

				WebfluxHandlerMethodIndexElement handler = extractHandlerInformation(node, path, httpMethods, contentTypes, acceptTypes, version, location.getRange(), symbol.getName());
				WebfluxRouteElementRangesIndexElement elements = extractElementsInformation(pathElements, httpMethods, contentTypes, acceptTypes);
				
				if (handler != null) beanDefinition.addChild(handler);
				if (elements != null) beanDefinition.addChild(elements);

			} catch (BadLocationException e) {
				log.error("bad location while extracting mapping symbol for " + doc.getUri(), e);
			}
		}
	}

	private static WebfluxRouteElement extractVersion(MethodInvocation node, TextDocument doc) {
		// TODO Auto-generated method stub
		return null;
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

	private static WebfluxRouteElement[] extractPath(MethodInvocation routerInvocation, TextDocument doc) {
		WebfluxPathFinder pathFinder = new WebfluxPathFinder(routerInvocation, doc);
		List<?> arguments = routerInvocation.arguments();
		for (Object argument : arguments) {
			if (argument != null && argument instanceof ASTNode) {
				((ASTNode)argument).accept(pathFinder);
			}
		}

		List<WebfluxRouteElement> path = pathFinder.getPath();

		extractNestedValue(routerInvocation, path, (methodInvocation) -> {
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();

			try {
				if (methodBinding != null && WebfluxUtils.REQUEST_PREDICATE_PATH_METHOD.equals(methodBinding.getName())) {
					StringLiteral stringLiteral = WebfluxUtils.extractStringLiteralArgument(methodInvocation);
					if (stringLiteral != null) {
						Range range = doc.toRange(stringLiteral.getStartPosition(), stringLiteral.getLength());
						return new WebfluxRouteElement(ASTUtils.getLiteralValue(stringLiteral), range);
					}
				}
			}
			catch (BadLocationException e) {
				log.error("bad location while extracting mapping symbol for " + doc.getUri(), e);
			}
			return null;
		});

		return (WebfluxRouteElement[]) path.toArray(new WebfluxRouteElement[path.size()]);
	}

	private static WebfluxRouteElement[] extractMethods(MethodInvocation routerInvocation, TextDocument doc) {
		WebfluxMethodFinder methodFinder = new WebfluxMethodFinder(routerInvocation, doc);
		List<?> arguments = routerInvocation.arguments();
		for (Object argument : arguments) {
			if (argument != null && argument instanceof ASTNode) {
				((ASTNode)argument).accept(methodFinder);
			}
		}

		final List<WebfluxRouteElement> methods = methodFinder.getMethods();

		extractNestedValue(routerInvocation, methods, (methodInvocation) -> {
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();

			try {
				if (methodBinding != null && WebfluxUtils.REQUEST_PREDICATE_METHOD_METHOD.equals(methodBinding.getName())) {
					QualifiedName qualifiedName = WebfluxUtils.extractQualifiedNameArgument(methodInvocation);
					if (qualifiedName.getName() != null) {
						Range range = doc.toRange(qualifiedName.getStartPosition(), qualifiedName.getLength());
						return new WebfluxRouteElement(qualifiedName.getName().toString(), range);
					}
				}
			}
			catch (BadLocationException e) {
				log.error("bad location while extracting mapping symbol for " + doc.getUri(), e);
			}

			return null;
		});

		return (WebfluxRouteElement[]) methods.toArray(new WebfluxRouteElement[methods.size()]);
	}

	private static WebfluxRouteElement[] extractAcceptTypes(MethodInvocation routerInvocation, TextDocument doc) {
		WebfluxAcceptTypeFinder typeFinder = new WebfluxAcceptTypeFinder(doc);
		List<?> arguments = routerInvocation.arguments();
		for (Object argument : arguments) {
			if (argument != null && argument instanceof ASTNode) {
				((ASTNode)argument).accept(typeFinder);
			}
		}

		final List<WebfluxRouteElement> acceptTypes = typeFinder.getAcceptTypes();

		extractNestedValue(routerInvocation, acceptTypes, (methodInvocation) -> {
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();

			try {
				if (methodBinding != null && WebfluxUtils.REQUEST_PREDICATE_ACCEPT_TYPE_METHOD.equals(methodBinding.getName())) {
					SimpleName nameArgument = WebfluxUtils.extractSimpleNameArgument(methodInvocation);
					if (nameArgument != null && nameArgument.getFullyQualifiedName() != null) {
						Range range = doc.toRange(nameArgument.getStartPosition(),  nameArgument.getLength());
						return new WebfluxRouteElement(nameArgument.getFullyQualifiedName().toString(), range);
					}
				}
			}
			catch (BadLocationException e) {
				log.error("bad location while extracting mapping symbol for " + doc.getUri(), e);
			}

			return null;
		});

		return (WebfluxRouteElement[]) acceptTypes.toArray(new WebfluxRouteElement[acceptTypes.size()]);
	}

	private static WebfluxRouteElement[] extractContentTypes(MethodInvocation routerInvocation, TextDocument doc) {
		WebfluxContentTypeFinder contentTypeFinder = new WebfluxContentTypeFinder(doc);
		List<?> arguments = routerInvocation.arguments();
		for (Object argument : arguments) {
			if (argument != null && argument instanceof ASTNode) {
				((ASTNode)argument).accept(contentTypeFinder);
			}
		}

		final List<WebfluxRouteElement> contentTypes = contentTypeFinder.getContentTypes();

		extractNestedValue(routerInvocation, contentTypes, (methodInvocation) -> {
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();

			try {
				if (methodBinding != null && WebfluxUtils.REQUEST_PREDICATE_CONTENT_TYPE_METHOD.equals(methodBinding.getName())) {
					SimpleName nameArgument = WebfluxUtils.extractSimpleNameArgument(methodInvocation);
					if (nameArgument != null && nameArgument.getFullyQualifiedName() != null) {
						Range range = doc.toRange(nameArgument.getStartPosition(),  nameArgument.getLength());
						return new WebfluxRouteElement(nameArgument.getFullyQualifiedName().toString(), range);
					}
				}
			}
			catch (BadLocationException e) {
				log.error("bad location while extracting mapping symbol for " + doc.getUri(), e);
			}

			return null;
		});

		return (WebfluxRouteElement[]) contentTypes.toArray(new WebfluxRouteElement[contentTypes.size()]);
	}

	private static void extractNestedValue(ASTNode node, Collection<WebfluxRouteElement> values, Function<MethodInvocation, WebfluxRouteElement> extractor) {
		if (node == null || node instanceof TypeDeclaration) {
			return;
		}

		if (node instanceof MethodInvocation) {
			MethodInvocation methodInvocation = (MethodInvocation) node;
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();

			if (methodBinding != null && methodBinding.getDeclaringClass() != null
					&& WebfluxUtils.ROUTER_FUNCTIONS_TYPE.equals(methodBinding.getDeclaringClass().getBinaryName())) {

				String name = methodBinding.getName();
				if (WebfluxUtils.REQUEST_PREDICATE_NEST_METHOD.equals(name)) {
					List<?> arguments = methodInvocation.arguments();
					for (Object argument : arguments) {
						if (argument instanceof MethodInvocation) {
							MethodInvocation nestedMethod = (MethodInvocation) argument;
							WebfluxRouteElement value = extractor.apply(nestedMethod);
							if (value != null) {
								values.add(value);
							}
						}
					}
				}
			}
		}

		extractNestedValue(node.getParent(), values, extractor);
	}

	private static WebfluxHandlerMethodIndexElement extractHandlerInformation(MethodInvocation node, String path, WebfluxRouteElement[] httpMethods,
			WebfluxRouteElement[] contentTypes, WebfluxRouteElement[] acceptTypes, WebfluxRouteElement version, Range range, String symbolLabel) {

		List<?> arguments = node.arguments();

		if (arguments != null) {
			for (Object argument : arguments) {
				if (argument instanceof ExpressionMethodReference) {
					ExpressionMethodReference methodReference = (ExpressionMethodReference) argument;
					IMethodBinding methodBinding = methodReference.resolveMethodBinding();

					if (methodBinding != null && methodBinding.getDeclaringClass() != null && methodBinding.getMethodDeclaration() != null) {
						String handlerClass = methodBinding.getDeclaringClass().getBinaryName();
						if (handlerClass != null) handlerClass = handlerClass.trim();

						String handlerMethod = methodBinding.getMethodDeclaration().toString();
						if (handlerMethod != null) handlerMethod = handlerMethod.trim();

						return new WebfluxHandlerMethodIndexElement(handlerClass, handlerMethod, path, getElementStrings(httpMethods), getElementStrings(contentTypes),
								getElementStrings(acceptTypes), version.getElement(), range, symbolLabel);
					}
				}
			}
		}

		return null;
	}

	private static String[] getElementStrings(WebfluxRouteElement[] routeElements) {
		List<String> result = new ArrayList<>();

		for (int i = 0; i < routeElements.length; i++) {
			result.add(routeElements[i].getElement());
		}

		return (String[]) result.toArray(new String[result.size()]);
	}

}
