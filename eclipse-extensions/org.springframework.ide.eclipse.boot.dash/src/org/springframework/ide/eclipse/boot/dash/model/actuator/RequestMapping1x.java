/*******************************************************************************
 * Copyright (c) 2015, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.actuator;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ide.eclipse.beans.ui.live.model.TypeLookup;
import org.springframework.ide.eclipse.boot.dash.model.actuator.JLRMethodParser.JLRMethod;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Boot 1.x compatible request mapping implementation
 *
 * @author Kris De Volder
 */
public class RequestMapping1x extends AbstractRequestMapping {

	private JLRMethod methodData;

	private String path;
	private String handler;

	protected RequestMapping1x(String path, String handler, TypeLookup typeLookup) {
		super(typeLookup);
		this.path = path;
		this.handler = handler;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public String toString() {
		return "RequestMapping1x("+path+")";
	}

	@Override
	public String getFullyQualifiedClassName() {
		JLRMethod m = getMethodData();
		if (m != null) {
			return m.getFQClassName();
		}
		return null;
	}

	/**
	 * Returns the raw string found in the requestmapping info. This is a 'toString' value
	 * of java.lang.reflect.Method object.
	 */
	public String getMethodString() {
		try {
			if (handler != null) {
				return handler; //Note: Boot 2.0 handler isn't always a method, but kind of hard to
					// recognize. Since we don't do anything meaningfull if its not a method...
					// its fine to treat everything as a method, as long as we don't make stuff
					// 'explode' if we cannot find the corresponding method in classpath.
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	@Override
	public String getMethodName() {
		JLRMethod m = getMethodData();
		if (m != null) {
			return m.getMethodName();
		}
		return null;
	}

	protected JLRMethod getMethodData() {
		if (methodData == null) {
			methodData = JLRMethodParser.parse(getMethodString());
		}
		return methodData;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((handler == null) ? 0 : handler.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RequestMapping1x other = (RequestMapping1x) obj;
		if (handler == null) {
			if (other.handler != null)
				return false;
		} else if (!handler.equals(other.handler))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	private static Stream<String> processOrPaths(String pathExp) {
		if (pathExp.contains("||")) {
			String[] paths = pathExp.split(Pattern.quote("||"));
			return Stream.of(paths).map(String::trim);
		} else {
			return Stream.of(pathExp);
		}
	}


	private static String extractPath(String key) {
		if (key.startsWith("{[")) {
			//An almost json string. Unfortunately not really json so we can't
			//use org.json or jackson Mapper to properly parse this.
			int start = 2; //right after first '['
			int end = key.indexOf(']');
			if (end >= 2) {
				return key.substring(start, end);
			}
		}
		//Case 1, or some unanticipated stuff.
		//Assume the key is the path, which is right for Case 1
		// and  probably more useful than null for 'unanticipated stuff'.
		return key;
	}

	public static Collection<RequestMapping1x> create(String predicate, String handler, TypeLookup typeLookup) {
		return processOrPaths(extractPath(predicate))
				.map(path -> new RequestMapping1x(path, handler, typeLookup))
				.collect(Collectors.toList());
	}


}
