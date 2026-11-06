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
package org.springframework.ide.eclipse.boot.dash.docker.jmx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;

/**
 * Installs the JMX password and access files into a created (not yet started) container, readable
 * by the user the container runs as only. This is what keeps other users from authenticating against the
 * container's JMX port: the password is neither part of the container's command line nor of its environment.
 * <p>
 * Docker only accepts a tar stream to upload files into a container. Files get mode 0400 (the JVM refuses a
 * password file readable by others) and are owned by the container's numeric user id. Podman always hands
 * uploaded files to the container's user, for Docker the owner in the tar entry is what counts.
 *
 * @author Alex Boyko
 */
public class JmxPasswordInstaller {

	public static void install(DockerClient client, String containerId, JmxSupport jmx) throws Exception {
		InspectContainerResponse container = client.inspectContainerCmd(containerId).exec();
		String userSpec = container.getConfig() == null ? null : container.getConfig().getUser();
		int[] owner = parseOwner(userSpec);

		ByteArrayOutputStream tar = new ByteArrayOutputStream();
		try (TarArchiveOutputStream out = new TarArchiveOutputStream(tar)) {
			addFile(out, JmxSupport.PASSWORD_FILE, jmx.getPasswordFileContent(), owner);
			addFile(out, JmxSupport.ACCESS_FILE, jmx.getAccessFileContent(), owner);
		}
		client.copyArchiveToContainerCmd(containerId)
				.withTarInputStream(new ByteArrayInputStream(tar.toByteArray()))
				.withRemotePath("/")
				.exec();
	}

	private static void addFile(TarArchiveOutputStream out, String absolutePath, String content, int[] owner) throws IOException {
		byte[] data = content.getBytes(StandardCharsets.UTF_8);
		TarArchiveEntry entry = new TarArchiveEntry(absolutePath.substring(1));
		entry.setSize(data.length);
		entry.setMode(0400);
		entry.setIds(owner[0], owner[1]);
		entry.setNames("", "");
		out.putArchiveEntry(entry);
		out.write(data);
		out.closeArchiveEntry();
	}

	/**
	 * Parses the container's <code>USER</code> (<code>uid[:gid]</code>, empty or 'root' meaning root) into
	 * {uid, gid}. User and group names are not resolved: the group only matters for Docker's file ownership and
	 * the file is only accessible to its owner anyway.
	 */
	static int[] parseOwner(String userSpec) {
		if (userSpec == null || userSpec.isBlank() || userSpec.trim().equals("root")) {
			return new int[] { 0, 0 };
		}
		String[] parts = userSpec.trim().split(":", 2);
		if (!isNumber(parts[0])) {
			throw new IllegalStateException("The container runs as user '" + parts[0] + "'. Password protected JMX needs a "
					+ "numeric user id (e.g. USER 1002) to make the password file readable by that user only.");
		}
		int gid = parts.length > 1 && isNumber(parts[1]) ? Integer.parseInt(parts[1]) : 0;
		return new int[] { Integer.parseInt(parts[0]), gid };
	}

	private static boolean isNumber(String s) {
		return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
	}

}
