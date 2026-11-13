/*******************************************************************************
 *  Copyright (c) 2017, 2020 Pivotal Software, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli.install;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.ide.eclipse.boot.core.cli.BootCliCommand;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springframework.ide.eclipse.boot.util.version.VersionRange;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Cloud CLI extension of Spring Boot CLI
 *
 * @author Alex Boyko
 *
 */
public class CloudCliInstall implements IBootInstallExtension {

	private static final Pattern VERSION_PATTERN = Pattern.compile("^Spring Cloud CLI v(\\d+\\.\\d+\\.\\d+(?:\\.[\\w-]+)?)$");

	private IBootInstall bootInstall;

	/**
	 * Version Cloud CLI command
	 */
	public static final String VERSION_COMMAND = "--version";

	/**
	 * List Cloud Services CLI command
	 */
	public static final String LIST_SERVICES_COMMAND = "--list";

	/**
	 * Prefix for Cloud CLI command for Boot CLI
	 */
	public static final String COMMAND_PREFIX = "cloud";

	/**
	 * Range of Cloud CLI versions that support JVM parameters for cloud services
	 */
	public static final VersionRange CLOUD_CLI_JAVA_OPTS_SUPPORTING_VERSIONS = new VersionRange(new Version(1, 3, 0, null));

	/**
	 * Creates instance of Cloud CLI install based on Boot CLI install
	 * @param bootInstall
	 */
	public CloudCliInstall(IBootInstall bootInstall) {
		this.bootInstall = bootInstall;
	}

	@Override
	public BootCliCommand createCommand() throws Exception {
		return new BootCliCommand(bootInstall.getHome()) {

			@Override
			public String[] getProcessArguments(String... arguments) {
				return super.getProcessArguments(Stream.concat(Stream.of(CloudCliInstall.COMMAND_PREFIX), Arrays.stream(arguments)).toArray(String[]::new));
			}

		};
	}

	/**
	 * Returns ids/names of supported cloud services
	 * @return array of ids/names of cloud services
	 */
	public String[] getCloudServices() {
		try {
			BootCliCommand cmd = createCommand();
			if (cmd.execute(CloudCliInstall.LIST_SERVICES_COMMAND) == 0 && !isCommandOutputErroneous(cmd.getOutput())) {
				String[] outputLines = cmd.getOutput().split("\n");
				if (outputLines.length > 1) {
					Log.warn("List services command output has more than one line:\n " + cmd.getOutput());
				}
				if (outputLines[outputLines.length - 1] == null || outputLines[outputLines.length - 1].trim().isEmpty()) {
					Log.warn("Cannot find list of services in the out put\n" + cmd.getOutput());
					return new String[0];
				} else {
					return outputLines[outputLines.length - 1].split("\\s+");
				}
			}
		} catch (Throwable t) {
			Log.log(t);
		}
		return new String[0];
	}

	/**
	 * Version of Spring Cloud CLI
	 * @return the version
	 */
	@Override
	public Version getVersion() {
		try {
			BootCliCommand cmd = createCommand();
			int result = cmd.execute(CloudCliInstall.VERSION_COMMAND);
			if (result == 0 && !isCommandOutputErroneous(cmd.getOutput())) {
				Matcher matcher = VERSION_PATTERN.matcher(cmd.getOutput());
				if (matcher.find()) {
					return Version.safeParse(matcher.group(1));
				}
			}
			Log.log(ExceptionUtil.coreException("Failed to determine Spring Cloud CLI version"));
		} catch (Throwable t) {
			Log.log(t);
		}
 		return null;
	}

	private static boolean isCommandOutputErroneous(String output) {
		return output.contains("Exception");
	}

}
