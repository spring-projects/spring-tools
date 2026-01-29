/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 ******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.formatter;

import org.json.JSONObject;

public class MongoQueryFormatter implements QueryFormatter {

	@Override
	public String format(String query) {
		try {
			// Try to format as JSON
			return new JSONObject(query).toString(4);
		} catch (Exception e) {
			// Fallback: just return the query as is
			return query;
		}
	}

}
