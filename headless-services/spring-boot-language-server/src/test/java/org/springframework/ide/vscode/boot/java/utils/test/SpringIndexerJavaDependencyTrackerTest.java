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
import org.springframework.ide.vscode.boot.java.utils.QualifiedTypeName;
import org.springframework.ide.vscode.boot.java.utils.SourceJavaFile;
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

	private static SourceJavaFile f(String path) {
		return SourceJavaFile.of(path);
	}

	private static QualifiedTypeName t(String name) {
		return QualifiedTypeName.of(name);
	}

	@BeforeEach
	public void setUp() {
		tracker = new SpringIndexerJavaDependencyTracker();
		
		project1 = mock(IJavaProject.class);
		when(project1.getElementName()).thenReturn("project1");
		
		project2 = mock(IJavaProject.class);
		when(project2.getElementName()).thenReturn("project2");
	}

	@Test
	public void testUpdateAndGetDependencies() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		Set<QualifiedTypeName> dependencies1 = Set.of(t("com.example.Bar"), t("com.example.Baz"));
		
		tracker.update(project1, file1, dependencies1);
		
		Collection<QualifiedTypeName> retrieved = tracker.getDependenciesForFile(project1, file1.absolutePath());
		assertNotNull(retrieved);
		assertEquals(2, retrieved.size());
		assertTrue(retrieved.contains(t("com.example.Bar")));
		assertTrue(retrieved.contains(t("com.example.Baz")));
	}

	@Test
	public void testUpdateReplacesExistingDependencies() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		Set<QualifiedTypeName> dependencies1 = Set.of(t("com.example.Bar"), t("com.example.Baz"));
		Set<QualifiedTypeName> dependencies2 = Set.of(t("com.example.Qux"));
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project1, file1, dependencies2);
		
		Collection<QualifiedTypeName> retrieved = tracker.getDependenciesForFile(project1, file1.absolutePath());
		assertNotNull(retrieved);
		assertEquals(1, retrieved.size());
		assertTrue(retrieved.contains(t("com.example.Qux")));
	}

	@Test
	public void testGetAllDependencies() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		SourceJavaFile file2 = f("com/example/Bar.java");
		Set<QualifiedTypeName> dependencies1 = Set.of(t("com.example.Baz"));
		Set<QualifiedTypeName> dependencies2 = Set.of(t("com.example.Qux"), t("com.example.Quux"));
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project1, file2, dependencies2);
		
		Multimap<SourceJavaFile, QualifiedTypeName> allDeps = tracker.getAllDependencies(project1);
		assertNotNull(allDeps);
		assertEquals(2, allDeps.keySet().size());
		assertEquals(1, allDeps.get(file1).size());
		assertEquals(2, allDeps.get(file2).size());
	}

	@Test
	public void testMultipleProjectsAreIndependent() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		Set<QualifiedTypeName> dependencies1 = Set.of(t("com.example.Bar"));
		Set<QualifiedTypeName> dependencies2 = Set.of(t("com.example.Baz"));
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project2, file1, dependencies2);
		
		Collection<QualifiedTypeName> project1Deps = tracker.getDependenciesForFile(project1, file1.absolutePath());
		Collection<QualifiedTypeName> project2Deps = tracker.getDependenciesForFile(project2, file1.absolutePath());
		
		assertEquals(1, project1Deps.size());
		assertTrue(project1Deps.contains(t("com.example.Bar")));
		
		assertEquals(1, project2Deps.size());
		assertTrue(project2Deps.contains(t("com.example.Baz")));
	}

	@Test
	public void testRemoveProject() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		Set<QualifiedTypeName> dependencies1 = Set.of(t("com.example.Bar"));
		
		tracker.update(project1, file1, dependencies1);
		tracker.update(project2, file1, dependencies1);
		
		tracker.removeProject(project1);
		
		Collection<QualifiedTypeName> project1Deps = tracker.getDependenciesForFile(project1, file1.absolutePath());
		assertTrue(project1Deps.isEmpty());
		
		Collection<QualifiedTypeName> project2Deps = tracker.getDependenciesForFile(project2, file1.absolutePath());
		assertEquals(1, project2Deps.size());
	}

	@Test
	public void testRestore() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		SourceJavaFile file2 = f("com/example/Bar.java");
		Set<QualifiedTypeName> dependencies1 = Set.of(t("com.example.Baz"));
		
		tracker.update(project1, file1, dependencies1);
		Multimap<SourceJavaFile, QualifiedTypeName> savedDeps = tracker.getAllDependencies(project1);
		
		tracker.removeProject(project1);
		tracker.update(project1, file2, Set.of(t("com.example.Different")));
		
		tracker.restore(project1, savedDeps);
		
		Collection<QualifiedTypeName> restored = tracker.getDependenciesForFile(project1, file1.absolutePath());
		assertEquals(1, restored.size());
		assertTrue(restored.contains(t("com.example.Baz")));
	}

	@Test
	public void testGetNonExistentFile() {
		Collection<QualifiedTypeName> deps = tracker.getDependenciesForFile(project1, "nonexistent.java");
		assertNotNull(deps);
		assertTrue(deps.isEmpty());
	}

	@Test
	public void testUpdateWithEmptyDependencies() {
		SourceJavaFile file1 = f("com/example/Foo.java");
		Set<QualifiedTypeName> dependencies = Set.of(t("com.example.Bar"));
		
		tracker.update(project1, file1, dependencies);
		tracker.update(project1, file1, Set.of());
		
		Collection<QualifiedTypeName> retrieved = tracker.getDependenciesForFile(project1, file1.absolutePath());
		assertNotNull(retrieved);
		assertTrue(retrieved.isEmpty());
	}

	@Test
	public void testRemoveFilesClearsEntriesForThosePaths() {
		String fileA = "/proj/src/Foo.java";
		String fileB = "/proj/src/Bar.java";
		tracker.update(project1, f(fileA), Set.of(t("com.example.Dep")));
		tracker.update(project1, f(fileB), Set.of(t("com.example.Other")));

		tracker.removeFiles(project1, new String[] { fileA });

		assertTrue(tracker.getDependenciesForFile(project1, fileA).isEmpty());
		assertEquals(1, tracker.getDependenciesForFile(project1, fileB).size());
		assertTrue(tracker.getDependenciesForFile(project1, fileB).contains(t("com.example.Other")));
	}

	@Test
	public void testRemoveFilesNullOrEmptyIsNoOp() {
		tracker.update(project1, f("/proj/Foo.java"), Set.of(t("com.example.X")));

		tracker.removeFiles(project1, null);
		tracker.removeFiles(project1, new String[0]);

		assertEquals(1, tracker.getAllDependencies(project1).keySet().size());
	}

	@Test
	public void testRemoveFilesUnknownProjectIsNoOp() {
		IJavaProject other = mock(IJavaProject.class);
		when(other.getElementName()).thenReturn("other");

		tracker.update(project1, f("/p/Foo.java"), Set.of(t("com.example.X")));
		tracker.removeFiles(other, new String[] { "/p/Foo.java" });

		assertEquals(1, tracker.getDependenciesForFile(project1, "/p/Foo.java").size());
	}

	@Test
	public void testAddDependenciesAccumulatesWithExisting() {
		SourceJavaFile file = f("com/example/Foo.java");
		tracker.update(project1, file, Set.of(t("com.example.Existing")));
		tracker.addDependencies(project1, file, Set.of(t("com.example.Added")));

		assertEquals(Set.of(t("com.example.Existing"), t("com.example.Added")), tracker.getDependenciesForFile(project1, file.absolutePath()));
	}

	@Test
	public void testRemoveFilesMultiplePaths() {
		tracker.update(project1, f("/a.java"), Set.of(t("t.A")));
		tracker.update(project1, f("/b.java"), Set.of(t("t.B")));
		tracker.update(project1, f("/c.java"), Set.of(t("t.C")));

		tracker.removeFiles(project1, new String[] { "/a.java", "/c.java" });

		assertTrue(tracker.getDependenciesForFile(project1, "/a.java").isEmpty());
		assertEquals(1, tracker.getDependenciesForFile(project1, "/b.java").size());
		assertTrue(tracker.getDependenciesForFile(project1, "/c.java").isEmpty());
	}
}
