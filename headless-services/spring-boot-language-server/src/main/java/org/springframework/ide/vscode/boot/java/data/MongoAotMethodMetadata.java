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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

class MongoAotMethodMetadata extends AbstractDataRepositoryAotMethodMetadata {
	
	record Query( String filter, String sort, String projection, String pipeline, String fields) {}
	
	private Query query; 

	public MongoAotMethodMetadata(String name, String signature, Query query) {
		super(name, signature);
		this.query = query;
	}

	@Override
	public String getQueryStatement() {
		List<String> parts = new ArrayList<>();
		
		if (query == null) return null;
		
		if (query.filter() != null) {
			if (!StringUtils.hasText(query.sort())
				&& !StringUtils.hasText(query.fields())
				&& !StringUtils.hasText(query.projection())
				&& !StringUtils.hasText(query.pipeline())) {

				parts.add(query.filter());
			}
			else {
				parts.add("filter = \"" + query.filter() + "\"");
			}
		}
		
		if (query.fields() != null) {
			parts.add("fields = \"" + query.fields() + "\"");
		}
		
		if (query.sort() != null) {
			parts.add("sort = \"" + query.sort() + "\"");
		}

		if (query.projection() != null) {
			parts.add("projection = \"" + query.projection() + "\"");
		}

		if (query.pipeline() != null) {
			parts.add("pipeline = \"" + query.pipeline() + "\"");
		}

		return String.join(", ", parts);
	}

	@Override
	public Map<String, String> getAttributesMap() {
		if (query != null) {
			Map<String, String> result = new HashMap<>();
			
			if (query.filter() != null) {
				result.put("value", query.filter());
			}
			
			if (query.fields() != null) {
				result.put("fields", query.fields());
			}
			
			if (query.sort() != null) {
				result.put("sort", query.sort());
			}
			
			// TODO; what about projection and pipeline ?

			return result;
		}
		return Map.of();
	}

}
