/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.preferences;

import static org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ALL_SERVERS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ServerInfo;

public class LsPreferencesUtil {


	public static ServerInfo[] getInstalledLs() {
		List<ServerInfo> serverInfos = new ArrayList<>();
		for (ServerInfo info : ALL_SERVERS) {
			Bundle bundle = Platform.getBundle(info.bundleId());
			if (bundle != null) {
				serverInfos.add(info);
			}
		}
		return serverInfos.toArray(new ServerInfo[serverInfos.size()]);
	}

	public static Optional<ServerInfo> getServerInfo(String bundleId) {
		for (ServerInfo info : ALL_SERVERS) {
			if (info.bundleId().equals(bundleId)) {
				return Optional.of(info);
			}
		}
		return Optional.empty();
	}

}
