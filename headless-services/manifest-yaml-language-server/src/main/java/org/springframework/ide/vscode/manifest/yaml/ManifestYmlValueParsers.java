/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;

import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileException;
import org.springframework.ide.vscode.commons.util.EnumValueParser;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.ValueParsers;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeFactory.YAtomicType;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Methods and constants to create/get parsers for some atomic types
 * used in manifest yml schema.
 *
 * @author Kris De Volder
 */
public class ManifestYmlValueParsers {

	public static final ValueParser POS_INTEGER = ValueParsers.integerRange(0, null);

	public static final ValueParser MEMORY = new ValueParser() {

		private final ImmutableSet<String> GIGABYTE = ImmutableSet.of("G", "GB");
		private final ImmutableSet<String> MEGABYTE = ImmutableSet.of("M", "MB");
		private final Set<String> UNITS = Sets.union(GIGABYTE, MEGABYTE);

		@Override
		public Object parse(String str) throws Exception {
			str = str.trim();
			String unit = getUnit(str.toUpperCase());
			if (unit == null) {
				throw new NumberFormatException(
						"'"+str+"' doesn't end with a valid unit of memory ('M', 'MB', 'G' or 'GB')"
				);
			}
			str = str.substring(0, str.length()-unit.length());
			int unitSize = GIGABYTE.contains(unit) ? 1024 : 1;
			int value = Integer.parseInt(str);
			if (value < 0) {
				throw new NumberFormatException("Negative value is not allowed");
			}
			return value * unitSize;
		}

		private String getUnit(String str) {
			for (String u : UNITS) {
				if (str.endsWith(u)) {
					return u;
				}
			}
			return null;
		}
	};

	public static EnumValueParser fromCFValueHints(Callable<Collection<YValueHint>> hintProvider, YAtomicType type, ProblemType problemType) {
		return new EnumValueParser(type.toString(), true /*CF value parsers are potentially long running*/, YTypeFactory.valuesFromHintProvider(hintProvider)) {
			@Override
			protected Exception errorOnParse(String message) {
				return new ReconcileException(message, problemType);
			}
		};
	}

	/**
	 * Parses an HTTP health check endpoint path. Note that this not parse the path portion of an HTTP URI, but verifies
	 * that the WHOLE value is a valid HTTP path, and therefore needs to start with an '/'
	 * <p/>
	 * Example: /appPath, /?check=true, /appPath/test.txt
	 * @return
	 */
	public static ValueParser healthCheckEndpointPath() {
		return new ValueParser() {

			@Override
			public Object parse(String pathVal) throws Exception {
				String parsed = pathVal;
				if (!StringUtil.hasText(pathVal)) {
					throw new IllegalArgumentException("Path requires a value staring with '/'");
				}
				else {
					URI uri = URI.create(pathVal);

					if (uri.getScheme() != null) {
						throw new IllegalArgumentException("Path contains scheme: " + uri.getScheme());
					}
					if (uri.getHost() != null) {
						throw new IllegalArgumentException("Path contains host: " + uri.getHost());
					}
					if (uri.getPort() != -1 ) {
						throw new IllegalArgumentException("Path contains port: " + uri.getPort());
					}
					if (uri.getAuthority() != null) {
						throw new IllegalArgumentException("Path contains authority: " + uri.getAuthority());
					}

					Path path = Paths.get(uri.getPath());
					if (!path.startsWith("/")) {
						throw new IllegalArgumentException("Path must start with a '/'");
					}
				}
				return parsed;
			}
		};
	}

}
