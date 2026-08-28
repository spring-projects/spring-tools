/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.DEFAULT_PROFILE;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.getProfile;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.setProfile;

import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.util.JavaProjectUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * @author Kris De Volder
 */
public class ProfileLaunchTabModel extends LaunchTabSelectionModel<String> {

	private static final Pattern FILE_NAME_PAT = Pattern.compile("^application-(.*)\\.properties$");
	private static final int FILE_NAME_PAT_GROUP = 1;
	private static final String[] NO_PROFILES =  new String[0];

	public static ProfileLaunchTabModel create(LiveExpression<IProject> project, IProfileHistory profileHistory) {
		LiveVariable<String> profile = new LiveVariable<String>("");
		return new ProfileLaunchTabModel(project, profile, Validator.OK, profileHistory);
	}

	private final LiveExpression<String[]> profiles;

	protected ProfileLaunchTabModel(LiveExpression<IProject> project, LiveVariable<String> selection, LiveExpression<ValidationResult> validator, IProfileHistory profileHistory) {
		super(selection, validator);
		profiles = new ProfileOptions(project, profileHistory);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration conf) {
		String profile = getProfile(conf);
		selection.setValue(profile);
		getDirtyState().setValue(false);
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy conf) {
		String profile = selection.getValue();
		setProfile(conf, profile);
		//Note: it seems logical to update profile history here, but it gets called
		// too often. I.e. not just when the user presses apply button, but really
		// any time a change happens in the launch tab, the LaunchConfig editor copies
		// all the data from the ui to a workingcopy by calling performApply.
		//Therefore, history is instead updated when a launch config is actually
		//launched. (See launch method in BootLaunchConfigurationDelegate)
		getDirtyState().setValue(false);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy conf) {
		setProfile(conf, DEFAULT_PROFILE);
	}

	/**
	 * Live expression that computes list of profiles to show in pull-down menu.
	 */
	public LiveExpression<String[]> profileOptions() {
		return profiles;
	}

	/**
	 * LiveExpression that computes list of suggested profiles for the selected
	 * project.
	 *
	 * @author Kris De Volder
	 */
	private class ProfileOptions extends LiveExpression<String[]> {

		private final IProfileHistory profileHistory;
		private final LiveExpression<IProject> project;

		public ProfileOptions(LiveExpression<IProject> project, IProfileHistory profileHistory) {
			super(NO_PROFILES);
			this.profileHistory = profileHistory;
			this.project = project;
			dependsOn(project);
		}

		@Override
		protected String[] compute() {
			LinkedHashSet<String> profiles = new LinkedHashSet<String>();
			discoverValidProfiles(profiles);
			addHistoricProfiles(profiles);
			return profiles.toArray(new String[profiles.size()]);
		}

		/**
		 * Retrieve a stored list of profiles that have been used in the
		 * past with the selected project. Add these profiles to
		 * the provided List.
		 */
		private void addHistoricProfiles(LinkedHashSet<String> profiles) {
			IProject proj = project.getValue();
			if (proj != null) {
				for (String profile : profileHistory.getHistory(proj)) {
					profiles.add(profile);
				}
			}
		}

		/**
		 * @param profiles discovered profiles are added to this array.
		 */
		private void discoverValidProfiles(LinkedHashSet<String> profiles) {
			try {
				for (IContainer srcFolder : JavaProjectUtil.getSourceFolders(project.getValue())) {
					for (IResource rsrc : srcFolder.members()) {
						if (rsrc.getType() == IResource.FILE) {
							String name = rsrc.getName();
							Matcher matcher = FILE_NAME_PAT.matcher(name);
							if (matcher.matches()) {
								profiles.add(matcher.group(FILE_NAME_PAT_GROUP));
							}
						}
					}
				}
			} catch (Exception e) {
				BootActivator.log(e);
			}
		}
	}
}
