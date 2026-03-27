/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import org.eclipse.lsp4j.Diagnostic;
import org.springframework.ide.vscode.boot.index.cache.AbstractIndexCacheable;

public class CachedDiagnostic extends AbstractIndexCacheable {

	private final Diagnostic diagnostic;

	public CachedDiagnostic(String docURI, Diagnostic diagnostic) {
		super(docURI);
		this.diagnostic = diagnostic;
	}
	
	public Diagnostic getDiagnostic() {
		return this.diagnostic;
	}

}
