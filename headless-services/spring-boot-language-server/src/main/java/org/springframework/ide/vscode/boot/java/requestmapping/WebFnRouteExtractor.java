/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class WebFnRouteExtractor {
	
	private static final Logger log = LoggerFactory.getLogger(WebFnRouteExtractor.class);
	
	// HTTP method constants
	private static final String METHOD_GET = "GET";
	private static final String METHOD_POST = "POST";
	private static final String METHOD_PUT = "PUT";
	private static final String METHOD_DELETE = "DELETE";
	private static final String METHOD_PATCH = "PATCH";
	private static final String METHOD_OPTIONS = "OPTIONS";
	private static final String METHOD_HEAD = "HEAD";
	
	// HTTP method set for fast lookup
	private static final Set<String> HTTP_METHODS = new HashSet<>(Arrays.asList(
		METHOD_GET, METHOD_POST, METHOD_PUT, METHOD_DELETE, 
		METHOD_PATCH, METHOD_OPTIONS, METHOD_HEAD
	));
	
	// Predicate method name constants
	private static final String PREDICATE_PATH = "path";
	private static final String PREDICATE_NEST = "nest";
	private static final String PREDICATE_ROUTE = "route";
	private static final String PREDICATE_ACCEPT = "accept";
	private static final String PREDICATE_CONTENT_TYPE = "contentType";
	private static final String PREDICATE_VERSION = "version";
	private static final String PREDICATE_METHOD = "method";
	private static final String PREDICATE_AND = "and";
	private static final String PREDICATE_OR = "or";
	
	// Prefix constants
	private static final String PREFIX_MEDIA_TYPE = "MediaType.";
	private static final String PREFIX_HTTP_METHOD = "HttpMethod.";
    
    private WebFnTypeChecker typeChecker;
    
    /**
     * Handler interface for processing predicate chains.
     * @param <T> The type of result to accumulate during traversal
     */
    @FunctionalInterface
    private interface PredicateHandler<T> {
    	
        T handle(String methodName, MethodInvocation predicate, TextDocument doc) throws BadLocationException;
        
        default T merge(T current, T nested) {
            return current != null ? current : nested;
        }
    }
    
    public WebFnRouteExtractor(WebFnTypeChecker typeChecker) {
    	this.typeChecker = typeChecker;
    }
    
    public List<WebFnRouteDefinition> extractAllRoutes(MethodInvocation routerChain, TextDocument doc) {
        List<WebFnRouteDefinition> routes = new ArrayList<>();
        
        try {
	        WebFnRouteExtractionContext context = new WebFnRouteExtractionContext();
	        
	        // Detect which pattern is being used
	        if (typeChecker.isBuilderMethodInvocation(routerChain)) {
	            processBuilderMethodChain(routerChain, context, routes, doc);
	        } else if (isStaticRouterMethod(routerChain)) {
	            processStaticMethodChain(routerChain, context, routes, doc);
	        }
        }
        catch (BadLocationException e) {
        	log.error("error identifying the location in the doc: ", doc.getUri(), e);
        }
        
        return routes;
    }
    
    private boolean isStaticRouterMethod(MethodInvocation methodInvocation) {
    	return typeChecker.isRouteMethodInvocation(methodInvocation)
    			|| typeChecker.isStaticNestInvocation(methodInvocation)
    			|| typeChecker.isStaticAndInvocation(methodInvocation);
    }
    
    private Range createRange(ASTNode node, TextDocument doc) throws BadLocationException {
        if (node == null) {
            return null;
        }
        
        if (node instanceof MethodInvocation invocation) {
    		int methodNameStart = invocation.getName().getStartPosition();
    		int invocationStart = invocation.getStartPosition();

    		return doc.toRange(methodNameStart, node.getLength() - (methodNameStart - invocationStart));
        }
        else {
        	return doc.toRange(node.getStartPosition(),  node.getLength());
        }
        
    }
    
    private WebfluxRouteElement createRouteElement(String value, ASTNode node, TextDocument doc) throws BadLocationException {
        return new WebfluxRouteElement(value, createRange(node, doc));
    }
    
    /**
     * Extracts a string literal or expression as a route element.
     * If the expression is a StringLiteral, uses its literal value.
     * Otherwise, uses the toString() representation of the expression.
     */
    private WebfluxRouteElement extractStringOrExpression(Expression expr, TextDocument doc) throws BadLocationException {
        if (expr instanceof StringLiteral literal) {
            return createRouteElement(literal.getLiteralValue(), literal, doc);
        }
        return createRouteElement(expr.toString(), expr, doc);
    }
    
    /**
     * Removes a prefix from a string if present.
     */
    private String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
    
    private void processBuilderMethodChain(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        if (method == null) return;
        
        // Process receiver first (to maintain order)
        Expression receiver = method.getExpression();
        if (receiver instanceof MethodInvocation) {
            processBuilderMethodChain((MethodInvocation) receiver, context, routes, doc);
        }
        
        String methodName = method.getName().getIdentifier();
        
        // Handle different method types
        if (methodName.equals(PREDICATE_PATH)) {
            handleBuilderPathMethod(method, context, routes, doc);
        } else if (methodName.equals(PREDICATE_NEST)) {
            handleBuilderNestMethod(method, context, routes, doc);
        } else if (isHttpMethod(methodName)) {
            handleBuilderHttpMethod(method, context, routes, doc);
        } else if (methodName.equals(PREDICATE_ROUTE) && !method.arguments().isEmpty()) {
            handleBuilderRouteWithPredicate(method, context, routes, doc);
        }
        // Skip route() without args and build()
    }
    
    private void handleBuilderPathMethod(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        List<?> args = method.arguments();
        if (args.isEmpty()) return;
        
        WebfluxRouteElement pathPrefix = null;
        LambdaExpression lambda = null;
        
        for (Object arg : args) {
            if (arg instanceof StringLiteral) {
                StringLiteral literal = (StringLiteral) arg;
                pathPrefix = createRouteElement(literal.getLiteralValue(), literal, doc);
            } else if (arg instanceof LambdaExpression) {
                lambda = (LambdaExpression) arg;
            }
        }
        
        if (pathPrefix != null && lambda != null) {
            context.pushPathPrefix(pathPrefix);
            processLambdaBody(lambda, context, routes, doc);
            context.popPathPrefix();
        }
    }
    
    private void handleBuilderNestMethod(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        List<?> args = method.arguments();
        if (args.size() < 2) return;
        
        Expression predicate = (Expression) args.get(0);
        LambdaExpression lambda = null;
        
        if (args.get(1) instanceof LambdaExpression) {
            lambda = (LambdaExpression) args.get(1);
        }
        
        if (lambda != null) {
            // Extract and push predicate context (path, accept, contentType, version, etc.)
            WebfluxRouteElement pathPrefix = null;
            List<WebfluxRouteElement> acceptTypes = new ArrayList<>();
            List<WebfluxRouteElement> contentTypes = new ArrayList<>();
            WebfluxRouteElement version = null;
            
            if (predicate instanceof MethodInvocation) {
                MethodInvocation predicateInvocation = (MethodInvocation) predicate;
                
                pathPrefix = extractPathFromPredicateChain(predicateInvocation, acceptTypes, contentTypes, doc);
                version = extractVersionFromPredicateChain(predicateInvocation, doc);
            }
            
            if (pathPrefix != null) {
                context.pushPathPrefix(pathPrefix);
            }
            if (!acceptTypes.isEmpty()) {
                context.pushAcceptTypes(acceptTypes);
            }
            if (!contentTypes.isEmpty()) {
                context.pushContentTypes(contentTypes);
            }
            if (version != null) {
                context.pushVersion(version);
            }
            
            processLambdaBody(lambda, context, routes, doc);
            
            if (version != null) {
                context.popVersion();
            }
            if (!contentTypes.isEmpty()) {
                context.popContentTypes();
            }
            if (!acceptTypes.isEmpty()) {
                context.popAcceptTypes();
            }
            if (pathPrefix != null) {
                context.popPathPrefix();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
	private void handleBuilderHttpMethod(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        WebFnRouteDefinition route = new WebFnRouteDefinition();
        route.addHttpMethod(createRouteElement(method.getName().getIdentifier(), method.getName(), doc));
        route.setNestingLevel(context.getNestingLevel());
        
        // Set range for the builder pattern - covers the method invocation that includes the handler reference
        route.setRange(createRange(method, doc));
        
        WebfluxRouteElement localPath = null;
        List<WebfluxRouteElement> localAcceptTypes = new ArrayList<>();
        List<WebfluxRouteElement> localContentTypes = new ArrayList<>();
        WebfluxRouteElement localVersion = null;
        
        // Parse arguments
        List<Expression> args = method.arguments();
        for (Expression expr : args) {
            
            if (expr instanceof StringLiteral) {
                StringLiteral literal = (StringLiteral) expr;
                localPath = createRouteElement(literal.getLiteralValue(), literal, doc);
            } else if (expr instanceof MethodReference || expr instanceof LambdaExpression) {
                typeChecker.extractHandlerInfo(expr, route);
            } else if (expr instanceof MethodInvocation) {
                // Local predicates (accept, contentType, version on this specific route)
                // Use extractPredicateInfo to handle chained predicates like accept().and(contentType())
                MethodInvocation mi = (MethodInvocation) expr;
                WebfluxRouteElement extractedVersion = extractPredicateInfo(mi, localAcceptTypes, localContentTypes, doc);
                if (extractedVersion != null) {
                    localVersion = extractedVersion;
                }
            }
        }
        
        // Only set path if we found one, otherwise use empty string
        if (localPath == null) {
            localPath = createRouteElement("", null, doc);
        }
        route.setPath(localPath);
        
        // Collect all path elements individually
        List<WebfluxRouteElement> allPathElements = context.getAllPathElements(localPath);
        for (WebfluxRouteElement pathElement : allPathElements) {
            route.addPathElement(pathElement);
        }
        
        // Merge nested predicates with route-specific ones
        List<WebfluxRouteElement> allAcceptTypes = new ArrayList<>(context.getCurrentAcceptTypes());
        allAcceptTypes.addAll(localAcceptTypes);
        for (WebfluxRouteElement acceptType : allAcceptTypes) {
            route.addAcceptType(acceptType);
        }
        
        List<WebfluxRouteElement> allContentTypes = new ArrayList<>(context.getCurrentContentTypes());
        allContentTypes.addAll(localContentTypes);
        for (WebfluxRouteElement contentType : allContentTypes) {
            route.addContentType(contentType);
        }
        
        // Set version (local overrides nested)
        route.setVersion(localVersion != null ? localVersion : context.getCurrentVersion());
        
        routes.add(route);
    }
    
    @SuppressWarnings("unchecked")
	private void handleBuilderRouteWithPredicate(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        WebFnRouteDefinition route = new WebFnRouteDefinition();
        route.setNestingLevel(context.getNestingLevel());
        
        // Set range for the builder pattern - covers the route() method invocation with handler reference
        route.setRange(createRange(method, doc));
        
        List<Expression> args = method.arguments();
        if (args.isEmpty()) return;
        
        Expression predicate = args.get(0);
        
        if (predicate instanceof MethodInvocation) {
            analyzeRequestPredicate((MethodInvocation) predicate, route, doc);
        }
        
        // Second arg is handler
        if (args.size() > 1) {
            Expression handler = args.get(1);
            if (handler instanceof MethodReference || handler instanceof LambdaExpression) {
                typeChecker.extractHandlerInfo(handler, route);
            }
        }
        
        // Apply context - collect all path elements individually
        if (route.getPath() != null) {
            List<WebfluxRouteElement> allPathElements = context.getAllPathElements(route.getPath());
            for (WebfluxRouteElement pathElement : allPathElements) {
                route.addPathElement(pathElement);
            }
        }
        
        // Merge nested predicates with route-specific ones
        // Store route-specific types before resetting
        List<WebfluxRouteElement> routeAcceptTypes = new ArrayList<>(route.getAcceptTypes());
        List<WebfluxRouteElement> routeContentTypes = new ArrayList<>(route.getContentTypes());
        
        route.resetAcceptTypes();
        // Add context types first
        for (WebfluxRouteElement acceptType : context.getCurrentAcceptTypes()) {
            route.addAcceptType(acceptType);
        }
        // Then add route-specific types
        for (WebfluxRouteElement acceptType : routeAcceptTypes) {
            route.addAcceptType(acceptType);
        }
        
        route.resetContentTypes();
        // Add context types first
        for (WebfluxRouteElement contentType : context.getCurrentContentTypes()) {
            route.addContentType(contentType);
        }
        // Then add route-specific types
        for (WebfluxRouteElement contentType : routeContentTypes) {
            route.addContentType(contentType);
        }
        
        // Set version from context if not already set
        if (route.getVersion() == null) {
            route.setVersion(context.getCurrentVersion());
        }
        
        routes.add(route);
    }

    private void processLambdaBody(LambdaExpression lambda, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        ASTNode body = lambda.getBody();
        
        if (body instanceof Block block) {
            // Lambda with block: b -> { ... }
            for (Object stmt : block.statements()) {
                if (stmt instanceof ReturnStatement returnStmt) {
                    Expression expr = returnStmt.getExpression();
                    if (expr instanceof MethodInvocation methodInvocation) {
                        processBuilderMethodChain(methodInvocation, context, routes, doc);
                    }
                } else if (stmt instanceof ExpressionStatement exprStmt) {
                    Expression expr = exprStmt.getExpression();
                    if (expr instanceof MethodInvocation) {
                        processBuilderMethodChain((MethodInvocation) expr, context, routes, doc);
                    }
                }
            }
        } else if (body instanceof MethodInvocation methodInvocation) {
            // Lambda with expression: b -> b.GET(...)
            processBuilderMethodChain(methodInvocation, context, routes, doc);
        }
    }

    @SuppressWarnings("unchecked")
	private void analyzeRequestPredicate(MethodInvocation predicate, WebFnRouteDefinition route, TextDocument doc) throws BadLocationException {
        String methodName = predicate.getName().getIdentifier();
        
        if (isHttpMethod(methodName)) {
            route.addHttpMethod(createRouteElement(methodName, predicate.getName(), doc));
            List<?> args = predicate.arguments();
            if (!args.isEmpty() && args.get(0) instanceof StringLiteral) {
                StringLiteral literal = (StringLiteral) args.get(0);
                route.setPath(createRouteElement(literal.getLiteralValue(), literal, doc));
            }
        } else if (methodName.equals(PREDICATE_METHOD)) {
            // Handle method(HttpMethod.GET) predicate
            List<Expression> args = predicate.arguments();
            if (!args.isEmpty()) {
                Expression arg = args.get(0);
                String argStr = arg.toString();
                // Extract HTTP method from HttpMethod.GET -> GET
                String httpMethod = stripPrefix(argStr, PREFIX_HTTP_METHOD);
                if (!httpMethod.equals(argStr)) {
                    route.addHttpMethod(createRouteElement(httpMethod, arg, doc));
                }
            }
        } else if (methodName.equals(PREDICATE_PATH)) {
            // Handle path() predicate
            WebfluxRouteElement pathElement = extractPathFromPredicate(predicate, doc);
            if (pathElement != null) {
                route.setPath(pathElement);
            }
        } else if (methodName.equals(PREDICATE_ACCEPT)) {
            List<WebfluxRouteElement> acceptTypes = new ArrayList<>();
            extractMediaTypes(predicate, acceptTypes, doc);
            for (WebfluxRouteElement acceptType : acceptTypes) {
                route.addAcceptType(acceptType);
            }
        } else if (methodName.equals(PREDICATE_CONTENT_TYPE)) {
            List<WebfluxRouteElement> contentTypes = new ArrayList<>();
            extractMediaTypes(predicate, contentTypes, doc);
            for (WebfluxRouteElement contentType : contentTypes) {
                route.addContentType(contentType);
            }
        } else if (methodName.equals(PREDICATE_VERSION)) {
            WebfluxRouteElement version = extractVersion(predicate, doc);
            if (version != null) {
                route.setVersion(version);
            }
        } else if (methodName.equals(PREDICATE_AND) || methodName.equals(PREDICATE_OR)) {
            // Handle combined predicates: .and() or .or()
            // Process arguments (right side of the combination)
            List<Expression> args = predicate.arguments();
            for (Expression arg : args) {
                if (arg instanceof MethodInvocation methodnvocation) {
                    analyzeRequestPredicate(methodnvocation, route, doc);
                }
            }
        }
        
        // Handle chained predicates (left side of the chain)
        Expression receiver = predicate.getExpression();
        if (receiver instanceof MethodInvocation methodnvocation) {
            analyzeRequestPredicate(methodnvocation, route, doc);
        }
    }

    /**
     * Generic method to traverse a predicate chain and apply a handler to each node.
     * Handles both chained predicates (receiver) and combined predicates (and/or arguments).
     * @param <T> The type of result to accumulate
     * @param predicate The predicate method invocation to process
     * @param handler The handler to apply to each predicate
     * @param doc The text document
     * @return The accumulated result from traversing the chain
     */
    @SuppressWarnings("unchecked")
    private <T> T traversePredicateChain(MethodInvocation predicate, PredicateHandler<T> handler, TextDocument doc) throws BadLocationException {
        String methodName = predicate.getName().getIdentifier();
        
        // Handle current predicate
        T result = handler.handle(methodName, predicate, doc);
        
        // Handle combined predicates: .and() or .or()
        if (methodName.equals(PREDICATE_AND) || methodName.equals(PREDICATE_OR)) {
            List<Expression> args = predicate.arguments();
            for (Expression arg : args) {
                if (arg instanceof MethodInvocation methodnvocation) {
                    T argResult = traversePredicateChain(methodnvocation, handler, doc);
                    result = handler.merge(result, argResult);
                }
            }
        }
        
        // Handle chained predicates (left side of the chain)
        Expression receiver = predicate.getExpression();
        if (receiver instanceof MethodInvocation methodnvocation) {
            T nestedResult = traversePredicateChain(methodnvocation, handler, doc);
            result = handler.merge(result, nestedResult);
        }
        
        return result;
    }
    
	private WebfluxRouteElement extractPredicateInfo(MethodInvocation predicate, List<WebfluxRouteElement> acceptTypes, List<WebfluxRouteElement> contentTypes, TextDocument doc) throws BadLocationException {
        return traversePredicateChain(predicate, (methodName, pred, document) -> {
            if (methodName.equals(PREDICATE_ACCEPT)) {
                extractMediaTypes(pred, acceptTypes, document);
            } else if (methodName.equals(PREDICATE_CONTENT_TYPE)) {
                extractMediaTypes(pred, contentTypes, document);
            } else if (methodName.equals(PREDICATE_VERSION)) {
                return extractVersion(pred, document);
            }
            return null;
        }, doc);
    }
    
    @SuppressWarnings("unchecked")
	private WebfluxRouteElement extractVersion(MethodInvocation method, TextDocument doc) throws BadLocationException {
        List<Expression> args = method.arguments();
        if (args.isEmpty()) return null;
        
        return extractStringOrExpression(args.get(0), doc);
    }
    
    @SuppressWarnings("unchecked")
	private WebfluxRouteElement extractPathFromPredicate(MethodInvocation method, TextDocument doc) throws BadLocationException {
        List<Expression> args = method.arguments();
        if (args.isEmpty()) return null;
        
        return extractStringOrExpression(args.get(0), doc);
    }
    
    /**
     * Extracts path from a predicate chain, also collecting accept/contentType predicates
     */
	private WebfluxRouteElement extractPathFromPredicateChain(MethodInvocation predicate, 
            List<WebfluxRouteElement> acceptTypes, List<WebfluxRouteElement> contentTypes, TextDocument doc) throws BadLocationException {
        return traversePredicateChain(predicate, (methodName, pred, document) -> {
            if (methodName.equals(PREDICATE_PATH)) {
                return extractPathFromPredicate(pred, document);
            } else if (methodName.equals(PREDICATE_ACCEPT)) {
                extractMediaTypes(pred, acceptTypes, document);
            } else if (methodName.equals(PREDICATE_CONTENT_TYPE)) {
                extractMediaTypes(pred, contentTypes, document);
            }
            return null;
        }, doc);
    }
    
    /**
     * Extracts version from a predicate chain
     */
	private WebfluxRouteElement extractVersionFromPredicateChain(MethodInvocation predicate, TextDocument doc) throws BadLocationException {
        return traversePredicateChain(predicate, (methodName, pred, document) -> {
            if (methodName.equals(PREDICATE_VERSION)) {
                return extractVersion(pred, document);
            }
            return null;
        }, doc);
    }
    
    /**
     * Extracts HTTP methods from a predicate chain
     */
    @SuppressWarnings("unchecked")
	private List<String> extractMethodsFromPredicateChain(MethodInvocation predicate) throws BadLocationException {
        List<String> methods = new ArrayList<>();
        
        // Use a custom handler that accumulates methods in the list
        traversePredicateChain(predicate, new PredicateHandler<Void>() {
            @Override
            public Void handle(String methodName, MethodInvocation pred, TextDocument doc) {
                if (methodName.equals(PREDICATE_METHOD)) {
                    // Extract the HTTP method from method(HttpMethod.GET) or method(RequestMethod.GET)
                    List<Expression> args = pred.arguments();
                    if (!args.isEmpty()) {
                        Expression arg = args.get(0);
                        String argStr = arg.toString();
                        // Extract the method name from HttpMethod.GET or RequestMethod.GET
                        if (argStr.contains(".")) {
                            String method = argStr.substring(argStr.lastIndexOf('.') + 1);
                            methods.add(method);
                        }
                    }
                }
                return null;
            }
            
            @Override
            public Void merge(Void current, Void nested) {
                return null; // Methods are accumulated in the list, no need to merge
            }
        }, null);
        
        return methods;
    }
    
    @SuppressWarnings("unchecked")
	private void extractMediaTypes(MethodInvocation method, List<WebfluxRouteElement> target, TextDocument doc) throws BadLocationException {
        for (Expression expr : (List<Expression>)method.arguments()) {
            String mediaType = stripPrefix(expr.toString(), PREFIX_MEDIA_TYPE);
            target.add(createRouteElement(mediaType, expr, doc));
        }
    }
    
    private boolean isHttpMethod(String methodName) {
        return HTTP_METHODS.contains(methodName);
    }
    
    /**
     * Process static method chains like: route(...).andRoute(...).andRoute(...)
     * or nest(...).and(nest(...))
     */
    private void processStaticMethodChain(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        if (method == null) return;
        
        // Process receiver first (left-to-right order)
        Expression receiver = method.getExpression();
        if (receiver instanceof MethodInvocation receiverMethod) {
            if (isStaticRouterMethod(receiverMethod)) {
                processStaticMethodChain(receiverMethod, context, routes, doc);
            }
        }
        
        // Process current method
        if (typeChecker.isRouteMethodInvocation(method)) {
            handleStaticRouteMethod(method, context, routes, doc);
        } else if (typeChecker.isStaticNestInvocation(method)) {
            handleStaticNestMethod(method, context, routes, doc);
        } else if (typeChecker.isStaticAndInvocation(method)) {
            handleStaticAndMethod(method, context, routes, doc);
        }
    }
    
    /**
     * Handle static route() or andRoute() methods
     * Example: route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson)
     */
    @SuppressWarnings("unchecked")
	private void handleStaticRouteMethod(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        List<Expression> args = method.arguments();
        if (args.isEmpty()) return;
        
        WebFnRouteDefinition route = new WebFnRouteDefinition();
        route.setNestingLevel(context.getNestingLevel());
        
        // Set range for the static pattern - covers the route() or andRoute() method invocation
        route.setRange(createRange(method, doc));
        
        // First arg is the predicate (RequestPredicate)
        Expression predicate = args.get(0);
        if (predicate instanceof MethodInvocation) {
            analyzeRequestPredicate((MethodInvocation) predicate, route, doc);
        }
        
        // Second arg is the handler (if present)
        if (args.size() > 1) {
            Expression handler = args.get(1);
            if (handler instanceof MethodReference || handler instanceof LambdaExpression) {
                typeChecker.extractHandlerInfo(handler, route);
            }
        }
        
        // Apply context - collect all path elements individually
        // If route has no path, use empty string to still collect context paths
        WebfluxRouteElement routePath = route.getPath();
        if (routePath == null && !context.getPathPrefixes().isEmpty()) {
            // No explicit path in route, but we have context paths - use empty path
            routePath = createRouteElement("", null, doc);
            route.setPath(routePath);
        }
        
        if (routePath != null) {
            List<WebfluxRouteElement> allPathElements = context.getAllPathElements(routePath);
            for (WebfluxRouteElement pathElement : allPathElements) {
                route.addPathElement(pathElement);
            }
        }
        
        // Merge nested predicates with route-specific ones
        // Store route-specific types before resetting
        List<WebfluxRouteElement> routeAcceptTypes = new ArrayList<>(route.getAcceptTypes());
        List<WebfluxRouteElement> routeContentTypes = new ArrayList<>(route.getContentTypes());
        
        route.resetAcceptTypes();
        // Add context types first
        for (WebfluxRouteElement acceptType : context.getCurrentAcceptTypes()) {
            route.addAcceptType(acceptType);
        }
        // Then add route-specific types
        for (WebfluxRouteElement acceptType : routeAcceptTypes) {
            route.addAcceptType(acceptType);
        }
        
        route.resetContentTypes();
        // Add context types first
        for (WebfluxRouteElement contentType : context.getCurrentContentTypes()) {
            route.addContentType(contentType);
        }
        // Then add route-specific types
        for (WebfluxRouteElement contentType : routeContentTypes) {
            route.addContentType(contentType);
        }
        
        // Set version from context if not already set
        if (route.getVersion() == null) {
            route.setVersion(context.getCurrentVersion());
        }
        
        // Apply context methods if route has no methods specified
        List<String> contextMethods = context.getCurrentMethods();
        if (!contextMethods.isEmpty() && route.getHttpMethods().isEmpty()) {
            for (String methodName : contextMethods) {
                route.addHttpMethod(createRouteElement(methodName, null, doc));
            }
        }
        
        routes.add(route);
    }
    
    /**
     * Handle static nest() method
     * Example: nest(accept(APPLICATION_JSON), nest(path("/person"), route(...)))
     */
    @SuppressWarnings("unchecked")
	private void handleStaticNestMethod(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        List<Expression> args = method.arguments();
        if (args.size() < 2) return;
        
        // First arg is the predicate
        Expression predicate = args.get(0);
        
        // Second arg is the nested RouterFunction
        Expression nested = args.get(1);
        
        // Extract predicate information
        WebfluxRouteElement pathPrefix = null;
        List<WebfluxRouteElement> acceptTypes = new ArrayList<>();
        List<WebfluxRouteElement> contentTypes = new ArrayList<>();
        WebfluxRouteElement version = null;
        List<String> methods = new ArrayList<>();
        
        if (predicate instanceof MethodInvocation predicateInvocation) {
            pathPrefix = extractPathFromPredicateChain(predicateInvocation, acceptTypes, contentTypes, doc);
            version = extractVersionFromPredicateChain(predicateInvocation, doc);
            methods = extractMethodsFromPredicateChain(predicateInvocation);
        }
        
        // Push context
        if (pathPrefix != null) {
            context.pushPathPrefix(pathPrefix);
        }
        if (!acceptTypes.isEmpty()) {
            context.pushAcceptTypes(acceptTypes);
        }
        if (!contentTypes.isEmpty()) {
            context.pushContentTypes(contentTypes);
        }
        if (version != null) {
            context.pushVersion(version);
        }
        if (!methods.isEmpty()) {
            context.pushMethods(methods);
        }
        
        // Process nested RouterFunction
        if (nested instanceof MethodInvocation nestedMethod) {
            if (isStaticRouterMethod(nestedMethod)) {
                processStaticMethodChain(nestedMethod, context, routes, doc);
            }
        }
        
        // Pop context (in reverse order)
        if (!methods.isEmpty()) {
            context.popMethods();
        }
        if (version != null) {
            context.popVersion();
        }
        if (!contentTypes.isEmpty()) {
            context.popContentTypes();
        }
        if (!acceptTypes.isEmpty()) {
            context.popAcceptTypes();
        }
        if (pathPrefix != null) {
            context.popPathPrefix();
        }
    }
    
    /**
     * Handle static and() method for combining RouterFunctions
     * Example: nest(...).and(nest(...))
     */
    @SuppressWarnings("unchecked")
	private void handleStaticAndMethod(MethodInvocation method, WebFnRouteExtractionContext context, List<WebFnRouteDefinition> routes, TextDocument doc) throws BadLocationException {
        List<Expression> args = method.arguments();
        if (args.isEmpty()) return;
        
        // The argument is another RouterFunction to combine
        Expression other = args.get(0);
        
        if (other instanceof MethodInvocation otherMethod) {
            if (isStaticRouterMethod(otherMethod)) {
                processStaticMethodChain(otherMethod, context, routes, doc);
            }
        }
    }
    
    
    //
    // isolated local manual testing
    //
    
    
    /**
     * Prints routes in a hierarchical format
     */
    public static void printRoutes(List<WebFnRouteDefinition> routes) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("NESTED ROUTE CONFIGURATION");
        System.out.println("=".repeat(80));
        System.out.println("Total routes: " + routes.size());
        System.out.println();
        
        for (int i = 0; i < routes.size(); i++) {
            System.out.println("[Route " + (i + 1) + "]");
            System.out.println(routes.get(i));
        }
        
        System.out.println("=".repeat(80));
    }
    
    /**
     * Prints routes in a table format
     */
    public static void printRoutesTable(List<WebFnRouteDefinition> routes) {
        System.out.println("\n" + "=".repeat(140));
        System.out.println("ROUTE TABLE");
        System.out.println("=".repeat(140));
        System.out.println(String.format("| %-6s | %-30s | %-15s | %-15s | %-10s | %-30s |", 
            "Method", "Full Path", "Accept", "Content", "Version", "Handler"));
        System.out.println("|" + "-".repeat(8) + "|" + "-".repeat(32) + "|" + 
                          "-".repeat(17) + "|" + "-".repeat(17) + "|" + "-".repeat(12) + "|" + "-".repeat(32) + "|");
        
        for (WebFnRouteDefinition route : routes) {
            String methodsStr = !route.getHttpMethods().isEmpty() ? 
                              route.getHttpMethods().stream()
                                  .map(WebfluxRouteElement::getElement)
                                  .reduce((a, b) -> a + ", " + b)
                                  .orElse("N/A") : "N/A";
            
            String fullPathStr = !route.getPathElements().isEmpty() ?
                              route.getPathElements().stream()
                                  .map(WebfluxRouteElement::getElement)
                                  .reduce((a, b) -> a + b)
                                  .orElse("N/A") : "N/A";
            
            String acceptStr = !route.getAcceptTypes().isEmpty() ? 
                              route.getAcceptTypes().stream()
                                  .map(WebfluxRouteElement::getElement)
                                  .reduce((a, b) -> a + ", " + b)
                                  .orElse("N/A") : "N/A";
            
            String contentStr = !route.getContentTypes().isEmpty() ? 
                              route.getContentTypes().stream()
                                  .map(WebfluxRouteElement::getElement)
                                  .reduce((a, b) -> a + ", " + b)
                                  .orElse("N/A") : "N/A";
            
            String versionStr = route.getVersion() != null ? route.getVersion().getElement() : "N/A";
            
            String handlerStr = "N/A";
            if (route.getHandlerClass() != null && route.getHandlerMethod() != null) {
                handlerStr = route.getHandlerClass() + "::" + route.getHandlerMethod();
            } else if (route.getHandlerClass() != null) {
                handlerStr = route.getHandlerClass();
            } else if (route.getHandlerMethod() != null) {
                handlerStr = route.getHandlerMethod();
            }
            
            System.out.println(String.format("| %-6s | %-30s | %-15s | %-15s | %-10s | %-30s |",
                methodsStr.length() > 6 ? methodsStr.substring(0, 3) + "..." : methodsStr,
                fullPathStr.length() > 30 ? fullPathStr.substring(0, 27) + "..." : fullPathStr,
                acceptStr.length() > 15 ? acceptStr.substring(0, 12) + "..." : acceptStr,
                contentStr.length() > 15 ? contentStr.substring(0, 12) + "..." : contentStr,
                versionStr.length() > 10 ? versionStr.substring(0, 7) + "..." : versionStr,
                handlerStr.length() > 30 ? handlerStr.substring(0, 27) + "..." : handlerStr
            ));
        }
        
        System.out.println("=".repeat(140));
    }
    
    /**
     * Example usage - tests both builder and static patterns
     */
    public static void main(String[] args) {
        // Test 1: Builder pattern
        String builderCode = """
				    return RouterFunctions.route()
				        .nest(accept(APPLICATION_JSON), jsonBuilder -> jsonBuilder
				            .nest(path("/person"), personBuilder -> personBuilder
				                .GET("/{id}", handler::getPerson)
				                .GET("", method(HttpMethod.GET), handler::listPeople))
				            .POST("/", contentType(APPLICATION_JSON), handler::createPerson))
				        .build();
				""";
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST 1: BUILDER PATTERN");
        System.out.println("=".repeat(80));
        System.out.println(builderCode);
        testCode(builderCode);
        
        // Test 2: Static method pattern - simple
        String staticSimple = """
				return route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson);
				""";
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST 2: STATIC PATTERN - SIMPLE");
        System.out.println("=".repeat(80));
        System.out.println(staticSimple);
        testCode(staticSimple);
        
        // Test 3: Static method pattern - multiple routes
        String staticMultiple = """
				return route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson)
					.andRoute(POST("/echo").and(accept(TEXT_PLAIN).and(contentType(TEXT_PLAIN))), handler::createPerson)
					.andRoute(GET("/quotes").and(accept(APPLICATION_JSON)), handler::listPeople);
				""";
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST 3: STATIC PATTERN - MULTIPLE ROUTES");
        System.out.println("=".repeat(80));
        System.out.println(staticMultiple);
        testCode(staticMultiple);
        
        // Test 4: Static method pattern - nested
        String staticNested = """
				return nest(accept(APPLICATION_JSON),
					nest(path("/person"),
						route(GET("/{id}"), handler::getPerson)
						.andRoute(method(HttpMethod.GET), handler::listPeople)
					).andRoute(POST("/").and(contentType(APPLICATION_JSON)), handler::createPerson));
				""";
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST 4: STATIC PATTERN - NESTED");
        System.out.println("=".repeat(80));
        System.out.println(staticNested);
        testCode(staticNested);
        
        // Test 5: Static method pattern - complex nested with multiple and/andRoute
        String staticComplexNested = """
				return nest(path("/person"),
					nest(path("/sub1"),
					  nest(path("/sub2"),
						nest(accept(APPLICATION_JSON),
						  route(GET("/{id}"), handler::getPerson)
						  .andRoute(method(HttpMethod.GET), handler::listPeople))
						.andRoute(GET("/nestedGet"), handler::getPerson))
					  .and(nest(path("/andNestPath"),
						route(GET("/andNestPathGET"), handler::getPerson))))
					.andRoute(POST("/").and(contentType(APPLICATION_JSON)), handler::createPerson))
				  .and(nest(method(HttpMethod.DELETE), route(path("/nestedDelete"), handler::deletePerson)));
				""";
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST 5: STATIC PATTERN - COMPLEX NESTED");
        System.out.println("=".repeat(80));
        System.out.println(staticComplexNested);
        testCode(staticComplexNested);
    }
    
    private static void testCode(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_STATEMENTS);
        
        ASTNode block = parser.createAST(null);
        
        SimpleWebFnTypeChecker testTypeChecker = new SimpleWebFnTypeChecker();
        TextDocument doc = new TextDocument("someuri", LanguageId.JAVA, 1, sourceCode);
        
		block.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
                String nodeName = node.getName().getIdentifier();
                
                // Check if this is the outermost invocation (no parent MethodInvocation)
                ASTNode parent = node.getParent();
                while (parent != null && !(parent instanceof ReturnStatement)) {
                    if (parent instanceof MethodInvocation) {
                        // This is not the outermost invocation
                        return super.visit(node);
                    }
                    parent = parent.getParent();
                }
                
                // For builder pattern, look for "build"
                // For static pattern, look for the outermost method (route, andRoute, nest)
                if (nodeName.equals("build") || 
                    nodeName.equals("route") || 
                    nodeName.equals("andRoute") || 
                    nodeName.equals("nest") ||
                    nodeName.equals("and")) {
                    
                	List<WebFnRouteDefinition> routes = new WebFnRouteExtractor(testTypeChecker).extractAllRoutes(node, doc);
                	
                    if (!routes.isEmpty()) {
                        printRoutes(routes);
                        printRoutesTable(routes);
                    } else {
                        System.out.println("No routes extracted!");
                    }
                }

				return super.visit(node);
			}
		});
    }
    
}
