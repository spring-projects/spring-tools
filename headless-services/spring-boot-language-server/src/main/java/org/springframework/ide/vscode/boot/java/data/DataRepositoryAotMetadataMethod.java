/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

public record DataRepositoryAotMetadataMethod(String name, String signature, DataRepositoryAotMetadataQuery query) {
	
	public String getQueryStatement(DataRepositoryAotMetadata repository) {
		if (repository != null && repository.isJPA()) {
			return getJpaQueryStatement();
		}
		else if (repository != null && repository.isMongoDb()) {
			return getMongoDbQueryStatement();
		}
		else {
			return null;
		}
	}
	
	private String getJpaQueryStatement() {
		return query() != null ? query.query(): null;
	}
	
	private String getMongoDbQueryStatement() {
		List<String> parts = new ArrayList<>();
		
		if (query == null) return null;
		
		if (query().filter() != null) {
			if (!StringUtils.hasText(query().sort())
				&& !StringUtils.hasText(query().fields())
				&& !StringUtils.hasText(query().projection())
				&& !StringUtils.hasText(query().pipeline())) {

				parts.add(query().filter());
			}
			else {
				parts.add("filter = \"" + query().filter() + "\"");
			}
		}
		
		if (query().fields() != null) {
			parts.add("fields = \"" + query().fields() + "\"");
		}
		
		if (query().sort() != null) {
			parts.add("sort = \"" + query().sort() + "\"");
		}

		if (query().projection() != null) {
			parts.add("projection = \"" + query().projection() + "\"");
		}

		if (query().pipeline() != null) {
			parts.add("pipeline = \"" + query().pipeline() + "\"");
		}

		return String.join(", ", parts);
	}

	public Map<String, String> getAttributesMap(DataRepositoryAotMetadata metadata) {
		if (metadata != null && metadata.isJPA()) {
			return Map.of("value", getJpaQueryStatement());
		}
		else if (metadata != null && metadata.isMongoDb()) {
			if (query != null) {
				return createMongoDbQueryAttributes();
			}
		}

		return Map.of();
	}

	private Map<String, String> createMongoDbQueryAttributes() {
		Map<String, String> result = new HashMap<>();
		
		if (query.filter() != null) {
			result.put("value", query.filter());
		}
		
		if (query().fields() != null) {
			result.put("fields", query().fields());
		}
		
		if (query().sort() != null) {
			result.put("sort", query().sort());
		}
		
		// TODO; what about projection and pipeline ?

		return result;
	}

}
