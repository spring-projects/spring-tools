/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.actuator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

	}

	private static final Set<String> MODIFIERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
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
