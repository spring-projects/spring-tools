/*******************************************************************************
 * Copyright (c) 2015, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import static org.springframework.ide.eclipse.boot.ui.BootUIImages.BOOT_ICON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.launch.livebean.EnableJmxSection;
import org.springframework.ide.eclipse.boot.launch.profiles.ProfileHistory;
import org.springframework.ide.eclipse.boot.launch.profiles.ProfileLaunchTabSection;
import org.springframework.ide.eclipse.boot.launch.properties.PropertiesEditorSection;
import org.springframework.ide.eclipse.boot.launch.util.DelegatingLaunchConfigurationTabSection;
import org.springframework.ide.eclipse.boot.launch.util.GroupLaunchTabSection;
import org.springframework.ide.eclipse.boot.launch.util.LaunchConfigurationTabWithSections;
import org.springframework.ide.eclipse.boot.ui.BootUIImages;
import org.springsource.ide.eclipse.commons.livexp.ui.CheckboxSection;
import org.springsource.ide.eclipse.commons.livexp.ui.HLineSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * @author Kris De Volder
 */
public class BootMainTab extends LaunchConfigurationTabWithSections implements IPageWithSections {

	@Override
	public String getName() {
		return "Spring Boot";
	}

	@Override
	public Image getImage() {
		return BootUIImages.getImage(BOOT_ICON);
	}

	@Override
	protected List<IPageSection> createSections() {
		BootLaunchUIModel model = new BootLaunchUIModel(new ProfileHistory());
		List<WizardPageSection> jvmArgsSections = new ArrayList<>(4);
		jvmArgsSections.add(new EnableDebugSection(this, model.enableDebug));
		jvmArgsSections.add(new HideFromBootDashSection(this, model.hideFromDash));
		jvmArgsSections.add(new FastStartupLaunchTabSection(this, model.fastStartup));
		jvmArgsSections.add(new DelegatingLaunchConfigurationTabSection(this, model.autoConnect, new CheckboxSection(this, model.autoConnect, "Auto-connect to fetch Live Data")));
		/*
		 * Show UI for enabling/disabling ANSI console output only if
		 * IDE supports ANSI console output
		 */
		if (BootLaunchConfigurationDelegate.supportsAnsiConsoleOutput()) {
			jvmArgsSections.add(new DelegatingLaunchConfigurationTabSection(this, model.ansiConsoleOutput, new CheckboxSection(this, model.ansiConsoleOutput, "ANSI console output")));
		}
		if (BootPreferences.getInstance().getThinWrapper() != null) {
			jvmArgsSections.add(new UseThinWrapperSection(this, model.useThinWrapper));
		}

		return Arrays.asList(new IPageSection[] {
				SelectProjectLaunchTabSection.create(this, model.project),
				new MainTypeLaunchTabSection(this, model.project.selection, model.mainTypeName),
				new ProfileLaunchTabSection(this, model.profile),
				new HLineSection(this),
				new GroupLaunchTabSection(this, null, jvmArgsSections.toArray(new WizardPageSection[jvmArgsSections.size()])).columns(2),
				new EnableJmxSection(this, model.enableJmx),
				new HLineSection(this),
				new PropertiesEditorSection(this, model.project.selection)
		});
	}

}
