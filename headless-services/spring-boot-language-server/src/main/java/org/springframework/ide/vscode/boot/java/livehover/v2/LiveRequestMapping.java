/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.Set;

public interface LiveRequestMapping {
	// String getPath(); commented... because... not used??
	String[] getSplitPath();
	String getFullyQualifiedClassName();
	String getMethodName();
	String[] getMethodParameters();
	String getMethodString();
	Set<String> getRequestMethods();

	/**
	 * @return the parsed route information if this mapping is a functional (WebFlux.fn / WebMvc.fn)
	 * route whose predicate could be understood, <code>null</code> otherwise
	 */
	default LiveFunctionalRoute getFunctionalRoute() {
		return null;
	}

	/**
	 * @return the API version this mapping is restricted to (e.g. <code>1.1</code> for <code>version = "1.1+"</code>),
	 * <code>null</code> if the mapping is not versioned or the version is unknown
	 */
	default String getApiVersion() {
		return null;
	}
}
