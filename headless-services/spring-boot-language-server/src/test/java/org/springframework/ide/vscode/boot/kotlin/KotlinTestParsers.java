/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.kotlin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReaders;

/**
 * The parsers the Kotlin tests run against. Setting up a parser can be expensive, so the readers
 * are shared by all tests of the JVM.
 *
 * @author Martin Lippert
 */
public final class KotlinTestParsers {

	private static final Map<String, KotlinSyntaxReader> READERS = new ConcurrentHashMap<>();

	private KotlinTestParsers() {
	}

	/** the names of all registered parsers, the source of the parameters of {@link AllKotlinParsers} */
	public static List<String> names() {
		return KotlinSyntaxReaders.names();
	}

	public static KotlinSyntaxReader reader(String parser) {
		return READERS.computeIfAbsent(parser, KotlinSyntaxReaders::create);
	}

}
