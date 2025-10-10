/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

public interface JdtAstReconciler {
	
	/**
	 * This checks whether the reconciler is applicable for the given project.
	 * The implementation can take project and classpath information into account,
	 * but should not look into source or output folders.
	 * 
	 * The result is the implementation might be cached and cache entries will only
	 * be renewed if the project changes.
	 */
	boolean isApplicable(IJavaProject project);
	
	ProblemType getProblemType();

	ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context);

	default List<String> identifyFilesToReconcile(IJavaProject project, List<String> changedPropertyFiles) {
		return List.of();
	};

}
