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
package org.springframework.ide.vscode.boot.java.data;

import java.util.Map;

class JpaAotMethodMetadata extends AbstractDataRepositoryAotMethodMetadata {
	
	record Query(String query) {}
	
	private Query query;

	public JpaAotMethodMetadata(String name, String signature, Query query) {
		super(name, signature);
		this.query = query;
	}

	@Override
	public String getQueryStatement() {
		return query == null ? null : query.query();
	}

	@Override
	public Map<String, String> getAttributesMap() {
		 return Map.of("value", getQueryStatement());
	}

}
