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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.jsonrpc.util.ToStringBuilder;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * Model object to be used in <code>client/registerCapability</code> JSON message to register file watchers
 *
 * @author Alex Boyko
 *
 */
public class DidChangeWatchedFilesRegistrationOptions {

	@NonNull
	private List<FileSystemWatcher> watchers;

	public DidChangeWatchedFilesRegistrationOptions() {
	    this(new ArrayList<FileSystemWatcher>());
	  }

	public DidChangeWatchedFilesRegistrationOptions(@NonNull final List<FileSystemWatcher> watchers) {
	    this.watchers = watchers;
	  }

	@NonNull
	public List<FileSystemWatcher> getRegistrations() {
		return this.watchers;
	}

	public void setRegistrations(@NonNull final List<FileSystemWatcher> watchers) {
		this.watchers = watchers;
	}

	@Override
	public String toString() {
		ToStringBuilder b = new ToStringBuilder(this);
		b.add("watchers", this.watchers);
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
		DidChangeWatchedFilesRegistrationOptions other = (DidChangeWatchedFilesRegistrationOptions) obj;
		if (this.watchers == null) {
			if (other.watchers != null)
				return false;
		} else if (!this.watchers.equals(other.watchers))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.watchers == null) ? 0 : this.watchers.hashCode());
		return result;
	}
}
