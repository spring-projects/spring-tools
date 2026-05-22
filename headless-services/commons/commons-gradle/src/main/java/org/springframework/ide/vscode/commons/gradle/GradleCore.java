/*******************************************************************************
 * Copyright (c) 2017, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.gradle;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.Assert;

/**
 * Gradle API tooling utility
 * 
 * @author Alex Boyko
 *
 */
public class GradleCore {
	
	private static final Logger log = LoggerFactory.getLogger(GradleCore.class);
	
	@FunctionalInterface
	public interface GradleConfiguration {
		void configure(GradleConnector connector);
	}
	
	public interface GradleCoreProject {
		EclipseProject getProject();
		BuildEnvironment getBuildEnvironment();
	}
	
	public static final String GRADLE_BUILD_FILE = "build.gradle";
	public static final String GRADLE_KTS_BUILD_FILE = "build.gradle.kts";

	static final String GLOB_GRADLE_FILE = "**/*.gradle";
	
	private static GradleCore defaultInstance = null;
	
	public static GradleCore getDefault() {
		if (defaultInstance == null) {
			defaultInstance = new GradleCore();
		}
		return defaultInstance;
	}
	
	private GradleConfiguration configuration;
	
	public GradleCore() {
		this.configuration = (connector) -> {};
	}
	
	public GradleCore(GradleConfiguration configuration) {
		Assert.isNotNull(configuration);
		this.configuration = configuration;
	}
	
	public <T> T getModel(File projectDir, Class<T> modelType) throws GradleException {
		ProjectConnection connection = null;
		try {
			GradleConnector gradleConnector = GradleConnector.newConnector().forProjectDirectory(projectDir);
			/*
			 * Shut down Gradle daemons right away. Necessary project data is
			 * queried once and then cached, hence no need need to have the
			 * daemon running
			 */
			((DefaultGradleConnector) gradleConnector).daemonMaxIdleTime(1, TimeUnit.SECONDS);
			configuration.configure(gradleConnector);
			if (Files.exists(projectDir.toPath().resolve("gradle/wrapper/gradle-wrapper.properties"))) {
				gradleConnector.useBuildDistribution();
			} else {
				gradleConnector.useGradleVersion("8.5");
			}
			connection = gradleConnector.connect();
			return connection.getModel(modelType);
		} catch (GradleConnectionException e) {
			log.error("", e);
			throw new GradleException(e);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}
	
}
