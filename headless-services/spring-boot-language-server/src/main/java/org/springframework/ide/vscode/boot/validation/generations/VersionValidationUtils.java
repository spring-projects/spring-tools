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
import java.util.List;

import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;

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

	public static Version getLatestSupportedRelease(ResolvedSpringProject springProject)
			throws Exception {
		List<Version> rls = springProject.getReleases();
		return rls.isEmpty() ? null : rls.get(rls.size() - 1);
	}
	
}
