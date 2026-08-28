/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.profiles;

import static org.springsource.ide.eclipse.commons.core.util.StringUtil.*;

import java.util.Arrays;
import java.util.LinkedList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.launch.IProfileHistory;
import org.springsource.ide.eclipse.commons.frameworks.core.util.ArrayEncoder;


/**
 * An instance of this class is capable of storing and retrieving a history
 * of Profiles (Strings) in project preferences area.
 *
 * @author Kris De Volder
 */
public class ProfileHistory implements IProfileHistory {

	private static final String PROFILE_HISTORY = "spring.boot.launch.profile.history";
	private static final String[] NO_PROFILES = new String[0];

	private int maxProfileHistory = 10;

	public ProfileHistory setMaxHistory(int max) {
		Assert.isLegal(max >= 0);
		this.maxProfileHistory = max;
		return this;
	}

	public String[] getHistory(IProject project) {
		if (project != null) {
			IEclipsePreferences prefs = getPreferences(project);
			if (prefs != null) {
				String[] storedHistory = ArrayEncoder.decode(prefs.get(PROFILE_HISTORY, null));
				if (storedHistory != null) {
					return storedHistory;
				}
			}
		}
		return NO_PROFILES;
	}

	protected void setHistory(IProject project, String[] profiles) {
		try {
			IEclipsePreferences prefs = getPreferences(project);
			if (prefs != null) {
				prefs.put(PROFILE_HISTORY, ArrayEncoder.encode(profiles));
				prefs.flush();
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
	}

	/**
	 * Adds a new profile to the history. May remove oldest profile if
	 * the maxHistory limit is exceeded.
	 */
	public void updateHistory(IProject project, String profile) {
		if (project != null && hasText(profile)) {
			LinkedList<String> history = new LinkedList<>(
					Arrays.asList(getHistory(project)));
			history.remove(profile);
			history.addFirst(profile);
			while (history.size() > maxProfileHistory) {
				history.removeLast();
			}
			setHistory(project, history.toArray(new String[history.size()]));
		}
	}

	/**
	 * Retrieve eclipse preferences node where the history list will be stored.
	 */
	protected IEclipsePreferences getPreferences(IProject p) {
		ProjectScope scope = new ProjectScope(p);
		IEclipsePreferences prefs = scope.getNode(BootActivator.PLUGIN_ID);
		return prefs;
	}
}
