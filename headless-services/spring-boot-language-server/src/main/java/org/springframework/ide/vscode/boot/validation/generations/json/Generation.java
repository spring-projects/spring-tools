/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations.json;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ide.vscode.commons.Version;

public class Generation extends JsonHalLinks {

	private String name;
	private String ossSupportEndDate;
	private String commercialSupportEndDate;
	private String initialReleaseDate;
	private Map<String, String[]> linkedGenerations;
	private Map<String, String> latestPatch;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOssSupportEndDate() {
		return ossSupportEndDate;
	}

	public void setOssSupportEndDate(String ossSupportEndDate) {
		this.ossSupportEndDate = ossSupportEndDate;
	}

	public String getCommercialSupportEndDate() {
		return commercialSupportEndDate;
	}

	public void setCommercialSupportEndDate(String commercialSupportEndDate) {
		this.commercialSupportEndDate = commercialSupportEndDate;
	}

	public String getInitialReleaseDate() {
		return initialReleaseDate;
	}

	public void setInitialReleaseDate(String initialReleaseDate) {
		this.initialReleaseDate = initialReleaseDate;
	}

	public Map<String, String[]> getLinkedGenerations() {
		return linkedGenerations;
	}

	public void setLinkedGenerations(Map<String, String[]> linkedGenerations) {
		this.linkedGenerations = linkedGenerations;
	}

	public Map<String, String> getLatestPatch() {
		return latestPatch;
	}

	public void setLatestPatch(Map<String, String> latestPatch) {
		this.latestPatch = latestPatch;
	}

	/**
	 * OSS-only latest patch; {@code null} once OSS support has ended (enterprise-only from then on).
	 */
	public Version getLatestPatchVersion() {
		if (latestPatch == null) {
			return null;
		}
		return Version.parse(latestPatch.get("oss"));
	}

	/**
	 * All {@code latestPatch} entries, keyed by type ({@code oss}/{@code enterprise}).
	 */
	public Map<String, Version> getLatestPatchByType() {
		Map<String, Version> result = new LinkedHashMap<>();
		if (latestPatch != null) {
			for (Map.Entry<String, String> e : latestPatch.entrySet()) {
				Version v = Version.parse(e.getValue());
				if (v != null) {
					result.put(e.getKey(), v);
				}
			}
		}
		return result;
	}

}
