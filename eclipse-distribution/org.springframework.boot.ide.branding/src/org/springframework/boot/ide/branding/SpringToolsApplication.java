/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.boot.ide.branding;

import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.ui.internal.ide.application.IDEApplication;

@SuppressWarnings("restriction")
public class SpringToolsApplication extends IDEApplication {
	
	@Override
	public Object start(IApplicationContext appContext) throws Exception {
		MacOs26Dot1TextSelectionWorkaround.installIfNecessary();
		return super.start(appContext);
	}

}
