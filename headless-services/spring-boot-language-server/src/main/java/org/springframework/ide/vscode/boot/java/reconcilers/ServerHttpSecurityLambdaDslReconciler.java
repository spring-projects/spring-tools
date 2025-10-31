/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.Arrays;
import java.util.Collection;

import org.openrewrite.java.spring.security5.ServerHttpSecurityLambdaDsl;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

public class ServerHttpSecurityLambdaDslReconciler extends AbstractSecurityLamdaDslReconciler {

	private static final Collection<String> APPLICABLE_METHOD_NAMES = Arrays.asList("anonymous", "authorizeExchange",
			"cors", "csrf", "exceptionHandling", "formLogin", "headers", "httpBasic", "logout", "oauth2Client",
			"oauth2Login", "oauth2ResourceServer", "redirectToHttps", "requestCache", "x509");

	public ServerHttpSecurityLambdaDslReconciler(QuickfixRegistry registry) {
		super(registry);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersion(project, "spring-security-config");
		return version != null && version.compareTo(new Version(5, 2, 0, null)) >= 0;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_LAMBDA_DSL;
	}

	@Override
	protected String getFixLabel() {
		return "Switch to 'ServerHttpSecurity` Lambda DSL syntax";
	}

	@Override
	protected String getRecipeId() {
		return ServerHttpSecurityLambdaDsl.class.getName();
	}

	@Override
	protected String getProblemLabel() {
		return "Consider switching to 'ServerHttpSecurity' Lambda DSL syntax";
	}

	@Override
	protected String getTargetTypeFqName() {
		return "org.springframework.security.config.web.server.ServerHttpSecurity";
	}

	@Override
	protected Collection<String> getApplicableMethodNames() {
		return APPLICABLE_METHOD_NAMES;
	}

}
