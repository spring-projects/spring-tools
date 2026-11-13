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

/**
 * Reads Kotlin source text into {@link KotlinSyntax}. Implementations differ in the parser they
 * use, but all of them have to give the same result for the same source: the syntax tests of the
 * language server run against every implementation that is registered in
 * {@link KotlinSyntaxReaders}.
 * <p>
 * Implementations are thread safe, and tolerate source with syntax errors: they return what is
 * there, as far as it can be recognized.
 *
 * @author Martin Lippert
 */
public interface KotlinSyntaxReader {

	/**
	 * Parses the given source. All ranges in the result are offsets into {@code text}, whatever
	 * line separators it uses.
	 *
	 * @param fileName the name of the file, only used to name the parsed file
	 * @param text the source text
	 */
	KotlinSyntax.File read(String fileName, String text);

	/** releases what the parser holds on to, it is set up again if more files are read */
	void dispose();

}
