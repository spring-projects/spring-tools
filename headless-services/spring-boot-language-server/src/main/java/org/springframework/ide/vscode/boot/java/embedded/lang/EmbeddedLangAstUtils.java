/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.embedded.lang;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;

public class EmbeddedLangAstUtils {

	public static EmbeddedLanguageSnippet extractEmbeddedExpression(Expression valueExp) {
		if (valueExp instanceof StringLiteral sl) {
			return new StringLiteralLanguageSnippet(sl);
		} else if (valueExp instanceof TextBlock tb) {
			return new TextBlockLanguageSnippet(tb);
		} else if (valueExp instanceof InfixExpression ie && ie.getOperator() == InfixExpression.Operator.PLUS) {
			EmbeddedLanguageSnippet leftSnippet = extractEmbeddedExpression(ie.getLeftOperand());
			List<EmbeddedLanguageSnippet> snippets = new ArrayList<>(2 + ie.extendedOperands().size()); 
			if (leftSnippet == null) {
				return null;
			} else {
				snippets.add(leftSnippet);
			}
			EmbeddedLanguageSnippet rightSnippet = extractEmbeddedExpression(ie.getRightOperand());
			if (rightSnippet == null) {
				return null;
			} else {
				snippets.add(rightSnippet);
			}
			for (Object o : ie.extendedOperands()) {
				if (o instanceof Expression exp) {
					EmbeddedLanguageSnippet s = extractEmbeddedExpression(exp);
					if (s == null) {
						return null;
					} else {
						snippets.add(s);
					}
				}
			}
			return new CompositeEmbeddedLanguageSnippet(snippets);
		} else if (valueExp instanceof SimpleName se) {
			Object o = se.resolveConstantExpressionValue();
			if (o instanceof String v) {
				return new StringConstantLanguageSnippet(valueExp.getStartPosition(), valueExp.getLength(), v);
			}
		} else if (valueExp instanceof FieldAccess fa) {
			return extractEmbeddedExpression(fa.getName());
		} else if (valueExp instanceof QualifiedName qn) {
			return extractEmbeddedExpression(qn.getName());
		}
		return null;
	}

}
