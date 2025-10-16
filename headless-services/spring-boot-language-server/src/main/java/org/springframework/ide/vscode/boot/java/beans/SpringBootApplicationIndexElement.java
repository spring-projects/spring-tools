/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class SpringBootApplicationIndexElement extends AbstractSpringIndexElement {
	
	private final String packageName;
	private final String typeName;
	private final boolean isClassDeclaration;
	private final boolean isAnnotationDeclaration;
	
	public SpringBootApplicationIndexElement(String packageName, String typeName, boolean isClassDeclaration, boolean isAnnotationDeclaration) {
		this.packageName = packageName;
		this.typeName = typeName;
		this.isClassDeclaration = isClassDeclaration;
		this.isAnnotationDeclaration = isAnnotationDeclaration;
	}
	
	public String getPackageName() {
		return packageName;
	}
	
	public String getTypeName() {
		return typeName;
	}

	public boolean isClassDeclaration() {
		return isClassDeclaration;
	}
	
	public boolean isAnnotationDeclaration() {
		return isAnnotationDeclaration;
	}

}
