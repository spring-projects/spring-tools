/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.util;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.ARTIFACT_ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.GROUP_ID;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.SCOPE;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.TYPE;
import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.findChild;

import org.eclipse.m2e.core.ui.internal.editing.PomEdits;
import org.w3c.dom.Element;

@SuppressWarnings("restriction")
public class PomUtils {

	public static String getType(Element depEl) {
		return getTextChild(depEl, TYPE);
	}

	public static String getScope(Element depEl) {
		return getTextChild(depEl, SCOPE);
	}

	public static String getTextChild(Element depEl, String name) {
		Element child = findChild(depEl, name);
		if (child != null) {
			return PomEdits.getTextValue(child);
		}
		return null;
	}

	public static String getGroupId(Element depEl) {
		return getTextChild(depEl, GROUP_ID);
	}

	public static String getArtifactId(Element depEl) {
		return getTextChild(depEl, ARTIFACT_ID);
	}
}
