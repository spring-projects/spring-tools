/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation.framework;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.core.MarkerUtils;

public class BootMarkerUtils {

	public static IProject getProject(IMarker marker) {
		IResource resource = marker.getResource();
		if (resource != null) {
			return resource.getProject();
		}
		return null;
	}

	/**
	 * Creates an {@link IMarker validation marker} on the specified resource for the given validation problem.
	 * <p>
	 * Adds the originating resource as marker attribute with the key {@link MarkerUtils#ORIGINATING_RESOURCE_KEY}.
	 */
	public static void createProblemMarker(String markerId, ValidationProblem problem) {

		// Use resource used during reporting of the problem as this might be
		// more concise.
		IResource resource = problem.getResource();
		if (resource != null && resource.isAccessible()) {
			try {
				// Create new marker
				IMarker marker = resource.createMarker(markerId);
				Map<String, Object> attributes = new HashMap<String, Object>();
				attributes.put(IMarker.MESSAGE, problem.getMessage());
				attributes.put(IMarker.SEVERITY, new Integer(problem.getSeverity()));
				int start = problem.getStart();
				if (start >= 0) {
					attributes.put(IMarker.CHAR_START, start);
					attributes.put(IMarker.CHAR_END, problem.getEnd());
				}
				if (problem.getErrorId() != null) {
					attributes.put(IValidationProblemMarker.ERROR_ID, problem.getErrorId());
				}
				if (problem.getRuleId() != null) {
					attributes.put(IValidationProblemMarker.RULE_ID, problem.getRuleId());
				}
				marker.setAttributes(attributes);
			} catch (Exception e) {
				Log.log(e);
			}
		}
	}
}
