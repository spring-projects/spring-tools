/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.util;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Kris De Volder
 *
 * @since 2.8
 */
public class OsUtils {

	public static boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}

	private static final PosixFilePermission[] decodeMap = {
			//This is exactly the reverse order as in the enum.
			//But it seems safer not to rely on that.
			OTHERS_EXECUTE,
			OTHERS_WRITE,
			OTHERS_READ,
			GROUP_EXECUTE,
			GROUP_WRITE,
			GROUP_READ,
			OWNER_EXECUTE,
			OWNER_WRITE,
			OWNER_READ
	};

	public static Set<PosixFilePermission> posixFilePermissions(int mode) {
		int mask = 1;
		Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
		for (PosixFilePermission flag : decodeMap) {
			if (flag != null && (mask & mode) != 0) {
				perms.add(flag);
			}
			mask = mask << 1;
		}
		return perms;
	}
}
