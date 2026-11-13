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
package org.springframework.ide.vscode.boot.kotlin.syntax;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Kotlin parsers the language server can use, by name. This is the one place to register
 * another {@link KotlinSyntaxReader}: the setting {@code boot-java.kotlin-support.parser} picks
 * one of them, and the tests run against all of them.
 *
 * @author Martin Lippert
 */
public final class KotlinSyntaxReaders {

	/** the parser of the embedded Kotlin compiler */
	public static final String PSI = "psi";

	/** the tree-sitter parser with the Kotlin grammar */
	public static final String TREE_SITTER = "tree-sitter";

	public static final String DEFAULT = PSI;

	private static final Map<String, Supplier<KotlinSyntaxReader>> FACTORIES = new LinkedHashMap<>();

	static {
		FACTORIES.put(PSI, KotlinPsiReader::new);
		FACTORIES.put(TREE_SITTER, KotlinTreeSitterReader::new);
	}

	private KotlinSyntaxReaders() {
	}

	/** the names of all parsers, the default one first */
	public static List<String> names() {
		return List.copyOf(FACTORIES.keySet());
	}

	public static boolean isKnown(String name) {
		return FACTORIES.containsKey(name);
	}

	/**
	 * @param name the name of a parser, the default one is used if the name is null or not known
	 * @return a new reader, which the caller has to dispose
	 */
	public static KotlinSyntaxReader create(String name) {
		return FACTORIES.getOrDefault(name, FACTORIES.get(DEFAULT)).get();
	}

}
