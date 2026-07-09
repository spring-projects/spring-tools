/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.devtools;

import org.eclipse.equinox.security.storage.EncodingUtils;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.springframework.ide.eclipse.boot.launch.BootLaunchActivator;

/**
 * Stores the Spring Boot DevTools remote secret in Eclipse's secure storage
 * instead of as a plain launch configuration attribute, so that it is never
 * written in cleartext to a {@code .launch} XML file (which may end up in
 * the workspace metadata or, if the launch configuration is shared, in the
 * project tree and version control).
 *
 * @author Broadcom, Inc.
 */
class DevtoolsRemoteSecretStore {

	private static final String NODE = "org.springframework.ide.eclipse.boot.launch/devtools.remote.secret";
	private static final String KEY = "secret";

	private DevtoolsRemoteSecretStore() {
	}

	static String get(String id) {
		if (id == null) {
			return null;
		}
		try {
			return node(id).get(KEY, null);
		} catch (StorageException e) {
			BootLaunchActivator.getInstance().getLog().error("Failed to retrieve Remote DevTools Client secret", e);
			return null;
		}
	}

	static void put(String id, String value) {
		try {
			node(id).put(KEY, value, true);
		} catch (StorageException e) {
			BootLaunchActivator.getInstance().getLog().error("Failed to store Remote DevTools Client secret", e);
		}
	}

	static void remove(String id) {
		if (id != null) {
			node(id).removeNode();
		}
	}

	private static ISecurePreferences node(String id) {
		return SecurePreferencesFactory.getDefault().node(NODE).node(EncodingUtils.encodeSlashes(id));
	}

}
