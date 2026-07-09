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

import org.eclipse.core.resources.IProject;
import org.springframework.ide.eclipse.boot.launch.ExistingBootProjectSelectionValidator;
import org.springframework.ide.eclipse.boot.launch.SelectProjectLaunchTabModel;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.StringFieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.validators.UrlValidator;

/**
 * Model for the 'main type' selection widgetry on a launchconfiguration tab.
 * <p>
 * Contains the 'logic' for the UI except for the widgets themselves.
 * Can be unit tested without having to instantiate launch configuration dialogs
 * etc.
 *
 * @author Kris De Volder
 */
public class BootDevtoolsClientLaunchUIModel {

	public final SelectProjectLaunchTabModel project;
	public final StringFieldLaunchTabModel remoteUrl;
	public final StringFieldLaunchTabModel remoteSecret;

	public BootDevtoolsClientLaunchUIModel() {
		project = createProjectSelectionModel();
		remoteUrl = createRemoteUrlModel();
		remoteSecret = createRemoteSecretModel();
	}

	private StringFieldLaunchTabModel createRemoteUrlModel() {
		StringFieldModel field = new StringFieldModel("Remote Url", "");
		field.validator(
				new UrlValidator(field)
					.allowedSchemes("http", "https")
		);
		return new StringFieldLaunchTabModel(field, BootDevtoolsClientLaunchConfigurationDelegate.REMOTE_URL);
	}

	private StringFieldLaunchTabModel createRemoteSecretModel() {
		StringFieldModel field = new StringFieldModel("Remote Secret", "");
		return new RemoteSecretLaunchTabModel(field);
	}


	private SelectProjectLaunchTabModel createProjectSelectionModel() {
		LiveVariable<IProject> project = new LiveVariable<IProject>();
		CompositeValidator validator = new CompositeValidator();
		validator.addChild(new ExistingBootProjectSelectionValidator(project));
		validator.addChild(new DevtoolsEnabledValidator(project));
		return new SelectProjectLaunchTabModel(project, validator);
	}

}
