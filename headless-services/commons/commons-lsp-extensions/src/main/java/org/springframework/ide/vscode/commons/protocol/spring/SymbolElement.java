/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import org.eclipse.lsp4j.DocumentSymbol;

public interface SymbolElement extends SpringIndexElement {

	public DocumentSymbol getDocumentSymbol();

	/**
	 * A hash of the source text that this element was created from, computed while indexing.
	 * <p>
	 * Lets clients tell that the source behind an element changed even when the element itself
	 * still looks exactly the same (an edited method body behind an unchanged request mapping,
	 * for example). Elements that don't compute one - and elements restored from an index cache
	 * written before content hashes existed - return null, which simply means "unknown".
	 */
	default String getContentHash() {
		return null;
	}

}
