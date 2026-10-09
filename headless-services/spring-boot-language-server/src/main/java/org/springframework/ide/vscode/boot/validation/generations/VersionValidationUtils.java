/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.sql.Date;

import org.springframework.ide.vscode.boot.validation.generations.json.Generation;

public class VersionValidationUtils {
	
	public static boolean isOssValid(Generation gen) {
		if (gen != null) {
			Date currentDate = new Date(System.currentTimeMillis());
			Date ossEndDate = Date.valueOf(gen.getOssSupportEndDate());
			return currentDate.before(ossEndDate);
		}
		return false;
	}

	public static boolean isCommercialValid(Generation gen) {
		if (gen != null) {
			Date currentDate = new Date(System.currentTimeMillis());
			Date commercialEndDate = Date.valueOf(gen.getCommercialSupportEndDate());
			return currentDate.before(commercialEndDate);
		}
		return false;
	}

	/**
	 * Human-readable label for a {@code Generation.latestPatch} entry key
	 * ({@code oss} or {@code enterprise}).
	 */
	public static String patchTypeLabel(String type) {
		return switch (type) {
			case "oss" -> "OSS";
			case "enterprise" -> "Enterprise";
			default -> type;
		};
	}

}
