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

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;

public class SpringBootApplicationIndexer {
	
	private static boolean isSpringBootApplicationType(AbstractTypeDeclaration typeDeclaration, ITypeBinding typeBinding) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);
		return annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.BOOT_APP);
	}

	public static void createIndexElement(AbstractTypeDeclaration type, ITypeBinding typeBinding, SpringIndexerJavaContext context) {
		if (isSpringBootApplicationType(type, typeBinding)) {
			ITypeBinding binding = type.resolveBinding();
			String packageName = binding.getPackage().getName();
			String typeName = binding.getName();
			
			boolean classDef = type instanceof TypeDeclaration;
			boolean annotationDef = type instanceof AnnotationTypeDeclaration;
	
			SpringBootApplicationIndexElement mainIndexElement = new SpringBootApplicationIndexElement(packageName, typeName, classDef, annotationDef);
			context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), mainIndexElement));
		}
	}

}
