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
package org.springframework.ide.vscode.boot.app;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.common.ProjectReconcileScheduler;
import org.springframework.ide.vscode.boot.java.rewrite.SpringBootUpgrade;
import org.springframework.ide.vscode.boot.validation.BootVersionValidationEngine;
import org.springframework.ide.vscode.boot.validation.generations.GenerationsValidator;
import org.springframework.ide.vscode.boot.validation.generations.ProjectVersionDiagnosticProvider;
import org.springframework.ide.vscode.boot.validation.generations.SpringCloudCompatibilityValidator;
import org.springframework.ide.vscode.boot.validation.generations.SpringIoProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.UpdateBootVersion;
import org.springframework.ide.vscode.boot.validation.generations.VersionValidator;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

@Configuration(proxyBeanMethods = false)
public class BootVersionValidationConfig {
	
	private static final Logger log = LoggerFactory.getLogger(BootVersionValidationConfig.class);
	
	@Bean UpdateBootVersion updateBootVersion(SimpleLanguageServer server, Optional<SpringBootUpgrade> bootUpgradeOpt, SpringProjectsProvider projectsProvider) {
		return new UpdateBootVersion(server.getDiagnosticSeverityProvider(), bootUpgradeOpt, projectsProvider);
	}
	
	@Bean SpringIoProjectsProvider springProjectsProvider(SimpleLanguageServer server, BootJavaConfig config, RestTemplateFactory restTemplateFactory) {
		return new SpringIoProjectsProvider(config, restTemplateFactory, server.getProgressService(), server.getMessageService(), 30_000);
	}
	
	@Bean GenerationsValidator generationsValidator(SimpleLanguageServer server, SpringProjectsProvider projectsProvider) {
		return new GenerationsValidator(server.getDiagnosticSeverityProvider(), projectsProvider);
	}
	
	@Bean SpringCloudCompatibilityValidator springCloudCompatibilityValidator(SimpleLanguageServer server, SpringProjectsProvider projectsProvider) {
		return new SpringCloudCompatibilityValidator(server.getDiagnosticSeverityProvider(), projectsProvider);
	}
	
	@Bean ProjectVersionDiagnosticProvider projectVersionDiagnosticProvider(List<VersionValidator> validators) {
		return new ProjectVersionDiagnosticProvider(validators);
	}
	
	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@ConditionalOnProperty(prefix = "languageserver", name = "reconcile-only-opened-docs", havingValue = "false", matchIfMissing = true)
	@Bean
	ProjectReconcileScheduler bootVersionValidationScheduler(SimpleLanguageServer server,
			JavaProjectFinder projectFinder, BootJavaConfig config, ProjectObserver projectObserver,
			ProjectVersionDiagnosticProvider diagnosticProvider) {
		return new ProjectReconcileScheduler(server,
				new BootVersionValidationEngine(server, config, projectObserver, projectFinder, diagnosticProvider),
				projectFinder) {

			@Override
			protected void init() {
				super.init();
				config.addListener(evt -> scheduleValidationForAllProjects());
				projectObserver.addListener(new ProjectObserver.Listener() {

					@Override
					public void deleted(IJavaProject project) {
						unscheduleValidation(project);
						clear(project, true);
					}

					@Override
					public void created(IJavaProject project) {
						scheduleValidation(project);
					}

					@Override
					public void changed(IJavaProject project) {
						scheduleValidation(project);
					}
				});
				log.info("Started Boot Version reconciler");
			}

		};
	}
	

}
