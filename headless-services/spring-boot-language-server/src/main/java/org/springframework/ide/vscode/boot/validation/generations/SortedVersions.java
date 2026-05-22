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

	public Optional<Version> getNewerLatestPatchRelease(Version current) {
		return descendingVersions.stream()
				.filter(v -> v.getMajor() == current.getMajor()
						&& v.getMinor() == current.getMinor()
						&& v.getPatch() > current.getPatch())
				.findFirst();
	}

	public Optional<Version> getNewerLatestMinorRelease(Version current) {
		return descendingVersions.stream()
				.filter(v -> v.getMajor() == current.getMajor()
						&& v.getMinor() > current.getMinor())
				.findFirst();
	}

	public Optional<Version> getNewerLatestMajorRelease(Version current) {
		return descendingVersions.stream()
				.filter(v -> v.getMajor() > current.getMajor())
				.findFirst();
	}

	public List<Version> toList() {
		return descendingVersions;
	}

}
