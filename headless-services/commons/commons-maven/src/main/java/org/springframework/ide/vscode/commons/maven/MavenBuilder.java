/*******************************************************************************
 * Copyright (c) 2016-2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.maven;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.ExternalCommand;
import org.springframework.ide.vscode.commons.util.ExternalProcess;

public class MavenBuilder {


	private Path projectPath;

	private List<String> targets;

	private List<String> properties;

	public void execute() throws IOException, InterruptedException, TimeoutException {
		Path mvnwPath = System.getProperty("os.name").toLowerCase().startsWith("win") ? projectPath.resolve("mvnw.cmd")
				: projectPath.resolve("mvnw");
		Assert.isLegal(mvnwPath.toFile().isFile(), "No maven wrapper found at: "+mvnwPath);
		mvnwPath.toFile().setExecutable(true);
		List<String> all = new ArrayList<>(1 + targets.size() + properties.size());
		all.add(mvnwPath.toAbsolutePath().toString());
		all.addAll(targets);
		all.addAll(properties);
		ExternalProcess process = new ExternalProcess(projectPath.toFile(),
				new ExternalCommand(all.toArray(new String[all.size()])), true);
		if (process.getExitValue() != 0) {
			System.err.println("Failed to build test project!");
			System.err.println(process);
			throw new RuntimeException("Failed to build test project! " + process);
		}
	}

	public static MavenBuilder newBuilder(Path projectPath) {
		return new MavenBuilder(projectPath);
	}

	public MavenBuilder clean() {
		targets.add("clean");
		return this;
	}

	public MavenBuilder pack() {
		targets.add("package");
		return this;
	}

	/**
	 * Skips both compiling and running the test sources of the project being built.
	 *
	 * <p>Deliberately {@code -Dmaven.test.skip=true} rather than {@code -DskipTests}: every caller of
	 * this builder runs it directly against a test fixture project living under this module's own
	 * {@code target/test-classes} (a classpath resource, not a copy), so that fixture's test classes
	 * end up compiled into the very same directory tree that Surefire scans for tests to run. With
	 * only {@code -DskipTests} (which still compiles test sources, just skips running them), any
	 * fixture project with its own {@code *Tests.java} - and most Spring Boot demo-app fixtures have
	 * one - leaves an orphaned, compiled test class sitting there. A later (non-clean) test run then
	 * picks it up and tries to run it as if it belonged to this module, typically failing with an
	 * unrelated Spring context startup error since it has none of its own dependencies on this
	 * module's classpath.
	 */
	public MavenBuilder skipTests() {
		properties.add("-Dmaven.test.skip=true");
		return this;
	}

	public MavenBuilder javadoc() {
		properties.add("javadoc:javadoc");
		properties.add("-Dshow=private");
		return this;
	}

	private MavenBuilder(Path projectPath) {
		this.projectPath = projectPath;
		this.targets = new ArrayList<>();
		this.properties = new ArrayList<>();
	}

}
