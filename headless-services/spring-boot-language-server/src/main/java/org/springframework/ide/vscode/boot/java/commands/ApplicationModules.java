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

import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.modulith.AppModules;

public class ApplicationModules {
	
	private final AppModules modules;
	
	public ApplicationModules(AppModules modules) {
		this.modules = modules;
	}

	public Optional<String> getSystemName() {
		return Optional.empty();
	}

	public Optional<ApplicationModule> getModuleForPackage(String name) {
		return modules.getModuleForPackage(name).map(ApplicationModule::new);
	}

	public Optional<ApplicationModule> getModuleByType(StereotypeClassElement type) {
		return modules.getModuleForType(type.getType()).map(ApplicationModule::new);
	}

	public Stream<ApplicationModule> stream() {
		return modules.stream().map(ApplicationModule::new);
	}

}
