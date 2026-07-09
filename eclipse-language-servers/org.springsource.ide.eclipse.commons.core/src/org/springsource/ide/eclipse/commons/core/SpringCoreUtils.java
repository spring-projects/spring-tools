// COPIED from spring-ide org.springframework.ide.eclipse.core.SpringCoreUtils
/*******************************************************************************
 * Copyright (c) 2012, 2022 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.wst.common.project.facet.core.FacetedProjectFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Some helper methods.
 * @author Torsten Juergeleit
 * @author Christian Dupuis
 * @author Martin Lippert
 */
public final class SpringCoreUtils {

	/**
	 * File name of OSGi bundle manifests
	 * @since 2.0.5
	 */
	public static String BUNDLE_MANIFEST_FILE = "MANIFEST.MF";

	/**
	 * Folder name of OSGi bundle manifests directories
	 * @since 2.0.5
	 */
	public static String BUNDLE_MANIFEST_FOLDER = "META-INF";

	/** New placeholder string for Spring 3 EL support */
	public static final String EL_PLACEHOLDER_PREFIX = "#{";

	/** URL file schema */
	public static final String FILE_SCHEME = "file";

	public static final String LINE_SEPARATOR = System.getProperty("line.separator");

	public static final String PLACEHOLDER_PREFIX = "${";

	public static final String PLACEHOLDER_SUFFIX = "}";

	public static final String SOURCE_CONTROL_SCHEME = "sourcecontrol";

	private static final String DEPLOY_PATH = "deploy-path";

	private static boolean DOCUMENT_BUILDER_ERROR = false;

	private static final Object DOCUMENT_BUILDER_LOCK = new Object();

	private static XPathExpression EXPRESSION;

	private static boolean SAX_PARSER_ERROR = false;

	private static final Object SAX_PARSER_LOCK = new Object();

	private static final String SOURCE_PATH = "source-path";

	private static final String XPATH_EXPRESSION = "//project-modules/wb-module/wb-resource";

	private static final String SPRING_BUILDER_ID = "org.springframework.ide.eclipse.core.springbuilder";

	private static final String MARKER_ID = "org.springframework.ide.eclipse.core.problemmarker";

	public static final String NATURE_ID = "org.springframework.ide.eclipse.core.springnature";

	static {
		try {
			XPathFactory newInstance = XPathFactory.newInstance();
			XPath xpath = newInstance.newXPath();
			EXPRESSION = xpath.compile(XPATH_EXPRESSION);
		}
		catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Adds given builder to specified project.
	 */
	public static void addProjectBuilder(IProject project, String builderID, IProgressMonitor monitor)
			throws CoreException {
		IProjectDescription desc = project.getDescription();
		ICommand builderCommand = getProjectBuilderCommand(desc, builderID);
		if (builderCommand == null) {

			// Add a new build spec
			ICommand command = desc.newCommand();
			command.setBuilderName(builderID);

			// Commit the spec change into the project
			addProjectBuilderCommand(desc, command);
			project.setDescription(desc, monitor);
		}
	}

	/**
	 * Adds (or updates) a builder in given project description.
	 */
	public static void addProjectBuilderCommand(IProjectDescription description, ICommand command) throws CoreException {
		ICommand[] oldCommands = description.getBuildSpec();
		ICommand oldBuilderCommand = getProjectBuilderCommand(description, command.getBuilderName());
		ICommand[] newCommands;
		if (oldBuilderCommand == null) {

			// Add given builder to the end of the builder list
			newCommands = new ICommand[oldCommands.length + 1];
			System.arraycopy(oldCommands, 0, newCommands, 0, oldCommands.length);
			newCommands[oldCommands.length] = command;
		}
		else {

			// Replace old builder with given new one
			for (int i = 0, max = oldCommands.length; i < max; i++) {
				if (oldCommands[i] == oldBuilderCommand) {
					oldCommands[i] = command;
					break;
				}
			}
			newCommands = oldCommands;
		}
		description.setBuildSpec(newCommands);
	}

	/**
	 * Adds given nature as first nature to specified project.
	 */
	public static void addProjectNature(IProject project, String nature, IProgressMonitor monitor) throws CoreException {
		if (project != null && nature != null) {
			if (!project.hasNature(nature)) {
				IProjectDescription desc = project.getDescription();
				String[] oldNatures = desc.getNatureIds();
				String[] newNatures = new String[oldNatures.length + 1];
				newNatures[0] = nature;
				if (oldNatures.length > 0) {
					System.arraycopy(oldNatures, 0, newNatures, 1, oldNatures.length);
				}
				desc.setNatureIds(newNatures);
				project.setDescription(desc, monitor);
			}
		}
	}

	/**
	 * Triggers a build of the given {@link IProject} instance, but only the
	 * Spring builder
	 * @param project the project to build
	 */
	public static void buildProject(IProject project) {
		buildProject(project, SPRING_BUILDER_ID);
	}

	/**
	 * Triggers a build of the given {@link IProject} instance with a full build
	 * and all builders
	 * @param project the project to build
	 */
	public static void buildFullProject(IProject project) {
		buildProject(project, null);
	}

	/**
	 * Triggers a build of the given {@link IProject} instance, but only the
	 * Spring builder
	 * @param project the project to build
	 * @param builderID the ID of the specific builder that should be executed
	 * on the project
	 * @since 3.2.0
	 */
	public static void buildProject(IProject project, String builderID) {
		if (ResourcesPlugin.getWorkspace().isAutoBuilding()) {
			scheduleBuildInBackground(project, ResourcesPlugin.getWorkspace().getRuleFactory().buildRule(),
					new Object[] { ResourcesPlugin.FAMILY_AUTO_BUILD }, builderID);
		}
	}

	/**
	 * Creates given folder and (if necessary) all of it's parents.
	 */
	public static void createFolder(IFolder folder, IProgressMonitor monitor) throws CoreException {
		if (!folder.exists()) {
			IContainer parent = folder.getParent();
			if (parent instanceof IFolder) {
				createFolder((IFolder) parent, monitor);
			}
			folder.create(true, true, monitor);
		}
	}

	/**
	 * Creates specified simple project.
	 */
	public static IProject createProject(String projectName, IProjectDescription description, IProgressMonitor monitor)
			throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		if (!project.exists()) {
			if (description == null) {
				project.create(monitor);
			}
			else {
				project.create(description, monitor);
			}
		}
		else {
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		}
		if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		if (!project.isOpen()) {
			project.open(monitor);
		}
		return project;
	}

	/**
	 * Finds the first line separator used by the given text.
	 * @return </code>"\n"</code> or </code>"\r"</code> or </code>"\r\n"</code>,
	 * or <code>null</code> if none found
	 * @since 2.2.2
	 */
	public static String findLineSeparator(char[] text) {
		// find the first line separator
		int length = text.length;
		if (length > 0) {
			char nextChar = text[0];
			for (int i = 0; i < length; i++) {
				char currentChar = nextChar;
				nextChar = i < length - 1 ? text[i + 1] : ' ';
				switch (currentChar) {
				case '\n':
					return "\n"; //$NON-NLS-1$
				case '\r':
					return nextChar == '\n' ? "\r\n" : "\r"; //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
		// not found
		return null;
	}

	/**
	 * Returns the specified adapter for the given object or <code>null</code>
	 * if adapter is not supported.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getAdapter(Object object, Class<T> adapter) {
		if (object != null && adapter != null) {
			if (adapter.isAssignableFrom(object.getClass())) {
				return (T) object;
			}
			if (object instanceof IAdaptable) {
				return (T) ((IAdaptable) object).getAdapter(adapter);
			}
		}
		return null;
	}

	public static IFile getDeploymentDescriptor(IProject project) {
		if (SpringCoreUtils.hasProjectFacet(project, "jst.web")) {
			IFile settingsFile = project.getFile(".settings/org.eclipse.wst.common.component");
			if (settingsFile.exists()) {
				try {
					NodeList nodes = (NodeList) EXPRESSION
							.evaluate(parseDocument(settingsFile), XPathConstants.NODESET);
					for (int i = 0; i < nodes.getLength(); i++) {
						Element element = (Element) nodes.item(i);
						if ("/".equals(element.getAttribute(DEPLOY_PATH))) {
							String path = element.getAttribute(SOURCE_PATH);
							if (path != null) {
								IFile deploymentDescriptor = project.getFile(new Path(path).append("WEB-INF").append(
										"web.xml"));
								if (deploymentDescriptor.exists()) {
									return deploymentDescriptor;
								}
							}
						}
					}
				}
				catch (Exception e) {
					StatusHandler.log(new Status(IStatus.WARNING, CorePlugin.PLUGIN_ID, 1, e.getMessage(), e));
				}
			}
		}
		return null;
	}

	public static DocumentBuilder getDocumentBuilder() {
		try {
			DocumentBuilderFactory documentBuilderFactory = getDocumentBuilderFactory();
			documentBuilderFactory.setExpandEntityReferences(false);
			return documentBuilderFactory.newDocumentBuilder();
		}
		catch (Exception e) {
			StatusHandler.log(new Status(IStatus.ERROR, CorePlugin.PLUGIN_ID, "Error creating DocumentBuilder", e));
		}
		return null;
	}

	public static DocumentBuilderFactory getDocumentBuilderFactory() {
		if (!DOCUMENT_BUILDER_ERROR) {
			try {
				// this might fail on IBM J9; therefore trying only once and
				// then falling back to
				// OSGi service reference as it should be
				return DocumentBuilderFactory.newInstance();
			}
			catch (Exception e) {
				StatusHandler.log(new Status(IStatus.INFO, CorePlugin.PLUGIN_ID,
						"Error creating DocumentBuilderFactory. Switching to OSGi service reference."));
				DOCUMENT_BUILDER_ERROR = true;
			}
		}

		BundleContext bundleContext = CorePlugin.getDefault().getBundle().getBundleContext();
		ServiceReference reference = bundleContext.getServiceReference(DocumentBuilderFactory.class.getName());
		if (reference != null) {
			try {
				synchronized (DOCUMENT_BUILDER_LOCK) {
					return (DocumentBuilderFactory) bundleContext.getService(reference);
				}
			}
			catch (Exception e) {
				StatusHandler.log(new Status(IStatus.ERROR, CorePlugin.PLUGIN_ID,
						"Error creating DocumentBuilderFactory", e));
			}
			finally {
				bundleContext.ungetService(reference);
			}
		}

		return null;
	}

	/**
	 * Returns the line separator found in the given text. If it is null, or not
	 * found return the line delimiter for the given project. If the project is
	 * null, returns the line separator for the workspace. If still null, return
	 * the system line separator.
	 * @since 2.2.2
	 */
	public static String getLineSeparator(String text, IProject project) {
		String lineSeparator = null;

		// line delimiter in given text
		if (text != null && text.length() != 0) {
			lineSeparator = findLineSeparator(text.toCharArray());
			if (lineSeparator != null) {
				return lineSeparator;
			}
		}

		// line delimiter in project preference
		IScopeContext[] scopeContext;
		if (project != null) {
			scopeContext = new IScopeContext[] { new ProjectScope(project) };
			lineSeparator = Platform.getPreferencesService().getString(Platform.PI_RUNTIME,
					Platform.PREF_LINE_SEPARATOR, null, scopeContext);
			if (lineSeparator != null) {
				return lineSeparator;
			}
		}

		// line delimiter in workspace preference
		scopeContext = new IScopeContext[] { new InstanceScope() };
		lineSeparator = Platform.getPreferencesService().getString(Platform.PI_RUNTIME, Platform.PREF_LINE_SEPARATOR,
				null, scopeContext);
		if (lineSeparator != null) {
			return lineSeparator;
		}

		// system line delimiter
		return LINE_SEPARATOR;
	}

	/**
	 * Returns specified builder from given project description.
	 */
	public static ICommand getProjectBuilderCommand(IProjectDescription description, String builderID)
			throws CoreException {
		ICommand[] commands = description.getBuildSpec();
		for (int i = commands.length - 1; i >= 0; i--) {
			if (commands[i].getBuilderName().equals(builderID)) {
				return commands[i];
			}
		}
		return null;
	}

	public static IPath getProjectLocation(IProject project) {
		return (project.getRawLocation() != null ? project.getRawLocation() : project.getLocation());
	}

	public static URI getResourceURI(IResource resource) {
		if (resource != null) {
			URI uri = resource.getRawLocationURI();
			if (uri == null) {
				uri = resource.getLocationURI();
			}
			if (uri != null) {
				String scheme = uri.getScheme();
				if (FILE_SCHEME.equalsIgnoreCase(scheme)) {
					return uri;
				}
				else if (SOURCE_CONTROL_SCHEME.equals(scheme)) {
					// special case of Rational Team Concert
					IPath path = resource.getLocation();
					File file = path.toFile();
					if (file.exists()) {
						return file.toURI();
					}
				}
				else {
					IPathVariableManager variableManager = ResourcesPlugin.getWorkspace().getPathVariableManager();
					return variableManager.resolveURI(uri);
				}
			}
		}
		return null;
	}

	public static SAXParser getSaxParser() {
		if (!SAX_PARSER_ERROR) {
			try {
				SAXParserFactory factory = SAXParserFactory.newInstance();
				factory.setNamespaceAware(true);
				SAXParser parser = factory.newSAXParser();
				return parser;
			}
			catch (Exception e) {
				StatusHandler.log(new Status(IStatus.INFO, CorePlugin.PLUGIN_ID,
						"Error creating SaxParserFactory. Switching to OSGI service reference."));
				SAX_PARSER_ERROR = true;
			}
		}

		BundleContext bundleContext = CorePlugin.getDefault().getBundle().getBundleContext();
		ServiceReference reference = bundleContext.getServiceReference(SAXParserFactory.class.getName());
		if (reference != null) {
			try {
				synchronized (SAX_PARSER_LOCK) {
					SAXParserFactory factory = (SAXParserFactory) bundleContext.getService(reference);
					return factory.newSAXParser();
				}
			}
			catch (Exception e) {
				StatusHandler
						.log(new Status(IStatus.ERROR, CorePlugin.PLUGIN_ID, "Error creating SaxParserFactory", e));
			}
			finally {
				bundleContext.ungetService(reference);
			}
		}

		return null;
	}

	/**
	 * Returns a list of all projects with the Spring project nature.
	 */
	public static Set<IProject> getSpringProjects() {
		Set<IProject> projects = new LinkedHashSet<IProject>();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (isSpringProject(project)) {
				projects.add(project);
			}
		}
		return projects;
	}

	/**
	 * Returns true if given resource's project has the given nature.
	 */
	public static boolean hasNature(IResource resource, String natureId) {
		if (resource != null && resource.isAccessible()) {
			IProject project = resource.getProject();
			if (project != null) {
				try {
					return project.hasNature(natureId);
				}
				catch (CoreException e) {
					StatusHandler.log(new Status(IStatus.ERROR, CorePlugin.PLUGIN_ID,
							"An error occurred inspecting project nature", e));
				}
			}
		}
		return false;
	}

	/**
	 * Returns <code>true</code> if given text contains a placeholder, e.g.
	 * <code>${beansRef}</code> .
	 */
	public static boolean hasPlaceHolder(String text) {
		if (text == null || StringUtils.isBlank(text)) {
			return false;
		}
		int pos = text.indexOf(PLACEHOLDER_PREFIX);
		int elPos = text.indexOf(EL_PLACEHOLDER_PREFIX);
		return ((pos != -1 || elPos != -1) && text.indexOf(PLACEHOLDER_SUFFIX, pos) != -1);
	}

	public static boolean hasProjectFacet(IResource resource, String facetId) {
		if (resource != null && resource.isAccessible()) {
			try {
				return JdtUtils.isJavaProject(resource)
						&& FacetedProjectFramework.hasProjectFacet(resource.getProject(), facetId);
			}
			catch (CoreException e) {
				// TODO CD handle exception
			}
		}
		return false;
	}

	/**
	 * Returns true if there is an Eclise bundle with bundleID and its version is the same or newer than the given version.
	 * @param bundleID TODO
	 */
	public static boolean isEclipseBundleSameOrNewer(String bundleID, int majorVersion, int minorVersion) {
		Bundle bundle = Platform.getBundle(bundleID);

		if (bundle != null) {
			String versionString = bundle.getHeaders().get(org.osgi.framework.Constants.BUNDLE_VERSION);
			try {
				Version version = new Version(versionString);
				int major = version.getMajor();
				if (major > majorVersion) {
					return true;
				}
				if (major == majorVersion) {
					int minor = version.getMinor();
					if (minor >= minorVersion) {
						return true;
					}
				}
			}
			catch (IllegalArgumentException e) {
				// ignore this exception as this can't occur in pratice
			}
		}
		return false;
	}

	/**
	 * Checks if the given {@link IResource} is a OSGi bundle manifest.
	 * <p>
	 * Note: only the name and last segment of the folder name are checked.
	 * @since 2.0.5
	 */
	public static boolean isManifest(IResource resource) {
		// check if it is a MANIFEST.MF file in META-INF
		if (resource != null
				// && resource.isAccessible()
				&& resource.getType() == IResource.FILE && resource.getName().equals(BUNDLE_MANIFEST_FILE)
				&& resource.getParent() != null && resource.getParent().getProjectRelativePath() != null
				&& resource.getParent().getProjectRelativePath().lastSegment() != null
				&& resource.getParent().getProjectRelativePath().lastSegment().equals(BUNDLE_MANIFEST_FOLDER)) {

			// check if the manifest is not in an output folder
			IPath filePath = resource.getFullPath();
			IJavaProject javaProject = JdtUtils.getJavaProject(resource);
			if (javaProject != null) {
				try {
					IPath defaultOutputLocation = javaProject.getOutputLocation();
					if (defaultOutputLocation != null && defaultOutputLocation.isPrefixOf(filePath)) {
						return false;
					}
					for (IClasspathEntry entry : javaProject.getRawClasspath()) {
						if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
							IPath outputLocation = entry.getOutputLocation();
							if (outputLocation != null && outputLocation.isPrefixOf(filePath)) {
								return false;
							}
						}
					}
				}
				catch (JavaModelException e) {
					// don't care here
				}
				return true;
			}
			else {
				// if the project is not a java project -> it is the manifest
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if given resource's project is a Spring project.
	 */
	public static boolean isSpringProject(IResource resource) {
		return hasNature(resource, NATURE_ID);
	}

	/**
	 * Returns true if Eclipse's runtime bundle has the same or a newer than
	 * given version.
	 */
	public static boolean isVersionSameOrNewer(String versionString, int majorVersion, int minorVersion,
			int microVersion) {
		return new Version(versionString).compareTo(new Version(majorVersion, minorVersion, microVersion)) >= 0;
	}

	public static Document parseDocument(IFile deploymentDescriptor) {
		try {
			if (getResourceURI(deploymentDescriptor) != null) {
				return parseDocument(getResourceURI(deploymentDescriptor));
			}
			return getDocumentBuilder().parse(new InputSource(deploymentDescriptor.getContents()));
		}
		catch (SAXException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}

	public static Document parseDocument(URI deploymentDescriptor) {
		try {
			return getDocumentBuilder().parse(deploymentDescriptor.toString());
		}
		catch (SAXException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Removes given builder from specified project.
	 */
	public static void removeProjectBuilder(IProject project, String builderID, IProgressMonitor monitor)
			throws CoreException {
		if (project != null && builderID != null) {
			IProjectDescription desc = project.getDescription();
			ICommand[] commands = desc.getBuildSpec();
			for (int i = commands.length - 1; i >= 0; i--) {
				if (commands[i].getBuilderName().equals(builderID)) {
					ICommand[] newCommands = new ICommand[commands.length - 1];
					System.arraycopy(commands, 0, newCommands, 0, i);
					System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);
					// Commit the spec change into the project
					desc.setBuildSpec(newCommands);
					project.setDescription(desc, monitor);
					break;
				}
			}
		}
	}

	/**
	 * Removes given nature from specified project.
	 */
	public static void removeProjectNature(IProject project, String nature, IProgressMonitor monitor)
			throws CoreException {
		if (project != null && nature != null) {
			if (project.exists() && project.hasNature(nature)) {

				// first remove all problem markers (including the
				// inherited ones) from Spring beans project
				if (nature.equals(NATURE_ID)) {
					project.deleteMarkers(MARKER_ID, true, IResource.DEPTH_INFINITE);
				}

				// now remove project nature
				IProjectDescription desc = project.getDescription();
				String[] oldNatures = desc.getNatureIds();
				String[] newNatures = new String[oldNatures.length - 1];
				int newIndex = oldNatures.length - 2;
				for (int i = oldNatures.length - 1; i >= 0; i--) {
					if (!oldNatures[i].equals(nature)) {
						newNatures[newIndex--] = oldNatures[i];
					}
				}
				desc.setNatureIds(newNatures);
				project.setDescription(desc, monitor);
			}
		}
	}

	/**
	 * Verify that file can safely be modified; eventually checkout the file
	 * from source code control.
	 * @return <code>true</code> if resource can be modified
	 * @since 2.2.9
	 */
	public static boolean validateEdit(IFile... files) {
		for (IFile file : files) {
			if (!file.exists()) {
				return false;
			}
		}
		IStatus status = ResourcesPlugin.getWorkspace().validateEdit(files, IWorkspace.VALIDATE_PROMPT);
		if (status.isOK()) {
			return true;
		}
		return false;
	}

	private static void scheduleBuildInBackground(final IProject project, ISchedulingRule rule,
			final Object[] jobFamilies, final String builderID) {
		Job job = new Job("Building workspace") {

			@Override
			public boolean belongsTo(Object family) {
				if (jobFamilies == null || family == null) {
					return false;
				}
				for (Object jobFamilie : jobFamilies) {
					if (family.equals(jobFamilie)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public IStatus run(IProgressMonitor monitor) {
				try {
					if (builderID != null) {
						project.build(IncrementalProjectBuilder.FULL_BUILD, builderID, null, monitor);
					}
					else {
						project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
					}
					return Status.OK_STATUS;
				}
				catch (CoreException e) {
					return new Status(Status.ERROR, CorePlugin.PLUGIN_ID, 1, "Error during build of project ["
							+ project.getName() + "]", e);
				}
			}
		};
		if (rule != null) {
			job.setRule(rule);
		}
		job.setPriority(Job.BUILD);
		job.schedule();
	}
}
