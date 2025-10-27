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
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.ide.vscode.boot.java.Boot3JavaProblemType;
import org.springframework.ide.vscode.boot.modulith.AppModules;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class ModulithTypeReferenceViolationReconciler implements JdtAstReconciler, ApplicationContextAware {
	
	private ApplicationContext appContext;
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return ModulithService.isModulithDependentProject(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot3JavaProblemType.MODULITH_TYPE_REF_VIOLATION;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.appContext = applicationContext;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		Path sourceFile = Paths.get(docUri);
		if (IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
				.anyMatch(f -> sourceFile.startsWith(f.toPath()))) {

			ModulithService modulithService = appContext.getBean(ModulithService.class); 
			AppModules appModules = modulithService.getModulesData(project);

			if (appModules != null) {
				final String packageName = cu.getPackage().getName().getFullyQualifiedName();

				return new ASTVisitor() {

					@Override
					public boolean visit(QualifiedName node) {
						validate(node, node.resolveTypeBinding());
						return super.visit(node);
					}

					@Override
					public boolean visit(SimpleType node) {
						validate(node.getName(), node.resolveBinding());
						return super.visit(node);
					}
					
					private void validate(ASTNode node, ITypeBinding type) {
						if (type != null && type.getBinaryName() != null) {
							appModules.getModuleNotExposingType(packageName, type.getBinaryName()).ifPresent(module -> {
								context.getProblemCollector().accept(new ReconcileProblemImpl(getProblemType(),
										"Invalid reference to non-exposed type of module '%s'!".formatted(module.name()),
										node.getStartPosition(), node.getLength()));
							});
						}
					}

				};
			}
		}
		
		return null;
	}

}
