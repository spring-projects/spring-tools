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
package org.springframework.tooling.jdt.ls.commons.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.tooling.jdt.ls.commons.test.TestUtils.deleteAllProjects;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.tooling.jdt.ls.commons.classpath.ClasspathUtil;

/**
 * Tests for turning workspace paths into file system locations, in particular for source folders that
 * are linked resources and therefore do not live underneath the project location.
 *
 * @author Martin Lippert
 */
public class ClasspathUtilTest {

	private static final String PROJECT_NAME = "classpath-test-simple-java-project";

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	@After
	public void tearDown() throws Exception {
		deleteAllProjects();
	}

	@Test public void sourceFolderInsideTheProject() throws Exception {
		IJavaProject javaProject = createTestProject();
		IProject project = javaProject.getProject();

		IClasspathEntry entry = JavaCore.newSourceEntry(project.getFolder("src").getFullPath());
		setClasspath(javaProject, entry);

		CPE cpe = sourceCpe(javaProject, entry);
		assertSameLocation(project.getLocation().append("src"), cpe.getPath());
		assertSameLocation(project.getLocation().append("bin"), cpe.getOutputFolder());
	}

	@Test public void linkedSourceFolder() throws Exception {
		IJavaProject javaProject = createTestProject();
		IProject project = javaProject.getProject();

		File target = tmp.newFolder("sources-elsewhere");
		IFolder link = linkFolder(project, "linked-src", target);

		IClasspathEntry entry = JavaCore.newSourceEntry(link.getFullPath());
		setClasspath(javaProject, entry);

		CPE cpe = sourceCpe(javaProject, entry);
		assertSameLocation(new Path(target.getAbsolutePath()), cpe.getPath());
		assertTrue("resolved source folder must exist: " + cpe.getPath(), new File(cpe.getPath()).isDirectory());
	}

	/**
	 * The shape the Java language server's 'invisible project' has: the folder the user opened is linked into
	 * the project as a single link, and the source roots are nested underneath that link.
	 */
	@Test public void sourceFolderNestedInsideALinkedFolder() throws Exception {
		IJavaProject javaProject = createTestProject();
		IProject project = javaProject.getProject();

		File target = tmp.newFolder("workspace-elsewhere");
		File nestedSource = new File(target, "src");
		assertTrue(nestedSource.mkdirs());

		IFolder link = linkFolder(project, "_", target);

		IClasspathEntry entry = JavaCore.newSourceEntry(link.getFolder("src").getFullPath());
		setClasspath(javaProject, entry);

		CPE cpe = sourceCpe(javaProject, entry);
		assertSameLocation(new Path(nestedSource.getAbsolutePath()), cpe.getPath());
		assertTrue("resolved source folder must exist: " + cpe.getPath(), new File(cpe.getPath()).isDirectory());
	}

	/**
	 * An output folder usually does not exist on disk before the first build, so it cannot be resolved by
	 * looking the resource up in the workspace - it has to be resolved as a handle, through its linked parent.
	 */
	@Test public void outputFolderThatDoesNotExistYetInsideALinkedFolder() throws Exception {
		IJavaProject javaProject = createTestProject();
		IProject project = javaProject.getProject();

		File target = tmp.newFolder("workspace-with-unbuilt-output");
		assertTrue(new File(target, "src").mkdirs());

		IFolder link = linkFolder(project, "_", target);
		IPath outputLocation = link.getFolder("bin").getFullPath();

		IClasspathEntry entry = JavaCore.newSourceEntry(link.getFolder("src").getFullPath(), null, null, outputLocation);
		setClasspath(javaProject, entry);

		CPE cpe = sourceCpe(javaProject, entry);
		assertSameLocation(new Path(new File(target, "bin").getAbsolutePath()), cpe.getOutputFolder());
	}

	@Test public void projectItselfIsTheSourceFolder() throws Exception {
		IJavaProject javaProject = createTestProject();
		IProject project = javaProject.getProject();

		IClasspathEntry entry = JavaCore.newSourceEntry(project.getFullPath());
		setClasspath(javaProject, project.getFullPath(), entry);

		CPE cpe = sourceCpe(javaProject, entry);
		assertSameLocation(project.getLocation(), cpe.getPath());
		assertSameLocation(project.getLocation(), cpe.getOutputFolder());
	}

	///////////// harness stuff below ///////////////////////////////////////////////

	private IJavaProject createTestProject() throws Exception {
		IProject project = TestUtils.createTestProject(PROJECT_NAME, tmp);
		return JavaCore.create(project);
	}

	private static IFolder linkFolder(IProject project, String name, File target) throws Exception {
		IFolder link = project.getFolder(name);
		link.createLink(new Path(target.getAbsolutePath()), IResource.REPLACE, null);
		link.refreshLocal(IResource.DEPTH_INFINITE, null);
		return link;
	}

	private static void setClasspath(IJavaProject javaProject, IClasspathEntry... entries) throws Exception {
		setClasspath(javaProject, javaProject.getOutputLocation(), entries);
	}

	/**
	 * Replaces the source entries of the project, keeping the containers (the JRE, in particular) it came with.
	 */
	private static void setClasspath(IJavaProject javaProject, IPath outputLocation, IClasspathEntry... entries) throws Exception {
		List<IClasspathEntry> newClasspath = new ArrayList<>();
		for (IClasspathEntry existing : javaProject.getRawClasspath()) {
			if (existing.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				newClasspath.add(existing);
			}
		}
		newClasspath.addAll(Arrays.asList(entries));
		javaProject.setRawClasspath(newClasspath.toArray(new IClasspathEntry[newClasspath.size()]), outputLocation, null);
	}

	private static CPE sourceCpe(IJavaProject javaProject, IClasspathEntry entry) throws Exception {
		List<CPE> cpes = ClasspathUtil.createCpes(javaProject, entry);
		assertNotNull("no classpath entries created for " + entry.getPath(), cpes);
		assertEquals("expected exactly one CPE for " + entry.getPath(), 1, cpes.size());
		CPE cpe = cpes.get(0);
		assertTrue("expected a source entry for " + entry.getPath(), Classpath.isSource(cpe));
		return cpe;
	}

	private static void assertSameLocation(IPath expected, String actual) throws Exception {
		assertNotNull("no location resolved, expected " + expected, actual);
		assertEquals(expected.toFile().getCanonicalFile(), new File(actual).getCanonicalFile());
	}

}
