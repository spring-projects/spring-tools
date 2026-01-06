/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.preferences;

public class LanguageServerConsolePreferenceConstants {

	public static final String PREFIX_BOSH = "bosh";
	public static final String PREFIX_CF = "cloudfoundry";
	public static final String PREFIX_CONCOURSE = "concourse";
	public static final String PREFIX_SPRING_BOOT = "boot-java";

	public static final String SUFFIX_LOG_TARGET = ".log.target";
	public static final String SUFFIX_LOG_FILE = ".log.file";
	public static final String SUFFIX_LOG_LEVEL = ".log.level";


	public static final ServerInfo SPRING_BOOT_SERVER = new ServerInfo(PREFIX_SPRING_BOOT, "Spring Boot", "org.springframework.tooling.boot.ls");
	public static final ServerInfo CLOUDFOUNDRY_SERVER = new ServerInfo(PREFIX_CF, "Cloudfoundry", "org.springframework.tooling.cloudfoundry.manifest.ls");
	public static final ServerInfo CONCOURSE_SERVER = new ServerInfo(PREFIX_CONCOURSE, "Concourse", "org.springframework.tooling.concourse.ls");
	public static final ServerInfo BOSH_SERVER = new ServerInfo(PREFIX_BOSH, "Bosh", "org.springframework.tooling.bosh.ls");

	public static final ServerInfo[] ALL_SERVERS = {
			SPRING_BOOT_SERVER,
			CLOUDFOUNDRY_SERVER,
			CONCOURSE_SERVER,
			BOSH_SERVER
	};

	public record ServerInfo(String lsPrefix, String label, String bundleId) {

		public String preferenceKeyFileLog() {
			return lsPrefix + SUFFIX_LOG_FILE;
		}

		public String preferenceKeyLogLevel() {
			return lsPrefix + SUFFIX_LOG_LEVEL;
		}

		public String prefernceKeyLogTarget() {
			return lsPrefix + SUFFIX_LOG_TARGET;
		}
	}
}
