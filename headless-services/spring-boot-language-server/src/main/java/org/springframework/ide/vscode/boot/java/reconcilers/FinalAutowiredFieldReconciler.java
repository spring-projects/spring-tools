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
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class FinalAutowiredFieldReconciler implements JdtAstReconciler {

	private static final String LABEL = "`@Autowired` field should not be `final`";

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_FINAL_AUTOWIRED_FIELD;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		Path sourceFile = Paths.get(docUri);
		// Check if source file belongs to non-test java sources folder
		if (IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
				.anyMatch(f -> sourceFile.startsWith(f.toPath()))) {
			final AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

			return new ASTVisitor() {

				@Override
				public boolean visit(FieldDeclaration field) {
					Annotation annotation = ReconcileUtils.findAnnotation(annotationHierarchies, field,
							Annotations.AUTOWIRED, false);
					if (annotation != null && field.getParent() instanceof TypeDeclaration) {
						if (isFinal(field)) {
							ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL,
									field.getStartPosition(), field.getLength());
							context.getProblemCollector().accept(problem);
						}
					}
					return true;
				}

			};
		} else {
			return null;
		}
	}

	private static boolean isFinal(FieldDeclaration field) {
		for (Object modifier : field.modifiers()) {
			if (modifier instanceof Modifier && ((Modifier) modifier).isFinal()) {
				return true;
			}
		}
		return false;
	}

}
