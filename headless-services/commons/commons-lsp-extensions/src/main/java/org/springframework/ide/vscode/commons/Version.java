/*******************************************************************************
 * Copyright (c) 2022 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {
	
	private static final Pattern VERSION_PATTERN = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:(-|\\.)((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?");
	
	private int major;
	private int minor;
	private int patch;
	private String qualifier;
	
	public Version(int major, int minor, int patch, String qualifier) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.qualifier = qualifier;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	public int getPatch() {
		return patch;
	}

	public String getQualifier() {
		return qualifier;
	}
	
	public String toMajorMinorVersionStr() {
		StringBuilder sb = new StringBuilder();
		sb.append(major);
		sb.append('.');
		sb.append(minor);
		return sb.toString();
	}

	public String toMajorMinorPatchVersionStr() {
		StringBuilder sb = new StringBuilder();
		sb.append(major);
		sb.append('.');
		sb.append(minor);
		sb.append('.');
		sb.append(patch);
		return sb.toString();
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(major);
		sb.append('.');
		sb.append(minor);
		sb.append('.');
		sb.append(patch);
		if (qualifier != null) {
			sb.append('.');
			sb.append(qualifier);
		}
		return sb.toString();
	}

	@Override
	public int compareTo(Version o) {
		if (major == o.major) {
			if (minor == o.minor) {
				return patch - o.patch;
			} else {
				return minor - o.minor;
			}
		} else {
			return major - o.major;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch, qualifier);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Version other = (Version) obj;
		return major == other.major && minor == other.minor && patch == other.patch
				&& Objects.equals(qualifier, other.qualifier);
	}
	
	public static Version parse(String version) {
		Matcher matcher = VERSION_PATTERN.matcher(version);
		if (matcher.find() && matcher.groupCount() > 4) {
			String major = matcher.group(1);
			String minor = matcher.group(2);
			String patch = matcher.group(3);
			String qualifier = matcher.group(5);
			return new Version(
					Integer.parseInt(major),
					Integer.parseInt(minor),
					Integer.parseInt(patch),
					qualifier
			);
		} else {
			String[] tokens = version.split("\\.");
			if (tokens.length <= 3) {
				if (tokens.length >= 1) {
					int major = Integer.parseInt(tokens[0]);
					if (tokens.length >= 2) {
						int minor = Integer.parseInt(tokens[1]);
						if (tokens.length == 3) {
							int patch = Integer.parseInt(tokens[2]);
							return new Version(major, minor, patch, null);
						} else {
							return new Version(major, minor, 0, null);
						}
					} else {
						return new Version(major, 0, 0, null);
					}
				}
			}
		}
		return null;
	}


}
