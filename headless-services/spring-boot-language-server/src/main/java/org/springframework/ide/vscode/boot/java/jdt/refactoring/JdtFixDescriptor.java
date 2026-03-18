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

import java.util.List;

/**
 * Serializable descriptor for a JDT-based quick fix.
 * <p>
 * Carries the {@link JdtRefactoring} to execute, the document URIs of affected
 * sources, and a human-readable label. This descriptor is stored in
 * {@code CodeAction.data} and travels over the LSP protocol. The
 * {@link JdtRefactoring} is serialized polymorphically via
 * {@link org.springframework.ide.vscode.commons.RuntimeTypeAdapterFactory}.
 *
 * @param refactoring the JDT refactoring to execute
 * @param docUris     document URIs of the source files to apply the refactoring to
 * @param label       human-readable label shown in the quick fix menu
 */
public record JdtFixDescriptor(
		JdtRefactoring refactoring,
		List<String> docUris,
		String label
) {

	public JdtFixDescriptor {
		if (refactoring == null) {
			throw new IllegalArgumentException("refactoring must not be null");
		}
		if (docUris == null || docUris.isEmpty()) {
			throw new IllegalArgumentException("docUris must not be null or empty");
		}
		docUris = List.copyOf(docUris);
	}

}
