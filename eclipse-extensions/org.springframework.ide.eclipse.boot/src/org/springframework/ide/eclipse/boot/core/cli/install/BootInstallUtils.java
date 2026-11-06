/*******************************************************************************
 *  Copyright (c) 2017, 2026 Pivotal Software, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli.install;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springframework.ide.eclipse.boot.util.version.VersionParser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Utility methods for Spring Boot CLI installation
 *
 * @author Alex Boyko
 *
 */
public class BootInstallUtils {

	/**
	 * Extension type class to maven coordinates prefix (excludes version) map
	 */
	static final Map<Class<? extends IBootInstallExtension>, String> EXTENSION_TO_MAVEN_PREFIX_MAP = new HashMap<>();
	static {
		EXTENSION_TO_MAVEN_PREFIX_MAP.put(CloudCliInstall.class, "org.springframework.cloud:spring-cloud-cli:");
	}

	/**
	 * Extension type class to extension full name map
	 */
	public static final ImmutableMap<Class<? extends IBootInstallExtension>, String> EXTENSION_TO_TITLE_MAP;
	static {
		Builder<Class<? extends IBootInstallExtension>, String> builder = ImmutableMap.<Class<? extends IBootInstallExtension>, String>builder();
		builder.put(CloudCliInstall.class, "Spring Cloud CLI");
		EXTENSION_TO_TITLE_MAP = builder.build();
	}

	/**
	 * Calculates the latest compatible Spring Cloud CLI version provided the Boot CLI version
	 * @param bootVersion Spring Boot CLI version
	 * @return latest compatible version of Cloud CLI
	 */
	public static Version getCloudCliVersion(Version bootVersion) {
		if (bootVersion == null) {
			return null;
		} else if (VersionParser.DEFAULT.parseRange("2.6.0").match(bootVersion)) {
			// Latest Spring Cloud CLI (built against Boot CLI 2.6.8). Spring Boot CLI 3.0+ has no extension support
			return Version.parse("3.1.1");
		} else if (VersionParser.DEFAULT.parseRange("[2.3.0,2.6.0)").match(bootVersion)) {
			return Version.parse("2.2.4.RELEASE");
		} else if (VersionParser.DEFAULT.parseRange("[2.2.0,2.3.0)").match(bootVersion)) {
			return Version.parse("2.2.1.RELEASE");
		} else if (VersionParser.DEFAULT.parseRange("[2.1.0,2.2.0)").match(bootVersion)) {
			return Version.parse("2.1.0.RELEASE");
		} else if (VersionParser.DEFAULT.parseRange("[2.0.0,2.1.0)").match(bootVersion)) {
			return Version.parse("2.0.0.RELEASE");
		} else if (VersionParser.DEFAULT.parseRange("[1.5.3,2.0.0)").match(bootVersion)) {
			return Version.parse("1.4.0.RELEASE");
		} else if (VersionParser.DEFAULT.parseRange("[1.4.4,1.5.3)").match(bootVersion)) {
			return Version.parse("1.3.2.RELEASE");
		} else if (VersionParser.DEFAULT.parseRange("[1.2.2,1.4.4)").match(bootVersion)) {
			return Version.parse("1.2.2.RELEASE");
		} else {
			return null;
		}
	}

}
