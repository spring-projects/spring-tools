/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

import com.google.gson.Gson;

/**
 * Project build info as the IDE sends it to the language server, in particular its tasks.
 *
 * @author Alex Boyko
 */
public class ProjectBuildTest {

	private final Gson gson = new Gson();

	@Test
	void tasksSurviveTheJsonRoundTrip() {
		ProjectBuild build = ProjectBuild.createGradleBuild("file:///project/build.gradle", Set.of("build", "processAot"));

		ProjectBuild received = gson.fromJson(gson.toJson(build), ProjectBuild.class);

		assertEquals(build, received);
		assertEquals(Set.of("build", "processAot"), received.tasks());
	}

	@Test
	void anEmptySetIsNotTheSameAsUnknown() {
		ProjectBuild none = gson.fromJson(gson.toJson(ProjectBuild.createGradleBuild("file:///b.gradle", Set.of())), ProjectBuild.class);
		ProjectBuild unknown = gson.fromJson(gson.toJson(ProjectBuild.createGradleBuild("file:///b.gradle")), ProjectBuild.class);

		assertEquals(Set.of(), none.tasks());
		assertNull(unknown.tasks());
	}

	@Test
	void aClientThatDoesNotKnowTasksYetStillWorks() {
		ProjectBuild received = gson.fromJson("{\"type\":\"gradle\",\"buildFile\":\"file:///project/build.gradle\"}", ProjectBuild.class);

		assertEquals(ProjectBuild.GRADLE_PROJECT_TYPE, received.type());
		assertNull(received.tasks());
	}

	@Test
	void theOrderOfTheTasksDoesNotMatterAfterTheJsonRoundTrip() {
		ProjectBuild one = gson.fromJson("{\"type\":\"gradle\",\"buildFile\":\"file:///b.gradle\",\"tasks\":[\"a\",\"b\",\"c\"]}", ProjectBuild.class);
		ProjectBuild other = gson.fromJson("{\"type\":\"gradle\",\"buildFile\":\"file:///b.gradle\",\"tasks\":[\"c\",\"a\",\"b\",\"a\"]}", ProjectBuild.class);

		assertEquals(one, other);
		assertEquals(IProjectBuild.create("gradle", URI.create("file:///b.gradle"), one.tasks()),
				IProjectBuild.create("gradle", URI.create("file:///b.gradle"), other.tasks()));
	}

	@Test
	void projectBuildsAreComparedByValueIncludingTheTasks() {
		URI file = URI.create("file:///project/build.gradle");

		assertEquals(IProjectBuild.create("gradle", file, Set.of("a")), IProjectBuild.create("gradle", file, Set.of("a")));
		assertEquals(IProjectBuild.create("gradle", file), IProjectBuild.create("gradle", file, null));
		assertNotEquals(IProjectBuild.create("gradle", file, Set.of("a")), IProjectBuild.create("gradle", file, Set.of("a", "b")));
		assertNotEquals(IProjectBuild.create("gradle", file, Set.of()), IProjectBuild.create("gradle", file));
		assertNull(IProjectBuild.create("maven", file).getTasks());
	}

}
