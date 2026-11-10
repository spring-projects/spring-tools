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
package org.springframework.ide.vscode.boot.java.utils;

/**
 * Contributes an additional {@link SpringIndexer} to the symbol index, next to the ones that are
 * built in. The beans of this type are picked up by the {@code SpringSymbolIndex}, which gives them
 * the handler their indexer publishes its results to.
 *
 * @author Martin Lippert
 */
public interface SpringIndexerProvider {

	SpringIndexer createIndexer(SymbolHandler symbolHandler);

}
