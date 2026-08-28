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

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.util.FileUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.junit.Assert;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * @author Steffen Pingel
 * @author Leo Dos Santos
 * @author Terry Denney
 * @author Kris De Volder
 */
public class StsTestUtil {

	public static final boolean ECLIPSE_3_4 = Platform.getBundle("org.eclipse.equinox.p2.repository") == null;

	public static final boolean ECLIPSE_3_6_OR_LATER;

	public static final boolean ECLIPSE_3_7_OR_LATER;
	static {
		ECLIPSE_3_6_OR_LATER = isEclipseVersionAtLeast(new Version(3, 6, 0));
		ECLIPSE_3_7_OR_LATER = isEclipseVersionAtLeast(new Version(3, 7, 0));
	}

	public static boolean isEclipseVersionAtLeast(Version minimalVersion) {
		//System.err.println("StsTestUtil: " + minimalVersion + " or later? ...");
		boolean found = false;
		try {
			Bundle platformBundle = Platform.getBundle("org.eclipse.core.runtime");
			//System.err.println("org.eclipse.core.runtime bundle: " + platformBundle);
			Version version = platformBundle.getVersion();
			//System.err.println("org.eclipse.core.runtime bundle version: " + version);
			if (version.compareTo(minimalVersion) >= 0) {
				found = true;
			}
		}
		catch (Throwable e) {
			//System.err.println("StsTestUtil: Couldn't determine Eclipse version");
			e.printStackTrace(System.err);
		}
		//System.err.println("StsTestUtil: " + minimalVersion + " or later? => " + found);
		return found;
	}

	public static final long WAIT_TIME = 2000;

	public static String canocalizeXml(String originalServerXml) throws Exception {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilderFactory.setExpandEntityReferences(false);

		DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
		Document document = builder.parse(new InputSource(new StringReader(originalServerXml)));
		document.normalize();

		TransformerFactory factory = TransformerFactory.newInstance();
		Transformer transformer = factory.newTransformer();
		StringWriter writer = new StringWriter();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.transform(new DOMSource(document.getDocumentElement()), new StreamResult(writer));
		return writer.toString().replace("\\s+\\n", "\\n");
	}

	public static void cleanUpProjects() throws Exception {
		closeAllEditors();
		deleteAllProjects();
	}

	public static void closeAllEditors() {
		IWorkbenchWindow window = null;
		try {
			window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		} catch (Exception e) {
			//ignore: happens if workbinch not created yet.
			// in that case there's no editors to close either so
			// this is okay.
		}
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				page.closeAllEditors(false);
			}
		}
	}

	/**
	 * Copy file from src (path to the original file) to dest (path to the
	 * destination file).
	 */
	private static void copy(File src, File dest) throws IOException {
		InputStream in = new FileInputStream(src);
		try {
			OutputStream out = new FileOutputStream(dest);
			try {
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			}
			finally {
				out.close();
			}
		}
		finally {
			in.close();
		}
	}

	/**
	 * Copy the given source directory (and all its contents) to the given
	 * target directory.
	 */
	public static void copyDirectory(File source, File target) throws IOException {
		if (!target.exists()) {
			target.mkdirs();
		}
		File[] files = source.listFiles();
		if (files == null) {
			return;
		}
		for (File sourceChild : files) {
			String name = sourceChild.getName();
			if (name.equals(".svn")) {
				continue;
			}
			File targetChild = new File(target, name);
			if (sourceChild.isDirectory()) {
				copyDirectory(sourceChild, targetChild);
			}
			else {
				copy(sourceChild, targetChild);
			}
		}
	}

	public static IProject createPredefinedProject(final String projectName, String bundleName) throws CoreException,
			IOException {
		IJavaProject jp = setUpJavaProject(projectName, bundleName);
		StsTestUtil.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
		return jp.getProject();
	}


	public static File createTempDirectory() throws IOException {
		return createTempDirectory("sts", null);
	}

	public static File createTempDirectory(String prefix, String suffix) throws IOException {
		return FileUtil.createTempDirectory(prefix, suffix);
	}

	public static void deleteAllProjects() throws Exception {
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : allProjects) {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
			project.close(null);
			deleteProject(project);
		}
		getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
	}

	public static void deleteAllProjectsExcept(String... names) throws Exception {
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		List<String> namesList = Arrays.asList(names);
		for (IProject project : allProjects) {
			if (!namesList.contains(project.getName())) {
				project.refreshLocal(IResource.DEPTH_INFINITE, null);
				try {
					project.close(new NullProgressMonitor());
				} catch (Exception e) {

				}
				deleteProject(project);
			}
		}
		getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
	}

	private static void deleteProject(IProject project) throws Exception {
		int retryCount = 10; // wait 1 minute at most
		Exception lastException = null;
		while (project.exists() && --retryCount >= 0) {
			waitForManualBuild();
			waitForAutoBuild();
			try {
				project.delete(true, true, new NullProgressMonitor());
				lastException = null;
			} catch (Exception e) {
				lastException = e;
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e1) {
				}
			}
		}
		if (lastException != null) {
			throw lastException;
		}
	}

	/**
	 * Delete this resource.
	 */
	private static void deleteResource(IResource resource, boolean force) throws CoreException {
		if (!resource.exists()/* || !resource.isAccessible() */) {
			return;
		}
		waitForManualBuild();
		waitForAutoBuild();
		CoreException lastException = null;
		try {
			resource.delete(force, null);
		}
		catch (CoreException e) {
			lastException = e;
			// just print for info
			System.out.println("(CoreException): " + e.getMessage() + " Resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$
			e.printStackTrace();
		}
		catch (IllegalArgumentException iae) {
			// just print for info
			System.out
					.println("(IllegalArgumentException): " + iae.getMessage() + ", resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (!force) {
			return;
		}
		int retryCount = 10; // wait 1 minute at most
		while (resource.isAccessible() && --retryCount >= 0) {
			waitForAutoBuild();
			try {
				Thread.sleep(1000);
			}
			catch (InterruptedException e) {
			}
			try {
				resource.delete(true, null);
			}
			catch (CoreException e) {
				lastException = e;
				// just print for info
				System.out
						.println("(CoreException) Retry " + retryCount + ": " + e.getMessage() + ", resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			catch (IllegalArgumentException iae) {
				// just print for info
				System.out
						.println("(IllegalArgumentException) Retry " + retryCount + ": " + iae.getMessage() + ", resource " + resource.getFullPath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
		if (!resource.isAccessible()) {
			return;
		}
		System.err.println("Failed to delete " + resource.getFullPath()); //$NON-NLS-1$
		if (lastException != null) {
			throw lastException;
		}
	}

	public static File getBundlePath(String pluginId) throws IOException {
		URL platformURL = Platform.getBundle(pluginId).getEntry("/"); //$NON-NLS-1$
		return new File(FileLocator.toFileURL(platformURL).getFile());
	}

	public static File getFilePath(String pluginId, String segment) throws IOException {
		URL platformURL = Platform.getBundle(pluginId).getEntry(segment);
		Assert.assertNotNull("Couldn't find file '" + segment + "' in bundle '" + pluginId + "'", platformURL);
		return new File(FileLocator.toFileURL(platformURL).getFile());
	}

	public static String getMarkerMessages(IMarker[] markers) throws CoreException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < markers.length; i++) {
			IMarker currMarker = markers[i];
			String message = (String) currMarker.getAttribute("message");
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(message);
		}
		return sb.toString();
	}

	/**
	 * Returns the OS path to the directory that contains this plugin.
	 */
	private static String getPluginDirectoryPath(String bundleName) {
		try {
			URL platformURL = Platform.getBundle(bundleName).getEntry("/"); //$NON-NLS-1$
			return new File(FileLocator.toFileURL(platformURL).getFile()).getAbsolutePath();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static IProject getProject(String project) {
		return getWorkspaceRoot().getProject(project);
	}

	/**
	 * Get an IResource indicated by a given path starting at the workspace
	 * root.
	 * <p>
	 * Different type of resource is returned based on the length of the path
	 * and whether or not it ends with a path separator.
	 */
	public static IResource getResource(IPath path) {
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			if (path.segmentCount() == 0) {
				return root;
			}
			IProject project = root.getProject(path.segment(0));
			if (path.segmentCount() == 1) {
				return project;
			}
			if (path.hasTrailingSeparator()) {
				return root.getFolder(path);
			}
			else {
				return root.getFile(path);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Get an IResource from a path String starting at the workspace root.
	 * <p>
	 * Different type of resource is returned based on the length of the path
	 * and whether or not it ends with a path separator.
	 * <p>
	 * For example
	 *
	 * "" length = 0 => type of resource is IWorkspaceRoot "foo" length = 1 =>
	 * type of resource is IProject "foo/src/Foo.java" length > 1 and no
	 * trailing "/" => type is IFile
	 * "foo/src/          length > 1 and a trailing "/" => type is IFolder
	 */
	public static IResource getResource(String pathToFile) {
		return getResource(Path.ROOT.append(pathToFile));
	}

	public static String getSourceWorkspacePath(String bundleName) {
		return getPluginDirectoryPath(bundleName) + java.io.File.separator + "workspace"; //$NON-NLS-1$
	}

	/**
	 * Returns the IWorkspace this test suite is running on.
	 */
	public static IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	public static IWorkspaceRoot getWorkspaceRoot() {
		return getWorkspace().getRoot();
	}

	public static void saveAndWaitForEditor(final IEditorPart editor) throws CoreException {
		Display.getDefault().syncExec(new Runnable() {

			public void run() {
				editor.doSave(null);
			}
		});
		waitForEditor(editor);
	}

	private static IJavaProject setUpJavaProject(final String projectName, String bundleName) throws CoreException,
			IOException {
		return StsTestUtil.setUpJavaProject(projectName, "1.4", getSourceWorkspacePath(bundleName)); //$NON-NLS-1$
	}

	public static IJavaProject setUpJavaProject(final String projectName, String compliance, String sourceWorkspacePath)
			throws CoreException, IOException {
		IProject project = setUpProject(projectName, compliance, sourceWorkspacePath);
		IJavaProject javaProject = JavaCore.create(project);
		return javaProject;
	}

	public static IProject setUpProject(final String projectName, String compliance, String sourceWorkspacePath)
			throws CoreException, IOException {
		// copy files in project from source workspace to target workspace
		String targetWorkspacePath = getWorkspaceRoot().getLocation().toFile().getCanonicalPath();
		File sourceProjectPath = new File(sourceWorkspacePath, projectName);
		assertTrue("Doesn't exist: "+sourceProjectPath, sourceProjectPath.exists());
		copyDirectory(sourceProjectPath, new File(targetWorkspacePath, projectName));

		// create project
		final IProject project = getWorkspaceRoot().getProject(projectName);
		IWorkspaceRunnable populate = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				project.create(null);
				try {
					project.open(null);
				}
				catch (ConcurrentModificationException e) {
					// wait and try again to work-around
					// ConcurrentModificationException (bug 280488)
					try {
						Thread.sleep(500);
						project.open(null);
						project.refreshLocal(IResource.DEPTH_INFINITE, null);
					}
					catch (InterruptedException e1) {
						Thread.currentThread().interrupt();
					}
				}
			}
		};
		getWorkspace().run(populate, null);
		return project;
	}

	/**
	 * Wait for autobuild notification to occur
	 */
	public static void waitForAutoBuild() {
		waitForJobFamily(ResourcesPlugin.FAMILY_AUTO_BUILD);
	}

	/**
	 * Allows Display to process events, so UI can make progress. Tests running
	 * in the UI thread may need to call this to avoid UI deadlocks.
	 * <p>
	 * For convenience, it is allowed to call this method from a non UI thread,
	 * but such calls have no effect.
	 */
	public static void waitForDisplay() {
		if (inUIThread()) {
			try {
				while (Display.getDefault().readAndDispatch()) {
					// do nothing
				}
			} catch (Throwable e) {
				// in e 44 this is throwing exceptions... a lot. Log them in case they contain
				// some valuable hints... but move along. These errors happen because some component
				// probably unrelated to our tests misbehaved. I suspect GTK3 may be causing
				// NPEs and other errors in the Eclipse UI code, for example. These errors seem
				// to propagate out of the 'readAndDispatch' call on e44.
				FrameworkCoreActivator.log(e);
			}
		}
	}

	public static void waitForEditor(IEditorPart editor) throws CoreException {
		IFileEditorInput editorInput = (IFileEditorInput) editor.getEditorInput();
		IFile file = editorInput.getFile();
		waitForResource(file);
	}

	public static void waitForJobFamily(Object jobFamily) {
		boolean wasInterrupted = false;
		do {
			try {
				Job.getJobManager().join(jobFamily, null);
				wasInterrupted = false;
			}
			catch (OperationCanceledException e) {
				e.printStackTrace();
			}
			catch (InterruptedException e) {
				wasInterrupted = true;
			}
		} while (wasInterrupted);
	}

	public static void waitForManualBuild() {
		waitForJobFamily(ResourcesPlugin.FAMILY_MANUAL_BUILD);
	}

	public static void waitForResource(IResource resource) throws CoreException {
		waitForAutoBuild();
		waitForManualBuild();
		waitForJobFamily(ResourcesPlugin.FAMILY_AUTO_REFRESH);
		waitForJobFamily(ResourcesPlugin.FAMILY_MANUAL_REFRESH);
		resource.refreshLocal(IResource.DEPTH_ONE, null);
	}

	public static void setAutoBuilding(boolean enabled) throws CoreException {
		IWorkspaceDescription wsd = getWorkspace().getDescription();
		if (!wsd.isAutoBuilding() == enabled) {
			wsd.setAutoBuilding(enabled);
			getWorkspace().setDescription(wsd);
		}
	}

	public static boolean isAutoBuilding() {
		return getWorkspace().getDescription().isAutoBuilding();
	}

	public static void assertNoErrors(IProject project) throws CoreException {
		boolean wasAutobuilding = isAutoBuilding();
		try {
			setAutoBuilding(false);
			project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);
			project.build(IncrementalProjectBuilder.FULL_BUILD, null);
			waitForManualBuild();
			waitForAutoBuild();

			IMarker[] problems = project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
			StringBuilder errors = new StringBuilder();
			int errorCount = 0;
			for (IMarker problem : problems) {
				if (problem.getAttribute(IMarker.SEVERITY, 0) >= IMarker.SEVERITY_ERROR) {
					errors.append(markerMessage(problem)+"\n");
					errorCount++;
					if (errorCount >= 10) { //don't include hundreds of errors. 10 is reasonable
						break;
					}
				}
			}
			if (errorCount > 0) {
				IJavaProject javaproject = JavaCore.create(project);
				ByteArrayOutputStream capture = new ByteArrayOutputStream();
				PrintStream out = new PrintStream(capture);
				try {
					StsTestUtil.dumpClasspathInfo(javaproject, out);
				} catch (Throwable e) {
					out.append("Error dumping classpath: "+ExceptionUtil.getMessage(e));
					//don't let problems getting classpath stop the test from printing error markers!
					e.printStackTrace();
				}
				out.close();
				Assert.fail("Expecting no problems but found: " + errors.toString() + "\n" + capture.toString());
			}
		} finally {
			setAutoBuilding(wasAutobuilding);
		}
	}

	public static String markerMessage(IMarker m) throws CoreException {
		StringBuffer msg = new StringBuffer("Marker {\n");
		final Map attributes = m.getAttributes();
		IResource rsrc = m.getResource();
		msg.append("   rsrc = " + (rsrc == null ? "unknown" : rsrc.getFullPath() + "\n"));
		for (Object atrName : attributes.keySet()) {
			msg.append("   " + atrName + " = " + attributes.get(atrName) + "\n");
		}
		msg.append("}");
		if (rsrc != null) {
			if (rsrc.getType() == IResource.FILE) {
				IFile file = (IFile) rsrc;
				if (isGroovyOrJava(file)) {
					InputStream content = file.getContents();
					if (content != null) {
						try {
							msg.append(">>>>>>>>> " + file.getFullPath() + "\n");
							BufferedReader reader = new BufferedReader(new InputStreamReader(content));
							String line = reader.readLine();
							int lineNumber = 1;
							while (line != null) {
								msg.append(String.format("%3d", lineNumber++) + ": " + line);
								line = reader.readLine();
							}
						}
						catch (IOException e) {
							msg.append("error reading file: (" + e.getClass().getName() + ") " + e.getMessage());
						}
						finally {
							msg.append("<<<<<<<<< " + file.getFullPath() + "\n");
							if (content != null) {
								try {
									content.close();
								}
								catch (IOException e) {
								}
							}
						}
					}
				}
			}
		}
		return msg.toString();
		// return m.getAttribute(IMarker.MESSAGE, "") + " line: " +
		// m.getAttribute(IMarker.LINE_NUMBER, "unknown")
		// + " location: " + m.getAttribute(IMarker.LOCATION, "unknown");
	}

	private static boolean isGroovyOrJava(IFile file) {
		String ext = file.getFileExtension();
		return "groovy".equals(ext) || "java".equals(ext);
	}

	public static boolean inUIThread() {
		return Display.getDefault().getThread() == Thread.currentThread();
	}

	/**
	 * Returns a port number that is available to start a server on.
	 */
	public static int findFreeSocketPort() throws IOException {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0); // port=0 will bind to a free port.
			int port = socket.getLocalPort();
			return port;
		}
		finally {
			if (socket != null) {
				socket.close();
			}
		}
	}

	/**
	 * Rethrow a 'Throwable' exception if it isn't null, without forcing calling
	 * method to declare throwing a Throwable (which really doesn't make much
	 * sense).
	 * @throws Exception
	 */
	public static void rethrow(Throwable e) throws Exception {
		if (e == null) {
			return;
		}
		else if (e instanceof Exception) {
			throw (Exception) e;
		}
		else if (e instanceof Error) {
			// There are only two kinds of throwables, so this must be an
			// unchecked runtime Exception
			throw (Error) e;
		}
		else {
			// This really shouldn't be possible... but just in case...
			throw new Error(e);
		}
	}

	/**
	 * Determines if we are running on an automated build machine or somewhere
	 * else.
	 */
	public static boolean isOnBuildSite() {
		if (isOnBuildSite == null) {
			String workspacePath = ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toString();
			isOnBuildSite = workspacePath.contains("com.springsource.sts.releng") || workspacePath.contains("bamboo");
		}
		return isOnBuildSite;
	}

	private static Boolean isOnBuildSite;

	public static Thread[] getAllThreads() {
		ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
		ThreadGroup parentGroup = rootGroup.getParent();
		while (parentGroup != null) {
			rootGroup = parentGroup;
			parentGroup = rootGroup.getParent();
		}
		int count;
		Thread[] threads;
		do {
			count = rootGroup.activeCount();
			threads = new Thread[count * 2];
			count = rootGroup.enumerate(threads);
		} while (!(count < threads.length)); // If array filled to the max, we
												// have no guarantee we got all
												// threads.
		Thread[] result = new Thread[count];
		System.arraycopy(threads, 0, result, 0, count);
		return result;
	}

	public static StringBuffer getStackDumps() {
		StringBuffer sb = new StringBuffer();
		Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
		for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
			sb.append(entry.getKey().toString());
			sb.append("\n");
			for (StackTraceElement element : entry.getValue()) {
				sb.append("  ");
				sb.append(element.toString());
				sb.append("\n");
			}
			sb.append("\n");
		}
		return sb;
	}

	public static void dumpClasspathInfo(IJavaProject javaProject) throws JavaModelException {
		dumpClasspathInfo(javaProject, System.out);
	}

	public static void dumpClasspathInfo(IJavaProject javaProject, PrintStream out) throws JavaModelException {
		out.println(">>>>> classpath for " + javaProject.getElementName());
		out.println("RAW classpath for " + javaProject.getElementName());
		IClasspathEntry[] entries = javaProject.getRawClasspath();
		for (IClasspathEntry e : entries) {
			out.println(e);
		}
		out.println("RESOLVED classpath for " + javaProject.getElementName());
		entries = javaProject.getResolvedClasspath(true);
		for (IClasspathEntry e : entries) {
			out.println(e);
		}
		out.println("<<<<<< classpath for " + javaProject.getElementName());
	}


	///////////////////////////////////////////////////////////////////////////////
	// Utilities for manipulating projectsAJ
	///////////////////////////////////////////////////////////////////////////////
	protected static class Requestor extends TypeNameRequestor { }
    /**
     * Force indexes to be populated
     */
    public static void performDummySearch(IJavaElement element) throws CoreException {
        new SearchEngine().searchAllTypeNames(
            null,
            SearchPattern.R_EXACT_MATCH,
            "XXXXXXXXX".toCharArray(), // make sure we search a concrete name. This is faster according to Kent
            SearchPattern.R_EXACT_MATCH,
            IJavaSearchConstants.CLASS,
            SearchEngine.createJavaSearchScope(new IJavaElement[]{element}),
            new Requestor(),
            IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
            null);
    }

    public static ICompilationUnit createCompilationUnit(IPackageFragment pack, String cuName,
            String source) throws JavaModelException {
        StringBuffer buf = new StringBuffer();
        buf.append(source);
        ICompilationUnit unit = pack.createCompilationUnit(cuName,
                buf.toString(), false, null);
        waitForManualBuild();
        waitForAutoBuild();
        return unit;
    }

    public static ICompilationUnit createCompilationUnitAndPackage(String packageName, String fileName,
            String source, IJavaProject javaProject) throws CoreException {
        return createCompilationUnit(createPackage(packageName, javaProject), fileName, source);
    }

    public static IPackageFragment createPackage(String name, IJavaProject javaProject) throws CoreException {
        return createPackage(name, null, javaProject);
    }
    public static IPackageFragment createPackage(String name, IPackageFragmentRoot sourceFolder, IJavaProject javaProject) throws CoreException {
        if (sourceFolder == null) {
			sourceFolder = createDefaultSourceFolder(javaProject);
		}
        return sourceFolder.createPackageFragment(name, false, null);
    }

    public static void buildProject(IJavaProject javaProject) throws Exception {
        javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
        assertNoErrors(javaProject.getProject());
        performDummySearch(javaProject);
    }

    public static String getProblems(IProject project) throws CoreException {
        IMarker[] markers = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        StringBuffer sb = new StringBuffer();
        if (markers == null || markers.length == 0) {
            return null;
        }
        boolean errorFound = false;
        sb.append("Problems:\n");
        for (IMarker marker : markers) {
            if (((Integer) marker.getAttribute(IMarker.SEVERITY)).intValue() == IMarker.SEVERITY_ERROR) {
                sb.append("  ");
                sb.append(marker.getResource().getName()).append(" : ");
                sb.append(marker.getAttribute(IMarker.LINE_NUMBER)).append(" : ");
                sb.append(marker.getAttribute(IMarker.MESSAGE)).append("\n");
                if (!((String) marker.getAttribute(IMarker.MESSAGE)).contains("can't determine modifiers of missing type")) {
                    errorFound = true;
                }
            }
        }
        return errorFound ? sb.toString() : null;
    }

    private static IPackageFragmentRoot createDefaultSourceFolder(IJavaProject javaProject) throws CoreException {
        IProject project = javaProject.getProject();
        IFolder folder = project.getFolder("src");
        if (!folder.exists()) {
			ensureExists(folder);
		}

        // if already exists, do nothing
        final IClasspathEntry[] entries = javaProject
                .getResolvedClasspath(false);
        final IPackageFragmentRoot root = javaProject
                .getPackageFragmentRoot(folder);
        for (final IClasspathEntry entry : entries) {
            if (entry.getPath().equals(folder.getFullPath())) {
                return root;
            }
        }


        // else, remove old source folders and add this new one
        IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
        List<IClasspathEntry> oldEntriesList = new ArrayList<IClasspathEntry>();
        oldEntriesList.add(JavaCore.newSourceEntry(root.getPath()));
        for (IClasspathEntry entry : oldEntries) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                oldEntriesList.add(entry);
            }
        }

        IClasspathEntry[] newEntries = oldEntriesList.toArray(new IClasspathEntry[0]);
        javaProject.setRawClasspath(newEntries, null);
        return root;
    }

    public static ICompilationUnit[] createUnits(String[] packages, String[] cuNames, String[] cuContents, IJavaProject project) throws Throwable {
        boolean oldAutoBuilding = isAutoBuilding();
        setAutoBuilding(false);

        try {
            ICompilationUnit[] units = new ICompilationUnit[cuNames.length];
            for (int i = 0; i < units.length; i++) {
                units[i] = createCompilationUnitAndPackage(packages[i], cuNames[i], cuContents[i], project);
            }
            project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
            waitForManualBuild();
            waitForAutoBuild();
            assertNoErrors(project.getProject());
            return units;
        } finally {
            setAutoBuilding(oldAutoBuilding);
        }
    }

    static private void ensureExists(IFolder folder) throws CoreException {
        if (folder.getParent().getType() == IResource.FOLDER && !folder.getParent().exists()) {
            ensureExists((IFolder) folder.getParent());
        }
        folder.create(false, true, null);
    }

    public static class ReaderInputStream extends InputStream {

    	private final Reader reader;

    	public ReaderInputStream(Reader reader){
    		this.reader = reader;
    	}

    	@Override
		public int read() throws IOException {
    		return reader.read();
    	}


        @Override
		public void close() throws IOException {
        	reader.close();
        }

    }
    public static class StringInputStream extends ReaderInputStream {
        public StringInputStream(String s) {
            super(new StringReader(s));
        }
    }


    public static IFile createXMLConfig(String fullPath, String beansContents) throws CoreException {
    	String fullContents = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
    			"<beans xmlns=\"http://www.springframework.org/schema/beans\"\r\n" +
    			"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
    			"	xmlns:context=\"http://www.springframework.org/schema/context\"\r\n" +
    			"	xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans-3.0.xsd\r\n" +
    			"		http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context-2.5.xsd\">\r\n" +
    			beansContents +
    			"\n</beans>";

        InputStream input = new StringInputStream(fullContents);
        IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(fullPath));
        try {
        	if (!file.exists()) {
        		file.create(input, true, null);
        	} else {
        		file.setContents(input, IResource.FORCE, null);
        	}
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                //ignore
            }
        }
        return file;
    }
}
