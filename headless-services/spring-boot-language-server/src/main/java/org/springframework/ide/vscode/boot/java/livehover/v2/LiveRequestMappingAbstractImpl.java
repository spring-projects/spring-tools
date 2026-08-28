/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser.JLRMethod;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public abstract class LiveRequestMappingAbstractImpl implements LiveRequestMapping {

	private static final Pattern REQUEST_METHODS_PATTERN = Pattern.compile(".*methods=\\[(.*)\\].*");

	final private Supplier<JLRMethod> methodDataSupplier;
	final private Supplier<Set<String>> requestMethodsSupplier;
	final private Supplier<String[]> pathsSuplier;

	public LiveRequestMappingAbstractImpl() {
		this.requestMethodsSupplier = Suppliers.memoize(() -> parseRequestMethods());
		this.methodDataSupplier = Suppliers.memoize(() -> JLRMethodParser.parse(getMethodString()));
		this.pathsSuplier = Suppliers.memoize(() -> computePaths());
	}

	protected Set<String> parseRequestMethods() {
		Matcher matcher = REQUEST_METHODS_PATTERN.matcher(getPredicateString());
		if (matcher.matches()) {
			return Arrays.stream(matcher.group(1).split("\\s*,\\s*")).collect(Collectors.toSet());
		}
		return Collections.emptySet();
	}

	@Override
	public Set<String> getRequestMethods() {
		return requestMethodsSupplier.get();
	}

	@Override
	public final String getFullyQualifiedClassName() {
		JLRMethod m = getMethodData();
		if (m != null) {
			return m.getFQClassName();
		}
		return null;
	}

	protected JLRMethod getMethodData() {
		return methodDataSupplier.get();
	}

	@Override
	public final String getMethodName() {
		JLRMethod m = getMethodData();
		if (m != null) {
			return m.getMethodName();
		}
		return null;
	}

	@Override
	public String[] getMethodParameters() {
		return getMethodData().getParameters();
	}

	protected String[] computePaths() {
		//Two cases we know about:
		// 1: the 'predicate' is a path string
		// 2: the 'predicate' looks something like:
		//    "{[/actuator/health],methods=[GET],produces=[application/vnd.spring-boot.actuator.v2+json || application/json]}
		String predicate = getPredicateString();
		if (predicate.startsWith("{[")) {
			//An almost json string. Unfortunately not really json so we can't
			//use org.json or jackson Mapper to properly parse this.
			int start = 2; //right after first '['
			int end = predicate.indexOf(']');
			if (end >= 2) {
				String pathString = predicate.substring(start, end);
				return splitPaths(pathString);
			}
		}
		//Case 1, or some unanticipated stuff.
		//Assume the key is the paths strk g, which is right for Case 1
		// and  probably more useful than null for 'unanticipated stuff'.
		return splitPaths(predicate);
	}

	protected abstract String getPredicateString();

	@Override
	public String[] getSplitPath() {
		return pathsSuplier.get();
	}

	protected String[] splitPaths(String paths) {
		return Arrays.stream(paths.split("\\|\\|"))
				.map(s -> s.trim())
				.filter(s -> !s.isEmpty())
				.map(s -> {
					if (s.charAt(0) != '/') {
						return '/' + s;
					} else {
						return s;
					}
				})
				.toArray(String[]::new);
	}

}
