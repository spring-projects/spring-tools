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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.Version;

public class SortedVersions {

	private final List<Version> descendingVersions;

	public SortedVersions(Collection<Version> versions) {
		this.descendingVersions = versions.stream()
				.sorted(Comparator.reverseOrder())
				.collect(Collectors.toList());
	}

	/**
	 * Returns the newest GA release with the same major.minor that is strictly newer than
	 * {@code current}. Works correctly when {@code current} is itself a pre-release
	 * (e.g. {@code 3.3.0-M1} → suggests {@code 3.3.0}).
	 */
	public Optional<Version> getNewerLatestPatchRelease(Version current) {
		return descendingVersions.stream()
				.filter(Version::isRelease)
				.filter(v -> v.getMajor() == current.getMajor()
						&& v.getMinor() == current.getMinor()
						&& v.compareTo(current) > 0)
				.findFirst();
	}

	/** Returns the newest GA release with the same major version but a higher minor. */
	public Optional<Version> getNewerLatestMinorRelease(Version current) {
		return descendingVersions.stream()
				.filter(Version::isRelease)
				.filter(v -> v.getMajor() == current.getMajor()
						&& v.getMinor() > current.getMinor())
				.findFirst();
	}

	/** Returns the newest GA release with a higher major version. */
	public Optional<Version> getNewerLatestMajorRelease(Version current) {
		return descendingVersions.stream()
				.filter(Version::isRelease)
				.filter(v -> v.getMajor() > current.getMajor())
				.findFirst();
	}

	public List<Version> toList() {
		return descendingVersions;
	}

}
