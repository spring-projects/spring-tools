/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.common.collect.Streams;

public class WebApiReconcilerUtil {
	
	public static Stream<WebConfigIndexElement> getWebConfigs(SpringMetamodelIndex springIndex, IJavaProject project, ReconcilingContext context) {
		List<WebConfigIndexElement> javaWebConfigs = springIndex.getNodesOfType(project.getElementName(), WebConfigIndexElement.class);
		List<WebConfigIndexElement> propertiesWebConfigs = context.getReconcilingIndex().getWebConfigProperties(project);
		
		return Streams.concat(javaWebConfigs.stream(), propertiesWebConfigs.stream());
	}

}
