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

import java.util.Collections;

import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

public class ModulithStereotypeFactoryAdapter implements StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	private final IndexBasedStereotypeFactory delegate;

	public ModulithStereotypeFactoryAdapter(IndexBasedStereotypeFactory delegate) {
		this.delegate = delegate;
	}

	@Override
	public Stereotypes fromPackage(StereotypePackageElement pkg) {
		return new Stereotypes(Collections.emptySet());
	}

	@Override
	public Stereotypes fromType(StereotypeClassElement type) {
		return delegate.fromType(type);
	}

	@Override
	public Stereotypes fromMethod(StereotypeMethodElement method) {
		return delegate.fromMethod(method);
	}
}

