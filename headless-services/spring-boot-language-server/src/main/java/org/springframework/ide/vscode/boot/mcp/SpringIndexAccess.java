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
package org.springframework.ide.vscode.boot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class SpringIndexAccess {

	private static final Logger logger = LoggerFactory.getLogger(SpringIndexAccess.class);
	private SpringMetamodelIndex springIndex;

	public SpringIndexAccess(SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
	}

	@Tool(description = "Get detailed information about the spring beans and their dependencies via injection points of the current projects in the workspace")
	public Bean[] getBeanDetails() {
		logger.info("get Spring project bean details");
		
		return springIndex.getBeans();
	}

}
