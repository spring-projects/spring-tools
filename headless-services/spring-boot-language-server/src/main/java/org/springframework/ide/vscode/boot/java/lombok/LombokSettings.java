/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.lombok;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * The preferences for the Lombok support while indexing.
 *
 * @param enabled whether the constructors Lombok generates are taken into account at all
 * @param fieldDefaultsFinal every field is final unless marked {@code @NonFinal}, as with
 *        {@code lombok.fieldDefaults.defaultFinal = true} in {@code lombok.config}
 * @param copySpringAnnotations Spring's {@code @Qualifier} and {@code @Value} are copied from a field
 *        onto the generated constructor parameter, as with {@code lombok.copyableAnnotations} in
 *        {@code lombok.config}
 *
 * @author Martin Lippert
 */
public record LombokSettings(boolean enabled, boolean fieldDefaultsFinal, boolean copySpringAnnotations) {

	public static final LombokSettings DISABLED = new LombokSettings(false, false, false);

	private static final String CU_PROPERTY_KEY = LombokSettings.class.getName();

	public static LombokSettings get(ASTNode node) {
		return node.getRoot().getProperty(CU_PROPERTY_KEY) instanceof LombokSettings settings ? settings : DISABLED;
	}

	public static void set(ASTNode node, LombokSettings settings) {
		node.getRoot().setProperty(CU_PROPERTY_KEY, settings);
	}

}
