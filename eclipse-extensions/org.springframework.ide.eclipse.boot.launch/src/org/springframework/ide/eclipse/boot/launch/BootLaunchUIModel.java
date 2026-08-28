/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import static org.springframework.ide.eclipse.boot.launch.AbstractBootLaunchConfigurationDelegate.DEFAULT_ENABLE_DEBUG_OUTPUT;
import static org.springframework.ide.eclipse.boot.launch.AbstractBootLaunchConfigurationDelegate.ENABLE_DEBUG_OUTPUT;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.ANSI_CONSOLE_OUTPUT;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.DEFAULT_HIDE_FROM_BOOT_DASH;
import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.FAST_STARTUP;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.*;

import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springframework.ide.eclipse.boot.launch.livebean.EnableJmxFeaturesModel;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * Model for the 'main type' selection widgetry on a launchconfiguration tab.
 * <p>
 * Contains the 'logic' for the UI except for the widgets themselves.
 * Can be unit tested without having to instantiate launch configuration dialogs
 * etc.
 *
 * @author Kris De Volder
 */
public class BootLaunchUIModel {

	// TODO pulling out all of the logic for regression testing is a work in progress.
	//   Only some of the UI elements are represented in here. The other ones
	//   still are 'tangled' with the UI widgetry code.

	public static class MainTypeValidator extends Validator {

		private LiveVariable<String> mainTypeName;

		public MainTypeValidator(LiveVariable<String> n) {
			this.mainTypeName = n;
			dependsOn(mainTypeName);
		}

		protected ValidationResult compute() {
			String name = mainTypeName.getValue();
			if (!StringUtil.hasText(name)) {
				return ValidationResult.error("No Main type selected");
			}
			return ValidationResult.OK;
		}
	}

	public final SelectProjectLaunchTabModel project;
	public final MainTypeNameLaunchTabModel mainTypeName;
	public final ProfileLaunchTabModel profile;
	public final LaunchTabSelectionModel<Boolean> enableDebug;
	public final EnableJmxFeaturesModel enableJmx;
	public final LaunchTabSelectionModel<Boolean> hideFromDash;
	public final LaunchTabSelectionModel<Boolean> autoConnect;
	public final LaunchTabSelectionModel<Boolean> ansiConsoleOutput;
	public final LaunchTabSelectionModel<Boolean> fastStartup;
	public final LaunchTabSelectionModel<Boolean> useThinWrapper;

	public BootLaunchUIModel(IProfileHistory profileHistory) {
		project = SelectProjectLaunchTabModel.create();
		mainTypeName = MainTypeNameLaunchTabModel.create();
		profile = ProfileLaunchTabModel.create(project.selection, profileHistory);
		enableDebug = CheckboxLaunchTabModel.create(ENABLE_DEBUG_OUTPUT, DEFAULT_ENABLE_DEBUG_OUTPUT);
		enableJmx = new EnableJmxFeaturesModel();
		hideFromDash = CheckboxLaunchTabModel.create(HIDE_FROM_BOOT_DASH, DEFAULT_HIDE_FROM_BOOT_DASH);
		autoConnect = CheckboxLaunchTabModel.create(AUTO_CONNECT, DEFAULT_AUTO_CONNECT);
		ansiConsoleOutput = CheckboxLaunchTabModel.create(ANSI_CONSOLE_OUTPUT, BootLaunchConfigurationDelegate.supportsAnsiConsoleOutput());
		fastStartup = CheckboxLaunchTabModel.create(FAST_STARTUP, BootActivator.getDefault().getPreferenceStore()
				.getBoolean(BootPreferences.PREF_BOOT_FAST_STARTUP_DEFAULT));
		Validator thinWrapperValidator = new Validator() {
			@Override
			protected ValidationResult compute() {
				IProject p = project.selection.getValue();
				if (p != null && p.isAccessible() && useThinWrapper != null) {
					Boolean useThinWrapperValue = useThinWrapper.selection.getValue();
					if (useThinWrapperValue != null && useThinWrapperValue) {
						try {
							if (!p.hasNature(SpringBootCore.M2E_NATURE)) {
								return ValidationResult.error("Thin launcher support only works for Maven projects");
							}
						} catch (CoreException e) {
							Log.log(e);
						}
					}
				}
				return ValidationResult.OK;
			}
		};
		useThinWrapper = CheckboxLaunchTabModel.create(USE_THIN_WRAPPER, DEFAULT_USE_THIN_WRAPPER, thinWrapperValidator);
		thinWrapperValidator.dependsOn(useThinWrapper.selection);
		thinWrapperValidator.dependsOn(project.selection);
	}

}
