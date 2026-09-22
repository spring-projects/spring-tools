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
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;

/**
 * Asking for AOT metadata on a project whose build is neither Maven nor Gradle.
 * <p>
 * The answer has to be "no metadata" rather than an exception: the caller is the Spring Data
 * repository indexer, which reaches this service for every query method that carries no
 * {@code @Query}, and an exception there is caught per type - so the whole repository interface
 * loses its symbols.
 *
 * @author Artem
 */
public class DataRepositoryAotMetadataServiceBuildTypeTest {

	private final DataRepositoryAotMetadataService service = new DataRepositoryAotMetadataService(null, null, null);

	@Test
	void buildWithoutATypeYieldsNoMetadata() {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getProjectBuild()).thenReturn(IProjectBuild.create(null, null));

		assertEquals(Optional.empty(), service.getRepositoryMetadata(project, "com.example.UserRepository"));
	}

	@Test
	void noBuildAtAllYieldsNoMetadata() {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getProjectBuild()).thenReturn(null);

		assertEquals(Optional.empty(), service.getRepositoryMetadata(project, "com.example.UserRepository"));
	}
}
