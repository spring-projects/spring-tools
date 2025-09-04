/*******************************************************************************
 * Copyright (c) 2023, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.modulith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class AppModules {
	
	List<AppModule> modules;
	
	public AppModules(List<AppModule> modules) {
		this.modules = modules;
	}
	
	public Optional<AppModule> getModuleNotExposingType(String targetPackage, String referenceFqName) {
		return getModuleForType(referenceFqName).flatMap(refModule -> {
			if (containsInsideOfAnyNamedInterface(refModule, referenceFqName)) {
				return Optional.empty();
			} else {
				if (getModuleForPackage(targetPackage).map(targetModule -> targetModule == refModule).orElse(false)) {
					// same module for target package and reference type
					return Optional.empty();
				}
				return Optional.of(refModule);
			}
		});
	}

	private boolean containsInsideOfAnyNamedInterface(AppModule refModule, String referenceFqName) {
		Collection<NamedInterface> namedInterfaces = refModule.namedInterfaces();
		for (NamedInterface namedInterface : namedInterfaces) {
			if (namedInterface.getClasses().contains(referenceFqName)) {
				return true;
			}
		}
		
		return false;
	}
	
	public Optional<AppModule> getModuleForPackage(String pkgName) {
		return generatePackageHierarchy(pkgName)
			.stream()
			.map(p -> modules.stream().filter(m -> m.basePackage().equals(p)).findFirst())
			.filter(o -> o.isPresent())
			.map(o -> o.get())
			.findFirst();
	}
	
	public Optional<AppModule> getModuleForType(String typeFqName) {
		return getModuleForPackage(ModulithService.getPackageNameFromTypeFQName(typeFqName));
	}
	
	private List<String> generatePackageHierarchy(String pkgName) {
		List<String> packageHierarchy = new ArrayList<>();
		packageHierarchy.add(pkgName);
		for (int i = pkgName.length() - 1; i >= 0; i--) {
			if (pkgName.charAt(i) == '.') {
				packageHierarchy.add(pkgName.substring(0, i));
			}
		}
		return packageHierarchy;
	}
	
	public Stream<AppModule> stream() {
		return modules.stream();
	}

	@Override
	public int hashCode() {
		return Objects.hash(modules);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AppModules other = (AppModules) obj;
		return Objects.equals(modules, other.modules);
	}

}
