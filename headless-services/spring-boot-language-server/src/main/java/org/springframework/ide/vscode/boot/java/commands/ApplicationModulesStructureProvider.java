/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
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

import org.jmolecules.stereotype.tooling.StructureProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

public abstract class ApplicationModulesStructureProvider
		implements StructureProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	protected final StructureElements elements;

	public ApplicationModulesStructureProvider(StructureElements elements) {
		this.elements = elements;
	}

	@Override
	public Collection<StereotypePackageElement> extractPackages(ApplicationModules application) {

		return application.stream()
				.map(ApplicationModule::getBasePackage)
				.map(elements::packageNode)
				.toList();
	}

	@Override
	public Collection<StereotypeMethodElement> extractMethods(StereotypeClassElement type) {
		return type.getMethods();
	}

	static class SimpleApplicationModulesStructureProvider extends ApplicationModulesStructureProvider implements SimpleStructureProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

		SimpleApplicationModulesStructureProvider(StructureElements elements) {
			super(elements);
		}

		@Override
		public Collection<StereotypeClassElement> extractTypes(StereotypePackageElement pkg) {

			return elements.types().stream()
					.filter(element -> element.getType().startsWith(pkg.getPackageName()))
					.toList();
		}
	}
}
