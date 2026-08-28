/*******************************************************************************
 * Copyright (c) 2013, 2016 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrDependencySpec.DependencyInfo;
import org.springsource.ide.eclipse.commons.livexp.ui.Ilabelable;

public class MavenCoordinates implements IMavenCoordinates, Ilabelable {

	private final String group;
	private final String artifact;
	private final String version;
	private final String classifier;

	public MavenCoordinates(String group, String artifact, String classifier, String version) {
		this.group = group;
		this.artifact = artifact;
		this.version = version;
		this.classifier = classifier;
	}

	public MavenCoordinates(String group, String artifact, String version) {
		this.group = group;
		this.artifact = artifact;
		this.version = version;
		this.classifier = null;
	}

	public MavenCoordinates(String group, String artifact) {
		this.group = group;
		this.artifact = artifact;
		this.version = null;
		this.classifier = null;
	}

	public MavenCoordinates(DependencyInfo dep) {
		this(dep.getGroupId(),dep.getArtifactId(),dep.getClassifier(),dep.getVersion());
	}

	@Override
	public String getGroupId() {
		return group;
	}

	@Override
	public String getArtifactId() {
		return artifact;
	}

	@Override
	public String getClassifier() {
		return classifier;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return group + ":"+ artifact+":"+(classifier != null ? classifier+":" : "")+version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((artifact == null) ? 0 : artifact.hashCode());
		result = prime * result + ((classifier == null) ? 0 : classifier.hashCode());
		result = prime * result + ((group == null) ? 0 : group.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MavenCoordinates other = (MavenCoordinates) obj;
		if (artifact == null) {
			if (other.artifact != null)
				return false;
		} else if (!artifact.equals(other.artifact))
			return false;
		if (classifier == null) {
			if (other.classifier != null)
				return false;
		} else if (!classifier.equals(other.classifier))
			return false;
		if (group == null) {
			if (other.group != null)
				return false;
		} else if (!group.equals(other.group))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String getLabel() {
		return artifact;
	}

	public String toXmlString() {
		StringBuilder xml = new StringBuilder();
		xml.append("<dependency>\n");
		xml.append("   <artifactId>"+artifact+"</artifactId>\n");
		xml.append("   <groupId>"+artifact+"</groupId>\n");
		if (StringUtils.isNotBlank(version)) {
			xml.append("   <version>"+version+"</version>\n");
		}
		if (StringUtils.isNotBlank(classifier)) {
			xml.append("   <classifier>"+classifier+"</classifier>\n");
		}
		xml.append("</dependency>");
		return xml.toString();
	}
}
