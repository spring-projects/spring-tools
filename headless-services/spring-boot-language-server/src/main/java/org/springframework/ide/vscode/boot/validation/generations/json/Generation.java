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
	 * The latest publicly-available (OSS) patch release for this generation, i.e.
	 * the {@code oss} value of the {@code latestPatch} object. Returns {@code null}
	 * if the generation's OSS support window has ended - at that point {@code latestPatch}
	 * only carries an {@code enterprise} version, which is published to Broadcom's
	 * commercial repository rather than public Maven Central, so it isn't a valid
	 * upgrade suggestion for the general public.
	 */
	public Version getLatestPatchVersion() {
		if (latestPatch == null) {
			return null;
		}
		return Version.parse(latestPatch.get("oss"));
	}

	/**
	 * The {@code latestPatch} entries parsed into {@link Version}s, keyed by
	 * support type ({@code oss} or {@code enterprise}). Unlike
	 * {@link #getLatestPatchVersion()}, this exposes every known patch (including
	 * commercial-only ones) so callers can decide whether/how to offer each as an
	 * upgrade option.
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
