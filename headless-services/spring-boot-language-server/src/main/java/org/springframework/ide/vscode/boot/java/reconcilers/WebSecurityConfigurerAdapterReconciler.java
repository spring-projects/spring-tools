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

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class WebSecurityConfigurerAdapterReconciler implements JdtAstReconciler {
	
    private static final String WEB_SECURITY_CONFIGURER_ADAPTER = "WebSecurityConfigurerAdapter";
	private static final String FQN_WEB_SECURITY_CONFIGURER_ADAPTER = "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter";
	private static final String PROBLEM_LABEL = "Class extends 'WebSecurityConfigurerAdapter' which is removed in Spring-Security 6.x";
	private static final String FIX_LABEL = "Refactor class into a Configuration bean not extending 'WebSecurityConfigurerAdapter'";

	private static final String STUB_WEB_SECURITY_CONFIG_ADAPTER = """
			package org.springframework.security.config.annotation.web.configuration;
			
			import org.springframework.security.authentication.AuthenticationManager;
			import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
			import org.springframework.security.config.annotation.web.WebSecurityConfigurer;
			import org.springframework.security.config.annotation.web.builders.HttpSecurity;
			import org.springframework.security.config.annotation.web.builders.WebSecurity;
			import org.springframework.security.core.userdetails.UserDetailsService;
			
			public abstract class WebSecurityConfigurerAdapter {
			
				public void init(WebSecurity web) throws Exception {}
			
				public AuthenticationManager authenticationManagerBean() throws Exception { return null; }
				
				public UserDetailsService userDetailsServiceBean() throws Exception { return null; }
				
				protected void configure(HttpSecurity http) throws Exception {}
				
				public void configure(WebSecurity web) throws Exception {}
				
				protected void configure(AuthenticationManagerBuilder auth) throws Exception {}
			}
			""";

	private QuickfixRegistry registry;
	
	public WebSecurityConfigurerAdapterReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-security-config");
		return version != null && version.compareTo(new Version(5, 7, 0, null)) >= 0 && version.compareTo(new Version(6, 1, 0, null)) < 0;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.WEB_SECURITY_CONFIGURER_ADAPTER;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
		return new ASTVisitor() {
			
			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				Type type = typeDecl.getSuperclassType();
				if (isWebSecurityConfigurerAdapter(cu, type)) {
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, type.getStartPosition(), type.getLength());
					if (ReconcileUtils.findAnnotation(annotationHierarchies, typeDecl, Annotations.CONFIGURATION, true) != null) {
						ITypeBinding resolveBinding = type.resolveBinding();
						String[] typeStubs = resolveBinding == null || resolveBinding.isRecovered() ? new String[] { STUB_WEB_SECURITY_CONFIG_ADAPTER } : new String[0];
						String uri = docUri.toASCIIString();
						String recipeId = WebSecurityConfigurerAdapter.class.getName();
						ReconcileUtils.setRewriteFixes(registry, problem, List.of(
								new FixDescriptor(recipeId, List.of(uri),
										ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.FILE))
										.withRecipeScope(RecipeScope.FILE)
										.withTypeStubs(typeStubs),
								new FixDescriptor(recipeId, List.of(uri),
										ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.PROJECT))
										.withRecipeScope(RecipeScope.PROJECT)
										.withTypeStubs(typeStubs))

						);
					}
					context.getProblemCollector().accept(problem);
				}
				return super.visit(typeDecl);
			}
			
		};
	}
	
	private static boolean isWebSecurityConfigurerAdapter(CompilationUnit cu, Type type) {
		if (type != null && type.isSimpleType()) {
			String simpleName = ((SimpleType) type).getName().getFullyQualifiedName();
			if (FQN_WEB_SECURITY_CONFIGURER_ADAPTER.equals(simpleName)) {
				return true;
			} else if (WEB_SECURITY_CONFIGURER_ADAPTER.equals(simpleName)) {
				// check imports
				for (Object im : cu.imports()) {
					ImportDeclaration importDecl = (ImportDeclaration) im;
					if (importDecl.isOnDemand()) {
						if (FQN_WEB_SECURITY_CONFIGURER_ADAPTER.equals(importDecl.getName().getFullyQualifiedName() + "." + WEB_SECURITY_CONFIGURER_ADAPTER)) {
							return true;
						}
					} else if (FQN_WEB_SECURITY_CONFIGURER_ADAPTER.equals(importDecl.getName().getFullyQualifiedName())) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
