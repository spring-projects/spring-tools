/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java.ls;

import java.util.Map;
import java.util.Objects;

import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

public interface ClasspathListener {

	void changed(Event event);

	public static class Event {

		public final String projectUri;
		public final String name;
		public final boolean deleted;
		public final Classpath classpath;
		public final ProjectBuild projectBuild;
		public final Map<String, String> javaCoreOptions;

		public Event(String projectUri, String name, boolean deleted, Classpath classpath, ProjectBuild projectBuild, Map<String, String> javaCoreOptions) {
			super();
			this.projectUri = projectUri;
			this.name = name;
			this.deleted = deleted;
			this.classpath = classpath;
			this.projectBuild = projectBuild;
			this.javaCoreOptions = Objects.requireNonNull(javaCoreOptions, "javaCoreOptions must not be null");
		}

		@Override
		public String toString() {
			return "Event [projectUri=" + projectUri + ", name=" + name + ", deleted=" + deleted + ", classpath="
					+ classpath + ", projectBuild=" + projectBuild + "]";
		}

	}

}
