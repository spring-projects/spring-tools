/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation.rules;

import static org.eclipse.jdt.core.IClasspathEntry.CPE_LIBRARY;
import static org.eclipse.jdt.core.IClasspathEntry.CPE_PROJECT;
import static org.eclipse.jdt.core.IClasspathEntry.CPE_SOURCE;

import org.eclipse.jdt.core.IClasspathEntry;

/**
 * Abstraction for matcher that checks condition on a classpath.
 *
 * @author Kris De Volder
 */
public abstract class ClasspathMatcher {

	/**
	 * A 'conservative' value to return when classpath is unknown. Depending on
	 * what the matcher is used for this may be 'true' or 'false' (i.e. this value
	 * has to be chosen to avoid false positives when a rule is being checked at
	 * a time when classpath is in a undefined/inconsistent state.
	 */
	private boolean defaultValue;

	private boolean isCached = false;
	private IClasspathEntry[] cachedFor;
	private boolean cachedValue;

	public ClasspathMatcher(boolean defaultValue) {
		this.defaultValue = defaultValue;
	}

	/**
	 * Perform the matcher operation on given classpath. The classpath is guaranteed to be not null.
	 * This method is not meant to be called by clients. (Clients should call 'match')
	 */
	protected abstract boolean doMatch(IClasspathEntry[] classpath);

	
	protected boolean isProjectWithName(IClasspathEntry entry, String name) {
		if (entry.getEntryKind() == CPE_PROJECT) {
			return name.equals(entry.getPath().segment(0));
		}
		return false;
	}

	protected boolean isSourceFolderInProject(IClasspathEntry e, String projectName) {
		if (e.getEntryKind() == CPE_SOURCE) {
			return projectName.equals(e.getPath().segment(0));
		}
		return false;
	}

	protected boolean isJarNameContaining(IClasspathEntry entry, String nameFragment) {
		if (entry.getEntryKind() == CPE_LIBRARY) {
			return entry.getPath().toString().contains(nameFragment);
		}
		return false;
	}


	public final boolean match(IClasspathEntry[] classpathMaybe) {
		if (isCached && cachedFor == classpathMaybe) {
			return cachedValue;
		} else {
			boolean value;
			if (classpathMaybe != null) {
				value = doMatch(classpathMaybe);
			} else {
				value = defaultValue;
			}
			this.cachedFor = classpathMaybe;
			this.isCached = true;
			this.cachedValue = value;
			return cachedValue;
		}
	}

}
