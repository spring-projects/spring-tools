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
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.common.collect.Multimap;

/**
 * @author Martin Lippert
 */
public class SpringIndexerJavaDependencyTrackerTest {

	private SpringIndexerJavaDependencyTracker tracker;
	private IJavaProject project1;
	private IJavaProject project2;

	@BeforeEach
	public void setUp() {
		tracker = new SpringIndexerJavaDependencyTracker();
		
		// Create mock projects
		project1 = mock(IJavaProject.class);
		when(project1.getElementName()).thenReturn("project1");
		
		project2 = mock(IJavaProject.class);
		when(project2.getElementName()).thenReturn("project2");
	}

	@Test
	public void testUpdateAndGetDependencies() {
		String file1 = "com/example/Foo.java";
		Set<String> dependencies1 = Set.of("com/example/Bar.java", "com/example/Baz.java");
		
		tracker.update(project1, file1, dependencies1);
		
		Collection<String> retrieved = tracker.get(project1, file1);
		assertNotNull(retrieved);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.contains("com/example/Bar.java"));
		assertTrue(retrieved.contains("com/example/Baz.java"));
	}

	@Test
	public void testUpdateReplacesExistingDependencies() {
		String file1 = "com/example/Foo.java";
		Set<String> dependencies1 = Set.of("com/example/Bar.java", "com/example/Baz.java");
		Set<String> dependencies2 = Set.of("com/example/Qux.java");
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project1, file1, dependencies2);
		
		Collection<String> retrieved = tracker.get(project1, file1);
		assertNotNull(retrieved);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.contains("com/example/Qux.java"));
	}

	@Test
	public void testGetAllDependencies() {
		String file1 = "com/example/Foo.java";
		String file2 = "com/example/Bar.java";
		Set<String> dependencies1 = Set.of("com/example/Baz.java");
		Set<String> dependencies2 = Set.of("com/example/Qux.java", "com/example/Quux.java");
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project1, file2, dependencies2);
		
		Multimap<String, String> allDeps = tracker.getAllDependencies(project1);
		assertNotNull(allDeps);
		assertEquals(2, allDeps.keySet().size());
		assertEquals(1, allDeps.get(file1).size());
		assertEquals(2, allDeps.get(file2).size());
	}

	@Test
	public void testMultipleProjectsAreIndependent() {
		String file1 = "com/example/Foo.java";
		Set<String> dependencies1 = Set.of("com/example/Bar.java");
		Set<String> dependencies2 = Set.of("com/example/Baz.java");
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project2, file1, dependencies2);
		
		Collection<String> project1Deps = tracker.get(project1, file1);
		Collection<String> project2Deps = tracker.get(project2, file1);
		
		assertEquals(1, project1Deps.size());
		assertTrue(project1Deps.contains("com/example/Bar.java"));
		
		assertEquals(1, project2Deps.size());
		assertTrue(project2Deps.contains("com/example/Baz.java"));
	}

	@Test
	public void testRemoveProject() {
		String file1 = "com/example/Foo.java";
		Set<String> dependencies1 = Set.of("com/example/Bar.java");
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project2, file1, dependencies1);
		
		tracker.removeProject(project1);
		
		// Project1 dependencies should be gone (empty)
		Collection<String> project1Deps = tracker.get(project1, file1);
		assertTrue(project1Deps.isEmpty());
		
		// Project2 dependencies should still exist
		Collection<String> project2Deps = tracker.get(project2, file1);
		assertEquals(1, project2Deps.size());
	}

	@Test
	public void testRestore() {
		String file1 = "com/example/Foo.java";
		String file2 = "com/example/Bar.java";
		Set<String> dependencies1 = Set.of("com/example/Baz.java");
		
		// Set up initial state
		tracker.update(project1, file1, dependencies1);
		Multimap<String, String> savedDeps = tracker.getAllDependencies(project1);
		
		// Clear and add different data
		tracker.removeProject(project1);
		tracker.update(project1, file2, Set.of("com/example/Different.java"));
		
		// Restore original state
		tracker.restore(project1, savedDeps);
		
		Collection<String> restored = tracker.get(project1, file1);
		assertEquals(1, restored.size());
		assertTrue(restored.contains("com/example/Baz.java"));
	}

	@Test
	public void testGetNonExistentFile() {
		Collection<String> deps = tracker.get(project1, "nonexistent.java");
		assertNotNull(deps);
		assertTrue(deps.isEmpty());
	}

	@Test
	public void testUpdateWithEmptyDependencies() {
		String file1 = "com/example/Foo.java";
		Set<String> dependencies = Set.of("com/example/Bar.java");
		
		tracker.update(project1, file1, dependencies);
		tracker.update(project1, file1, Set.of());
		
		Collection<String> retrieved = tracker.get(project1, file1);
		assertNotNull(retrieved);
		assertTrue(retrieved.isEmpty());
	}
}
