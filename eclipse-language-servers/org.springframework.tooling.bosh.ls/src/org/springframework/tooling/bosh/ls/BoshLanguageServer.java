/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.bosh.ls;

import static org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.BOSH_SERVER;

import java.nio.file.Paths;
import java.util.Arrays;

import org.springframework.tooling.ls.eclipse.commons.STS4LanguageServerProcessStreamConnector;

/**
 * @author Martin Lippert
 */
public class BoshLanguageServer extends STS4LanguageServerProcessStreamConnector {

	public BoshLanguageServer() {
		super(BOSH_SERVER);
		
		initExecutableJarCommand(
				Paths.get("servers", "bosh-language-server"),
				"bosh-language-server",
				Arrays.asList(
						"-Dlsp.lazy.completions.disable=true",
						"-Dspring.config.location=classpath:/application.properties"
				)
		);
		
		setWorkingDirectory(getWorkingDirLocation());
	}
	
	@Override
	protected String getPluginId() {
		return Constants.PLUGIN_ID;
	}

}
