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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.Version;

public class MavenMetadata {
	
	private static final Logger log = LoggerFactory.getLogger(MavenMetadata.class);

	private final SortedVersions releaseVersions;

	public MavenMetadata(org.openrewrite.maven.tree.MavenMetadata rawMetadata) {
		List<Version> releases = new ArrayList<>();

		if (rawMetadata != null && rawMetadata.getVersioning() != null && rawMetadata.getVersioning().getVersions() != null) {
			for (String vStr : rawMetadata.getVersioning().getVersions()) {
				try {
					Version v = Version.parse(vStr);
					if (v != null && v.isRelease()) {
						releases.add(v);
					}
				} catch (Exception e) {
					log.warn("Failed to parse %s".formatted(vStr), e);
				}
			}
		}

		this.releaseVersions = new SortedVersions(releases);
	}

	public SortedVersions getReleaseVersions() {
		return releaseVersions;
	}

}
