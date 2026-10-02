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
 * the next level - its root packages ({@link StructureElements#rootPackages()}), or itself when the
 * tree has no package nodes. That is one root package for most projects and JARs, but as many as
 * there are top-most packages their types live in.
 *
 * @param name the label of the application node - the project's name
 * @param rootPackages the root packages of the application's types, ordered by name - none for a
 *        project without any types
 * @param packageNodes whether the tree shows a node per root package (when there is more than one)
 *        - or none at all: then the application delivers itself as the one package of the next
 *        level, and its types sit right below the application node
 *
 * @author Martin Lippert
 */
public record StructureApplication(String name, List<StereotypePackageElement> rootPackages, boolean packageNodes) {

	/**
	 * The application, with a node per root package.
	 */
	public StructureApplication(String name, List<StereotypePackageElement> rootPackages) {
		this(name, rootPackages, true);
	}

	/**
	 * The packages of the tree's next level: the root packages - or, without package nodes, the
	 * application itself, as a single package all of its types are in. A single package gets no
	 * node of its own (see {@code JMoleculesStructureView}), so with only one, its types sit right
	 * below the application node.
	 */
	public List<StereotypePackageElement> packages() {
		return packageNodes ? rootPackages : List.of(new StereotypePackageElement("", null));
	}

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
