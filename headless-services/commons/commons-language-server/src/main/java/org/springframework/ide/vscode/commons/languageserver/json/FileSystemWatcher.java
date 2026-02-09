/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.json;

import org.eclipse.lsp4j.jsonrpc.util.ToStringBuilder;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * Model object to be used in <code>client/registerCapability</code> JSON message to register a file watcher
 *
 * @author Alex Boyko
 *
 */
public class FileSystemWatcher {

	/**
	 * Interested in create events
	 */
	public static final int CREATE = 1;

	/**
	 * Interested in change events
	 */
	public static final int CHANGE = 2;

	/**
	 * Interested in delete events
	 */
	public static final int DELETE = 4;

	@NonNull
	private String globPattern;

	private int kind = CREATE | CHANGE | DELETE;

	public FileSystemWatcher() {
	}

	public FileSystemWatcher(@NonNull final String globPattern) {
		this(globPattern, CREATE | CHANGE | DELETE);
	}

	public FileSystemWatcher(@NonNull final String globPattern, final int kind) {
		this.globPattern = globPattern;
		this.kind = kind;
	}

	@NonNull
	public String getGlobPattern() {
		return this.globPattern;
	}

	public void setGlobPattern(@NonNull final String globPattern) {
		this.globPattern = globPattern;
	}

	@NonNull
	public int getKind() {
		return this.kind;
	}

	/**
	 * The method / capability to unregister for.
	 */
	public void setKind(final int kind) {
		this.kind = kind;
	}

	@Override
	public String toString() {
		ToStringBuilder b = new ToStringBuilder(this);
		b.add("globPattern", this.globPattern);
		b.add("kind", this.kind);
		return b.toString();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileSystemWatcher other = (FileSystemWatcher) obj;
		if (this.globPattern == null) {
			if (other.globPattern != null)
				return false;
		} else if (!this.globPattern.equals(other.globPattern))
			return false;
		if (this.kind != other.kind)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.globPattern == null) ? 0 : this.globPattern.hashCode());
		result = prime * result + (this.kind);
		return result;
	}

}
