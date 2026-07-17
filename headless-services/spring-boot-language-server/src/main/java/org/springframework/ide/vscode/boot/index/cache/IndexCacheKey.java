/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.cache;

import java.util.Objects;

/**
 * @author Martin Lippert
 */
public class IndexCacheKey {

	private static final String SEPARATOR = "-";

	private final String project;
	private final String indexer;
	private final String category;
	private final String version;

	public IndexCacheKey(String project, String indexer, String category, String version) {
		this.project = requireNoPathSeparator(project, "project");
		this.indexer = requireNoPathSeparator(indexer, "indexer");
		this.category = requireNoPathSeparator(category, "category");
		this.version = requireNoPathSeparator(version, "version");
	}

	private static String requireNoPathSeparator(String value, String fieldName) {
		if (value != null && (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0)) {
			throw new IllegalArgumentException("Invalid " + fieldName + " for index cache key, must not contain a path separator: " + value);
		}
		return value;
	}

	public String getProject() {
		return project;
	}
	
	public String getIndexer() {
		return indexer;
	}
	
	public String getCategory() {
		return category;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return project + SEPARATOR + indexer + SEPARATOR + category + SEPARATOR + version;
	}

	@Override
	public int hashCode() {
		return Objects.hash(category, indexer, project, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IndexCacheKey other = (IndexCacheKey) obj;
		return Objects.equals(category, other.category) && Objects.equals(indexer, other.indexer)
				&& Objects.equals(project, other.project) && Objects.equals(version, other.version);
	}

	public static IndexCacheKey parse(String fileName) {
		if (fileName != null) {

			String name = removeFileExtension(fileName);
			
			if (name != null && name.length() > 0) {
				String version = lastSegment(name);
	
				int endIndex = Math.max(name.length() - version.length() - 1, 0);
				String remainingName = name.substring(0, endIndex);
				String category = lastSegment(remainingName);
				
				endIndex = Math.max(remainingName.length() - category.length() - 1, 0);
				remainingName = remainingName.substring(0, endIndex);
				String indexer = lastSegment(remainingName);
				
				endIndex = Math.max(remainingName.length() - indexer.length() - 1, 0);
				String project = remainingName.substring(0, endIndex);
	
				return new IndexCacheKey(project, indexer, category, version);
			}
		}
		return null;
	}
	
	private static String lastSegment(String name) {
		int separatorIndex = name.lastIndexOf(SEPARATOR);
		if (separatorIndex > 0) {
			return name.substring(separatorIndex + 1);
		}
		else {
			return name;
		}
	}
	
	private static String removeFileExtension(String name) {
		int fileextensionIndex = name.lastIndexOf(".");
		if (fileextensionIndex >= 0) {
			return name.substring(0, fileextensionIndex);
		}
		else {
			return name;
		}
	}
	
}
