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
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Route information of a functional (WebFlux.fn / WebMvc.fn) endpoint, parsed from the
 * mappings actuator data of a running app.
 * <p>
 * The actuator folds all nested predicates of a route into a single and-chain and renders
 * it via <code>RequestPredicate.toString()</code>, e.g.:
 * <code>((/person &amp;&amp; Accept: application/json) &amp;&amp; (GET &amp;&amp; /{id}))</code>.
 * The path of the route is the concatenation of all path segments in that chain.
 *
 * @author Martin Lippert
 */
public record LiveFunctionalRoute(String handlerClassName, String path, Set<String> httpMethods) {

	private static final String LAMBDA_MARKER = "$$Lambda";

	private static final Pattern SINGLE_HTTP_METHOD = Pattern.compile("[A-Z]+");
	private static final Pattern MULTIPLE_HTTP_METHODS = Pattern.compile("\\[[A-Z]+(, [A-Z]+)*\\]");

	/**
	 * @return the parsed route, or <code>null</code> if the predicate contains elements that we
	 * can't reliably map to a single path (or-combinations, negations)
	 */
	public static LiveFunctionalRoute parse(String handlerClassName, String predicate) {
		if (predicate == null || predicate.contains("||") || predicate.contains("!")) {
			return null;
		}

		StringBuilder path = new StringBuilder();
		Set<String> httpMethods = new LinkedHashSet<>();

		for (String rawToken : predicate.split("&&")) {
			String token = trimParentheses(rawToken);

			if (token.startsWith("/")) {
				path.append(token);
			}
			else if (SINGLE_HTTP_METHOD.matcher(token).matches()) {
				httpMethods.add(token);
			}
			else if (MULTIPLE_HTTP_METHODS.matcher(token).matches()) {
				for (String method : token.substring(1, token.length() - 1).split(", ")) {
					httpMethods.add(method);
				}
			}
			// other predicates (accept, content-type, version, headers, ...) are not relevant for the path
		}

		return new LiveFunctionalRoute(handlerClassName, normalizePath(path.toString()), Collections.unmodifiableSet(httpMethods));
	}

	/**
	 * Lambdas and method references used as handler functions show up as synthetic classes
	 * of the class in which they are declared, e.g. <code>org.test.RouterConfig$$Lambda/0x0000...</code>
	 *
	 * @return the qualified name (using dots for nested types) of the class that declares the handler function
	 */
	public String getDeclaringClassName() {
		if (handlerClassName == null) {
			return null;
		}

		String className = handlerClassName;
		int lambdaIndex = className.indexOf(LAMBDA_MARKER);
		if (lambdaIndex > 0) {
			className = className.substring(0, lambdaIndex);
		}
		return className.replace('$', '.');
	}

	public static String normalizePath(String path) {
		return path == null ? "" : path.replaceAll("/{2,}", "/");
	}

	private static String trimParentheses(String s) {
		int start = 0;
		int end = s.length();
		for(; start < end && (s.charAt(start) == '(' || Character.isWhitespace(s.charAt(start))); start++);
		for(; end > start && (s.charAt(end - 1) == ')' || Character.isWhitespace(s.charAt(end - 1))); end--);
		return s.substring(start, end);
	}

}
