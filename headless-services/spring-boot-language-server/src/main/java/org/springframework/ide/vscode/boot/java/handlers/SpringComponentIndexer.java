/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;

public interface SpringComponentIndexer {
	
	default void index(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context) {};
	default void index(RecordDeclaration recordDeclaration, SpringIndexerJavaContext context) {};
	default void index(AnnotationTypeDeclaration annotationTypeDeclaration, SpringIndexerJavaContext context) {};
	default void index(MethodDeclaration methodDeclaration, SpringIndexerJavaContext context) {};
	default void index(PackageDeclaration packageDeclaration, SpringIndexerJavaContext context) {};

}
