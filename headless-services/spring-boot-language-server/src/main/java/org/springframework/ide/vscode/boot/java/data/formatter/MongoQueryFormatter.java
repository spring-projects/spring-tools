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
			String formattedJson = new JSONObject(query).toString(4);
			// Indent the formatted JSON to align with Java code (8 spaces)
			return "\n        " + formattedJson.replace("\n", "\n        ");
		} catch (Exception e) {
			// Fallback: just indent
			return "\n        " + query;
		}
	}

}
