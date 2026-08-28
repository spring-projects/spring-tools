/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Uiltily methods for extracting info out of the 'toString' values produced by java.lang.reflect.Method
 * objects.
 *
 * @author Kris De Volder
 */
public class JLRMethodParser {

	public static class JLRMethod {

		/**
		 * The whole 'raw' string (i.e. not parsed or processed in any way).
		 */
		private String rawString;

		private String fqClass;
		private String methodName;
		private String returnType;
		private String[] parameters;

		//TODO: parsing arguments to handle overloading

		public JLRMethod(String method) {
			this.rawString = method;
			String methodString = method;
			// Example: public java.lang.Object org.springframework.boot.actuate.endpoint.mvc.HealthMvcEndpoint.invoke(java.security.Principal)
			// Example: java.util.Collection<demo.Reservation> demo.ReservationRestController.reservations()
			// public org.springframework.http.ResponseEntity<java.util.Map<java.lang.String, java.lang.Object>> org.springframework.boot.autoconfigure.web.BasicErrorController.error(javax.servlet.http.HttpServletRequest)'

			//The spaces inside generics will mess this klunky parser up. So get rid of those first:
			methodString = methodString.replaceAll(",\\s", ",");
			String[] pieces = methodString.split("\\s");
			int modifiersEnd = 0;
			while (modifiersEnd < pieces.length && isModifier(pieces[modifiersEnd])) {
				modifiersEnd++;
			}
			returnType = pieces[modifiersEnd];
			if (pieces.length >= modifiersEnd+2) {
				methodString = pieces[modifiersEnd+1];
				int methodNameEnd = methodString.indexOf('(');
				if (methodNameEnd >= 0) {
					int methodNameStart = methodString.lastIndexOf('.', methodNameEnd);
					if (methodNameStart >= 0) {
						fqClass = methodString.substring(0, methodNameStart);
						if (methodNameStart >= 0) {
							methodNameStart = methodNameStart +1; //+1 because actauly pointing at the '.', not the name start
						}
						methodName = methodString.substring(methodNameStart, methodNameEnd);
					}
				}
			}
			
			int parametersStart = rawString.indexOf('(');
			int parametersEnd = rawString.indexOf(')');
			if (parametersStart < parametersEnd) {
				String parametersString = rawString.substring(parametersStart + 1, parametersEnd);
				this.parameters = parseParameters(parametersString);
			} else {
				this.parameters = new String[0];
			}
		}
		
		private String[] parseParameters(String parameterString) {
			List<String> parameters = new ArrayList<>();
			int openTemplateParameters = 0;
			StringBuilder currentParameter = new StringBuilder();
			for (int i = 0; i < parameterString.length(); i++) {
				char ch = parameterString.charAt(i);
				switch (ch) {
				case '.':
					currentParameter.append(ch);
					break;
				case '<':
					openTemplateParameters++;
					currentParameter.append(ch);
					break;
				case '>':	
					openTemplateParameters--;
					currentParameter.append(ch);
					break;
				case ','	:
					if (openTemplateParameters == 0) {
						if (currentParameter.length() != 0) {
							parameters.add(currentParameter.toString());
							currentParameter = new StringBuilder();
						}
					} else {
						currentParameter.append(ch);
					}
					break;
				default:
					currentParameter.append(ch);
				}
			}
			if (currentParameter.length() > 0) {
				parameters.add(currentParameter.toString());
			}
			return parameters.stream()
					.map(p -> {
						int genericStart = p.indexOf('<');
						int genericEnd = p.lastIndexOf('>');
						if (genericStart < genericEnd) {
							return p.substring(0, genericStart) + p.substring(genericEnd + 1);
						} else {
							return p;
						}
					})
					.map(p -> p.replaceAll("\\.\\.\\.", "[]"))
					.toArray(String[]::new);
		}

		@Override
		public String toString() {
			return rawString;
		}

		public String getFQClassName() {
			return fqClass;
		}

		public String getMethodName() {
			return methodName;
		}
		
		public String getReturnType() {
			return returnType;
		}
		
		public String[] getParameters() {
			return parameters;
		}

	}

	public static final Set<String> MODIFIERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"public", "protected", "private", "abstract",
		    "static", "final", "synchronized", "native", "strictfp"
	)));

	private static boolean isModifier(String string) {
		return MODIFIERS.contains(string);
	}

	public static JLRMethod parse(String method) {
		if (method != null) {
			return new JLRMethod(method);
		}
		return null;
	}

	public static String parseFQClassName(String data) {
		JLRMethod m = parse(data);
		if (m != null) {
			return m.getFQClassName();
		}
		return null;
	}

	public static String parseMethodName(String data) {
		JLRMethod m = parse(data);
		if (m != null) {
			return m.getMethodName();
		}
		return null;
	}

}
