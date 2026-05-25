/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.springframework.ide.vscode.boot.java.Boot3JavaProblemType;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class Boot3NotSupportedTypeReconciler implements JdtAstReconciler {
	
	private static final List<String> TYPE_FQNAMES = List.of(
			"org.springframework.web.multipart.commons.CommonsMultipartResolver",
			"java.lang.SecurityManager",
			"java.security.AccessControlException"
	);
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(3, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot3JavaProblemType.JAVA_TYPE_NOT_SUPPORTED;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(ImportDeclaration node) {
				String fqName = node.getName().getFullyQualifiedName();
				if (TYPE_FQNAMES.contains(fqName)) {
					context.getProblemCollector().accept(createProblem(fqName, node.getName().getStartPosition(), node.getName().getLength()));
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(SimpleType node) {
				String fqName = processType(cu, node.getName().getFullyQualifiedName());
				if (fqName != null) {
					context.getProblemCollector().accept(createProblem(fqName, node.getStartPosition(), node.getLength()));
				}
				return super.visit(node);
			}

		});
	}

	private static String processType(CompilationUnit cu, String name) {
		if (TYPE_FQNAMES.contains(name)) {
			return name;
		} else {
			for (String fqName : createFqNamesFromWildcardImports(cu, name)) {
				if (TYPE_FQNAMES.contains(fqName)) {
					return fqName;
				}
			}
		}
		return null;
	}
	
	private static List<String> createFqNamesFromWildcardImports(CompilationUnit cu, String name) {
		List<String> fqNames = new ArrayList<>();
		for (Object im : cu.imports()) {
			ImportDeclaration importDecl = (ImportDeclaration) im;
			if (importDecl.isOnDemand()) {
				fqNames.add(importDecl.getName().getFullyQualifiedName() + "." + name);
			}
		}
		return fqNames;
	}
	
	private static String createLabel(String type) {
		StringBuilder sb = new StringBuilder();
		sb.append("'");
		sb.append(type);
		sb.append("' not supported as of Spring Boot 3");
		return sb.toString();
	}

	private ReconcileProblemImpl createProblem(String type, int offset, int length) {
		return new ReconcileProblemImpl(getProblemType(), createLabel(type), offset, length);
	}

}
