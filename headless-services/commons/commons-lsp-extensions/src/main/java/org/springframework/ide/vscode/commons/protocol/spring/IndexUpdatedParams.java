/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.util.List;

public class IndexUpdatedParams {
	
	private List<String> affectedProjects;
	
	public List<String> getAffectedProjects() {
		return affectedProjects;
	}
	
	public void setAffectedProjects(List<String> affectedProjects) {
		this.affectedProjects = affectedProjects;
	}
	
	public static IndexUpdatedParams of(List<String> affectedProjects) {
		IndexUpdatedParams params = new IndexUpdatedParams();
		params.setAffectedProjects(affectedProjects);
		return params;
	}

	public static IndexUpdatedParams of(String affectedProject) {
		IndexUpdatedParams params = new IndexUpdatedParams();
		params.setAffectedProjects(List.of(affectedProject));
		return params;
	}

}
