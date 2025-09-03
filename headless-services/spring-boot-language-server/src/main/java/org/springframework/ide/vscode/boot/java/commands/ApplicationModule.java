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
package org.springframework.ide.vscode.boot.java.commands;

import java.util.Collection;

import org.springframework.ide.vscode.boot.modulith.AppModule;
import org.springframework.ide.vscode.boot.modulith.NamedInterface;
import org.springframework.util.StringUtils;

public class ApplicationModule {
	
	private final AppModule appModule;

	public ApplicationModule(AppModule appModule) {
		this.appModule = appModule;
	}

	public String getDisplayName() {
		return appModule.displayName() != null ? appModule.displayName() : 
				StringUtils.capitalize(lastSegment(appModule.basePackage()));
	}
	
	public String getBasePackage() {
		return appModule.basePackage();
	}
	
	private String lastSegment(String packageName) {
		int i = packageName.lastIndexOf('.');
		if (i >= 0) {
			return packageName.substring(i);
		}
		else {
			return packageName;
		}
	}

	public Collection<NamedInterface> getNamedInterfaces() {
		return appModule.namedInterfaces();
	}

}
