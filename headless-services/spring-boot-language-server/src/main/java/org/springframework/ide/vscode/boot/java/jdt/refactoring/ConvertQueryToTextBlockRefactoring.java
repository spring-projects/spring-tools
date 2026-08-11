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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * A JDT-based refactoring that replaces a {@link StringLiteral} with a
 * {@link TextBlock}, using the already-formatted, escaped text block source
 * (including the surrounding {@code """} delimiters) supplied by the caller.
 * <p>
 * The literal to replace is located by its original offset and length; if the
 * document has changed in the meantime and no {@link StringLiteral} of that
 * exact span exists any more, the refactoring is a no-op.
 */
public class ConvertQueryToTextBlockRefactoring implements JdtRefactoring {

	private final int literalOffset;
	private final int literalLength;
	private final String textBlockValue;

	/**
	 * @param literalOffset  start offset of the {@link StringLiteral} to replace
	 * @param literalLength  length of the {@link StringLiteral} to replace
	 * @param textBlockValue the escaped text block source, including the
	 *                       surrounding {@code """} delimiters
	 */
	public ConvertQueryToTextBlockRefactoring(int literalOffset, int literalLength, String textBlockValue) {
		this.literalOffset = literalOffset;
		this.literalLength = literalLength;
		this.textBlockValue = textBlockValue;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		ASTNode node = NodeFinder.perform(cu, literalOffset, literalLength);
		if (node instanceof StringLiteral literal && literal.getStartPosition() == literalOffset
				&& literal.getLength() == literalLength) {
			TextBlock textBlock = cu.getAST().newTextBlock();
			textBlock.setEscapedValue(textBlockValue);
			rewrite.replace(literal, textBlock, null);
		}
	}

}
