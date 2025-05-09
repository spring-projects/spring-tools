/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.List;

import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

@FunctionalInterface
public interface ImplementationHandler {
	List<LocationLink> handle(CancelChecker cancelToken, ImplementationParams implParams);
}
