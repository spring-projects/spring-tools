/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.tests.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil.StringInputStream;

import junit.framework.TestCase;

/**
 * Derived from AbstractBeansCoreTestCase
 * @author Steffen Pingel
 * @author Terry Denney
 */
public abstract class StsTestCase extends TestCase {

	protected IProject createPredefinedProject(final String projectName) throws CoreException, IOException {
		return StsTestUtil.createPredefinedProject(projectName, getBundleName());
	}

	protected IResource createPredefinedProjectAndGetResource(String projectName, String resourcePath)
			throws CoreException, IOException {
		IProject project = createPredefinedProject(projectName);
		// XXX do a second full build to ensure markers are up-to-date
		project.build(IncrementalProjectBuilder.FULL_BUILD, null);

		IResource resource = project.findMember(resourcePath);
		StsTestUtil.waitForResource(resource);
		return resource;
	}

	protected abstract String getBundleName();

	protected String getSourceWorkspacePath() {
		return StsTestUtil.getSourceWorkspacePath(getBundleName());
	}

	public static <T> void assertElements(T[] actual, T... expect) {
		assertElements(Arrays.asList(actual), expect);
	}

	public static <T> void assertElements(Collection<T> actual, T... expect) {
		Set<T> expectedSet = new HashSet<T>(Arrays.asList(expect));
		StringBuilder actualStr = new StringBuilder();

		for (T propVal : actual) {
			actualStr.append(propVal+"\n");
			if (!expectedSet.remove(propVal)) {
				fail("Unexpected element: "+propVal);
			}
		}

		if (!expectedSet.isEmpty()) {
			StringBuilder missing = new StringBuilder();
			for (T propVal : expectedSet) {
				missing.append(propVal+"\n");
			}
			fail(
					"Missing elements: \n"+ missing +
					"Actual elements:\n" + actualStr
			);
		}
	}

	@Override
	protected void tearDown() throws Exception {
		StsTestUtil.cleanUpProjects();
		super.tearDown();
	}

	public static void createEmptyFile(IProject project, String path)
			throws CoreException {
				IFile file = project.getFile(new Path(path));
				file.create(new StringInputStream(""), true, new NullProgressMonitor());
			}

	public static IFile createFile(IProject project, String path, File data) throws IOException, CoreException {
		InputStream stream = new FileInputStream(data);
		try {
			return createFile(project, path, stream);
		} finally {
			stream.close();
		}
	}

	private static IFile createFile(IProject project, String path, InputStream stream) throws CoreException {
		IFile file = project.getFile(new Path(path));
		file.create(stream, true, new NullProgressMonitor());
		return file;
	}

	public static IFile createFile(IProject project, String path, String data) throws CoreException {
		IFile file = project.getFile(new Path(path));
		if (file.exists()) {
			file.setContents(new StringInputStream(data), true, true, new NullProgressMonitor());
		} else {
			file.create(new StringInputStream(data), true, new NullProgressMonitor());
		}
		return file;
	}

	public static IFile createExeFile(IProject project, String path, String data) throws CoreException {
		IFile f = createFile(project, path, data);
		ResourceAttributes a = f.getResourceAttributes();
		a.setExecutable(true);
		f.setResourceAttributes(a);
		return f;
	}

	public static void fileReplace(IProject project, String path, String find, String replace) throws Exception {
		IFile file = project.getFile(path);
		assertTrue(file.exists());
		String content = getContents(file);
		content = content.replace(find, replace);
		setContents(file, content);
	}

	public static void setContents(IFile file, String content) throws Exception {
		byte[] bytes = content.getBytes(file.getCharset());
		file.setContents(new ByteArrayInputStream(bytes), true, true, new NullProgressMonitor());
	}

	public static String getContents(IFile file) throws Exception {
		InputStream is = file.getContents();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		IOUtil.pipe(is, os);
		return os.toString(file.getCharset());
	}

	public static void assertContains(String needle, String haystack) {
		if (haystack == null || !haystack.contains(needle)) {
			fail("Not found: "+needle+"\n in \n"+haystack);
		}
	}

	public static void assertNotContains(String needle, String haystack) {
		if (haystack == null || haystack.contains(needle)) {
			fail("Found: "+needle+"\n in \n"+haystack);
		}
	}

}
