/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.cron;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLangAstUtils;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

public class JdtCronVisitorUtils {
	
	static final String SCHEDULED_SIMPLE_NAME = "Scheduled";
	
	public static EmbeddedLanguageSnippet extractCron(NormalAnnotation node) {
		if (node.getTypeName() != null) {
			String fqn = node.getTypeName().getFullyQualifiedName();
			if (SCHEDULED_SIMPLE_NAME.equals(fqn) || Annotations.SCHEDULED.equals(fqn)) {
				ITypeBinding typeBinding = node.resolveTypeBinding();
				if (typeBinding != null && Annotations.SCHEDULED.equals(typeBinding.getQualifiedName())) {
					for (Object value : node.values()) {
						if (value instanceof MemberValuePair) {
							MemberValuePair pair = (MemberValuePair) value;
							String name = pair.getName().getFullyQualifiedName();
							if (name != null && "cron".equals(name) && isCronExpression(pair.getValue())) {
								return EmbeddedLangAstUtils.extractEmbeddedExpression(pair.getValue());
							}
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isCronExpression(Expression e) {
		String value = null;
		if (e instanceof StringLiteral sl) {
			value = ASTUtils.getLiteralValue(sl);
		} else if (e instanceof TextBlock tb) {
			value = tb.getLiteralValue();
		}
		if (value != null) {
			value = value.trim();
			if (value.startsWith("#{") || value.startsWith("${")) {
				// Either SPEL or Property Holder
				return false;
			}
		}
		return value != null;
	}


}
