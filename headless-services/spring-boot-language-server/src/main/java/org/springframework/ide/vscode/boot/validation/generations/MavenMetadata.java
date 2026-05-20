/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ide.vscode.commons.Version;

public class MavenMetadata {

	private final SortedVersions releaseVersions;

	public MavenMetadata(org.openrewrite.maven.tree.MavenMetadata rawMetadata) {
		List<Version> releases = new ArrayList<>();

		if (rawMetadata != null && rawMetadata.getVersioning() != null && rawMetadata.getVersioning().getVersions() != null) {
			for (String vStr : rawMetadata.getVersioning().getVersions()) {
				try {
					Version v = Version.parse(vStr);
					if (isRelease(v)) {
						releases.add(v);
					}
				} catch (Exception e) {
					// Ignore unparseable versions
				}
			}
		}

		this.releaseVersions = new SortedVersions(releases);
	}

	private boolean isRelease(Version v) {
		String qualifier = v.getQualifier();
		return qualifier == null || qualifier.isEmpty() || "RELEASE".equalsIgnoreCase(qualifier);
	}

	public SortedVersions getReleaseVersions() {
		return releaseVersions;
	}

}
