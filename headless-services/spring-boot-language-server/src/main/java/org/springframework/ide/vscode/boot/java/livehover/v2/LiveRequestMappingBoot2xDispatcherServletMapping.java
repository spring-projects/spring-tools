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
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.asm.Type;

import com.google.common.base.Suppliers;

public class LiveRequestMappingBoot2xDispatcherServletMapping implements LiveRequestMapping {

/*

Example entry:

{
  "handler": "public java.lang.String com.example.demo.RestApi.aha(java.lang.String)",
  "predicate": "{[/genericHello || /aha],methods=[GET]}",
  "details": {
    "requestMappingConditions": {
      "headers": [],
      "methods": [
        "GET"
      ],
      "patterns": [
        "/genericHello",
        "/aha"
      ],
      "produces": [],
      "params": [],
      "consumes": []
    },
    "handlerMethod": {
      "name": "aha",
      "className": "com.example.demo.RestApi",
      "descriptor": "(Ljava/lang/String;)Ljava/lang/String;"
    }
  }
}
*/

	private JSONObject data;
	private final Supplier<LiveFunctionalRoute> functionalRoute;

	public LiveRequestMappingBoot2xDispatcherServletMapping(JSONObject data) {
		this.data = data;
		this.functionalRoute = Suppliers.memoize(() -> parseFunctionalRoute());
	}

	private LiveFunctionalRoute parseFunctionalRoute() {
		JSONObject handlerFunction = getHandlerFunction();
		if (handlerFunction != null) {
			return LiveFunctionalRoute.parse(handlerFunction.optString("className", null), getPredicate());
		}
		return null;
	}

	@Override
	public LiveFunctionalRoute getFunctionalRoute() {
		return functionalRoute.get();
	}

	private JSONObject getDetails() {
		return data.optJSONObject("details");
	}

	private String getPredicate() {
		return data.optString("predicate");
	}

	private JSONObject getHandlerMethod() {
		JSONObject details = getDetails();
		if (details != null) {
			if (details.has("handlerMethod")) {
				return details.getJSONObject("handlerMethod");
			}
		}
		return null;
	}

	private JSONObject getHandlerFunction() {
		JSONObject details = getDetails();
		if (details != null) {
			if (details.has("handlerFunction")) {
				return details.getJSONObject("handlerFunction");
			}
		}
		return null;
	}

	private JSONObject getRequestMappingConditions() {
		JSONObject details = getDetails();
		return details == null ? null : details.optJSONObject("requestMappingConditions");
	}

	@Override
	public String getMethodString() {
		return data.optString("handler");
	}

	@Override
	public String getFullyQualifiedClassName() {
		JSONObject handlerMethod = getHandlerMethod();
		if (handlerMethod != null) {
			return handlerMethod.getString("className");
		}
		JSONObject handlerFunction = getHandlerFunction();
		if (handlerFunction != null) {
			return handlerFunction.getString("className");
		}
		return null;
	}

	@Override
	public String getMethodName() {
		JSONObject handlerMethod = getHandlerMethod();
		return handlerMethod == null ? null : handlerMethod.getString("name");
	}

	@Override
	public String[] getMethodParameters() {
		JSONObject handlerMethod = getHandlerMethod();
		if (handlerMethod != null) {
			Type type = Type.getMethodType(handlerMethod.getString("descriptor"));
			Type[] argsTypes = type.getArgumentTypes();
			String[] parameterTypes = new String[argsTypes.length];
			for (int i = 0; i < argsTypes.length; i++) {
				parameterTypes[i] = argsTypes[i].getClassName();
			}
			return parameterTypes;
		}
		return new String[0];
	}

	@Override
	public String[] getSplitPath() {
		LiveFunctionalRoute route = getFunctionalRoute();
		if (route != null) {
			return route.path().isEmpty() ? new String[0] : new String[] { route.path() };
		}

		JSONObject rmConditions = getRequestMappingConditions();
		if (rmConditions == null) {
			String predicate = getPredicate();
			if (predicate != null) {
				// Predicate is and/or string expression: ((GET && /hello) && Accept: [text/plain])
				String[] tokens = predicate.split("\\w*(&&|\\|\\|)\\w*");
				List<String> splitPaths = new ArrayList<>(tokens.length);
				for (String t : tokens) {
					// Remove leading `(`, trailing `)`
					String token = removeLeadingAndTrailingParenthises(t);
					if (!token.isEmpty() && token.charAt(0) == '/') {
						splitPaths.add(token);
					}
				}
				return splitPaths.toArray(new String[splitPaths.size()]);
			}
		} else {
			JSONArray jsonArray = rmConditions.getJSONArray("patterns");
			String[] paths = new String[jsonArray.length()];
			for (int i = 0; i < jsonArray.length(); i++) {
				paths[i] = jsonArray.getString(i);
			}
			return paths;
		}
		return new String[0];
	}

	private static String removeLeadingAndTrailingParenthises(String s) {
		int start = 0;
		int end = s.length();
		for(; start < s.length() && (s.charAt(start) == '(' || Character.isWhitespace(s.charAt(start))); start++);
		for(; end > start && (s.charAt(end - 1) == ')' || Character.isWhitespace(s.charAt(end - 1))); end--);
		return start <= end ? s.substring(start, end) : "";
	}

	@Override
	public Set<String> getRequestMethods() {
		LiveFunctionalRoute route = getFunctionalRoute();
		if (route != null) {
			return route.httpMethods();
		}

		JSONObject rmConditions = getRequestMappingConditions();
		if (rmConditions == null) {
			return Collections.emptySet();
		} else {
			JSONArray jsonArray = rmConditions.getJSONArray("methods");
			Set<String> methods = new HashSet<>();
			for (int i = 0; i < jsonArray.length(); i++) {
				methods.add(jsonArray.getString(i));
			}
			return methods;
		}
	}

}
