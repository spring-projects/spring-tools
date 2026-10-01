/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.List;

import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * The first level of a project's structure tree: the application, which delivers the packages of
 * the next level - its root packages ({@link StructureElements#rootPackages()}). That is one
 * package for most projects and JARs, but as many as there are top-most packages their types live
 * in.
 *
 * @param name the label of the application node - the project's name
 * @param rootPackages the packages below the application node, ordered by name - none for a
 *        project without any types
 *
 * @author Martin Lippert
 */
public record StructureApplication(String name, List<StereotypePackageElement> rootPackages) {

	/**
	 * The root package the given type belongs to - the one it is in or below - or {@code null} if
	 * there is none: root packages are identified from the very types they are asked for, so this
	 * is only for a type that was not among them.
	 */
	public StereotypePackageElement rootPackageOf(StereotypeClassElement type) {
		for (StereotypePackageElement rootPackage : rootPackages) {
			if (StructureViewUtil.isInPackage(type.getType(), rootPackage.getPackageName())) {
				return rootPackage;
			}
		}
		return null;
	}

}
