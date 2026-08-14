/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class SpringIndexAccess {

	private static final Logger logger = LoggerFactory.getLogger(SpringIndexAccess.class);
	private final ProjectLookup projects;
	private final SpringMetamodelIndex springIndex;

	public SpringIndexAccess(ProjectLookup projects, SpringMetamodelIndex springIndex) {
		this.projects = projects;
		this.springIndex = springIndex;
	}

	@Tool(description = """
			Returns indexed Spring beans for one workspace Java project, including injection points where the index exposes them.
			Use getProjectList to obtain valid project names. For a single named bean or type-scoped queries, prefer getBeanUsageInfo or findBeansByType.
			""")
	public Bean[] getBeanDetails(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {
		logger.info("get Spring project bean details for project: {}", projectName);

		IJavaProject project = projects.get(projectName);
		return springIndex.getBeansOfProject(project.getElementName());
	}

}
