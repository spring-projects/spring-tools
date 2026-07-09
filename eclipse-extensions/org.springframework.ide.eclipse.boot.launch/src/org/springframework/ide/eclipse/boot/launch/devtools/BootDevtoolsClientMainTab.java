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
package org.springframework.ide.eclipse.boot.launch.devtools;

import static org.springframework.ide.eclipse.boot.ui.BootUIImages.BOOT_DEVTOOLS_ICON;

import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.graphics.Image;
import org.springframework.ide.eclipse.boot.launch.SelectProjectLaunchTabSection;
import org.springframework.ide.eclipse.boot.launch.util.LaunchConfigurationTabWithSections;
import org.springframework.ide.eclipse.boot.ui.BootUIImages;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;

/**
 * @author Kris De Volder
 */
public class BootDevtoolsClientMainTab extends LaunchConfigurationTabWithSections implements IPageWithSections {

	@Override
	public String getName() {
		return "DevTools Client";
	}

	@Override
	public Image getImage() {
		return BootUIImages.getImage(BOOT_DEVTOOLS_ICON);
	}

	@Override
	protected List<IPageSection> createSections() {
		BootDevtoolsClientLaunchUIModel model = new BootDevtoolsClientLaunchUIModel();
		return Arrays.asList(new IPageSection[] {
				SelectProjectLaunchTabSection.create(this, model.project),
				StringFieldLaunchTabSection.create(this, model.remoteUrl),
				StringFieldLaunchTabSection.create(this, model.remoteSecret, true),
//				new MainTypeLaunchTabSection(this, model.project.selection).readonly(true),
//				new ProfileLaunchTabSection(this, model.profile),
//				new HLineSection(this),
//				new EnableDebugSection(this, model.enableDebug),
//				new EnableJmxSection(this, model.enableJmx),
//				new HLineSection(this),
//				new PropertiesTableSection(this, model.project.selection)
		});
	}

}
