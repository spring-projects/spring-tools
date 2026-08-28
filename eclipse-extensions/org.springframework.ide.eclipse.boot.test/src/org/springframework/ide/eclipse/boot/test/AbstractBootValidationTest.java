/*******************************************************************************
 * Copyright (c) 2005, 2012, 2015 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import static org.junit.Assert.fail;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.buildMavenProject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

/**
 * @author Christian Dupuis
 * @author Martin Lippert
 * @author Tomasz Zarna
 * @author Kris De Volder
 */
public abstract class AbstractBootValidationTest {

	private static final String BUNDLE_ID = "org.springframework.ide.eclipse.boot.test";
	private static boolean wasAutobuilding;

	@BeforeClass
	public static void setupClass() throws Exception {
		wasAutobuilding = StsTestUtil.isAutoBuilding();
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		StsTestUtil.setAutoBuilding(wasAutobuilding);
	}

	@Before
	public void setup() throws Exception {
		StsTestUtil.setAutoBuilding(false);
	}

	@After
	public void tearDown() throws Exception {
		StsTestUtil.deleteAllProjects();
	}
	/**
	 * Returns the IWorkspace this test suite is running on.
	 */
	public IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	public IWorkspaceRoot getWorkspaceRoot() {
		return getWorkspace().getRoot();
	}

	protected IProject createPredefinedProject(final String projectName)
			throws Exception {
		StsTestUtil.createPredefinedProject(projectName, BUNDLE_ID);
		IProject project = getProject(projectName);
		buildMavenProject(project);
		return project;
	}

	public static void assertNoMarkers(IMarker[] markers) throws Exception {
		assertNoMarkers(new LinkedHashSet<IMarker>(Arrays.asList(markers)));
	}

	public static void assertNoMarkers(Set<IMarker> markers) throws Exception {
		if (markers.size() > 0) {
			StringBuilder messages = new StringBuilder("Expected no markers but found: \n");
			for (IMarker m : markers) {
				messages.append(m.getAttribute(IMarker.MESSAGE)+"\n");
			}
			fail(messages.toString());
		}
	}

	/**
	 * @return Matcher that check whether a IMarker instance text range covers an area of
	 * its corresponding resource that contains a specific piece of text exactly.
	 */
	public static Matcher<IMarker> markerWithAreaCovering(final String expectedTextInMarkerRange) {
		return new TypeSafeMatcher<IMarker>(IMarker.class) {
			public void describeTo(Description description) {
				description.appendText("markerWithAreaCovering("+expectedTextInMarkerRange+")");
			}

			public void describeMismatchSafely(IMarker item, Description description) {
				try {
					int start = (Integer) item.getAttribute(IMarker.CHAR_START);
					int end = (Integer) item.getAttribute(IMarker.CHAR_END);
					IFile f = (IFile) item.getResource();
					String resourceContent = getContents(f);
					String actual = resourceContent.substring(start, end);
					description.appendDescriptionOf(this);
					description.appendText(" but found ");
					description.appendValue(actual);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			protected boolean matchesSafely(IMarker item) {
				try {
					int start = (Integer) item.getAttribute(IMarker.CHAR_START);
					int end = (Integer) item.getAttribute(IMarker.CHAR_END);
					IFile f = (IFile) item.getResource();
					String resourceContent = getContents(f);
					String actual = resourceContent.substring(start, end);
					return actual.equals(expectedTextInMarkerRange);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public static String getContents(IFile r) throws Exception {
		InputStream contents = r.getContents();
		ByteArrayOutputStream capture = new ByteArrayOutputStream();
		IOUtil.pipe(contents, capture);
		return capture.toString(r.getCharset());
	}

	public static Matcher<IMarker> markerWithMessageSnippet(final String expectSnippet) {
		return new TypeSafeMatcher<IMarker>(IMarker.class) {

			public void describeTo(Description description) {
				description.appendText("markerWithMessageSnippet("+expectSnippet+")");
			}

			public boolean matchesSafely(IMarker item) {
				try {
					String actualMessage = (String)item.getAttribute(IMarker.MESSAGE);
					return actualMessage.contains(expectSnippet);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	public IMarker[] getAllMarkers(IResource rsrc, String markerType) throws Exception {
		return rsrc.findMarkers(markerType, true, IResource.DEPTH_INFINITE);
	}

	protected IProject getProject(String project) {
		return getWorkspaceRoot().getProject(project);
	}

	protected IJavaProject getJavaProject(String projectName) {
		return JavaCore.create(getProject(projectName));
	}

	protected IResource createPredefinedProjectAndGetResource(
			String projectName, String resourcePath) throws Exception {
		IProject project = createPredefinedProject(projectName);
		return project.findMember(resourcePath);
	}
}
