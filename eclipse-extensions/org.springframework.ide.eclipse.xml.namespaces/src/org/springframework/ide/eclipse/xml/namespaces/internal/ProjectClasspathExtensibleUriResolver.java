/*******************************************************************************
 * Copyright (c) 2010, 2014 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.xml.namespaces.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Preferences.IPropertyChangeListener;
import org.eclipse.core.runtime.Preferences.PropertyChangeEvent;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.wst.common.uriresolver.internal.provisional.URIResolverExtension;
import org.springframework.ide.eclipse.xml.namespaces.ProjectAwareUrlStreamHandlerService;
import org.springframework.ide.eclipse.xml.namespaces.SpringXmlNamespacesPlugin;
import org.springframework.ide.eclipse.xml.namespaces.XmlNamespaceUtils;
import org.springframework.ide.eclipse.xml.namespaces.util.DocumentAccessor;
import org.springframework.ide.eclipse.xml.namespaces.util.DocumentAccessor.SchemaLocations;
import org.springsource.ide.eclipse.commons.core.JdtUtils;
import org.springsource.ide.eclipse.commons.frameworks.core.util.XmlUtils;
import org.springsource.ide.eclipse.commons.core.SpringCorePreferences;
import org.w3c.dom.Document;

/**
 * {@link URIResolverExtension} resolves URIs on the project classpath using the
 * protocol established by <code>spring.schemas</code> files.
 * 
 * @author Christian Dupuis
 * @author Martin Lippert
 * @since 2.3.1
 */
@SuppressWarnings({ "restriction", "deprecation" })
public class ProjectClasspathExtensibleUriResolver implements
		URIResolverExtension, IElementChangedListener, IPropertyChangeListener,
		IPreferenceChangeListener {

	private static final String KEY_DISABLE_CACHING_PREFERENCE = SpringXmlNamespacesPlugin.PLUGIN_ID + "."
			+ SpringXmlNamespacesPlugin.DISABLE_CACHING_FOR_NAMESPACE_LOADING_ID;
	
//	private static Map<IProject, ProjectClasspathUriResolver> projectResolvers = new ConcurrentHashMap<IProject, ProjectClasspathUriResolver>();
	private static ConcurrentMap<IProject, Future<ProjectClasspathUriResolver>> projectResolvers = new ConcurrentHashMap<IProject, Future<ProjectClasspathUriResolver>>();

	public ProjectClasspathExtensibleUriResolver() {
		JavaCore.addElementChangedListener(this,
				ElementChangedEvent.POST_CHANGE);
		SpringXmlNamespacesPlugin.getDefault().getPluginPreferences()
				.addPropertyChangeListener(this);
	}

	/**
	 * {@inheritDoc}
	 */
	public String resolve(IFile file, String baseLocation, String publicId,
			String systemId) {
		// systemId is already resolved; so don't touch
		if (systemId != null && systemId.startsWith("jar:")) {
			return null;
		}
		
		// identify the correct project
		IProject project = null;
		if (file != null) {
			project = getBestMatchingProject(file);
		} else if (baseLocation != null 
				&& baseLocation.startsWith(ProjectAwareUrlStreamHandlerService.PROJECT_AWARE_PROTOCOL_HEADER)) {
			String nameAndLocation = baseLocation
					.substring(ProjectAwareUrlStreamHandlerService.PROJECT_AWARE_PROTOCOL_HEADER
							.length());
			String projectName = nameAndLocation.substring(0, nameAndLocation.indexOf('/'));
			project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		}
		
//		// continue just for identified Spring projects
//		if (project == null || BeansCorePlugin.getModel().getProject(project) == null) {
//			return null;
//		}

		if (systemId == null && file != null) {
			systemId = findSystemIdFromFile(file, publicId);
		}

		if (systemId == null && publicId == null) {
			return null;
		}
		
		ProjectClasspathUriResolver resolver = getProjectResolver(file, project);
		if (resolver != null) {
			String resolved = resolver.resolveOnClasspath(publicId, systemId);
			if (resolved != null) {
				resolved = ProjectAwareUrlStreamHandlerService.createProjectAwareUrl(project.getName(), resolved);
			}
			return resolved;
		}

		return null;
	}

	private IProject getBestMatchingProject(IFile file) {
		IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(file.getLocationURI());
		if (files != null && files.length == 1) {
			return files[0].getProject();
		}
		else if (files != null && files.length > 1) {
			IFile shortestPathFile = files[0];
			int shortestPathSegmentCount = shortestPathFile.getFullPath().segmentCount();

			for (int i = 1; i < files.length; i++) {
				int segmentCount = files[i].getFullPath().segmentCount();
				if (segmentCount < shortestPathSegmentCount) {
					shortestPathFile = files[i];
					shortestPathSegmentCount = segmentCount;
				}
			}
			return shortestPathFile.getProject();
		}
		else {
			return null;
		}
	}

	private ProjectClasspathUriResolver getProjectResolver(final IFile file, final IProject project) {
//		// no project resolver if not a spring project
//		IBeansProject beansProject = BeansCorePlugin.getModel().getProject(project);
//		if (beansProject == null) {
//			return null;
//		}

		if (!XmlNamespaceUtils.useNamespacesFromClasspath(project)) {
			return null;
		}

		if (file != null && !checkFileExtension(file/*, beansProject*/)) {
			return null;
		}

		// Special case for 'pom.xml'. We can skip it entirely because it is not used to define spring beans.
		// Also... m2e apparantly causes this to be called directly from the UI thread causing major hangs / annoyance.
		// See: https://github.com/spring-projects/sts4/issues/318
		if (file != null && "pom.xml".equals(file.getName())) {
			return null;
		}

		while (true) {
			Future<ProjectClasspathUriResolver> future = projectResolvers.get(project);
			if (future == null) {
				
				Callable<ProjectClasspathUriResolver> createResolver = new Callable<ProjectClasspathUriResolver>() {
					public ProjectClasspathUriResolver call() throws InterruptedException {
						ProjectClasspathUriResolver resolver = new ProjectClasspathUriResolver(
								project);
						
						SpringCorePreferences
								.getProjectPreferences(project, SpringXmlNamespacesPlugin.PLUGIN_ID)
								.getProjectPreferences().addPreferenceChangeListener(ProjectClasspathExtensibleUriResolver.this);

						return resolver;
					}
				};
				
				FutureTask<ProjectClasspathUriResolver> futureTask = new FutureTask<ProjectClasspathUriResolver>(createResolver);
				future = projectResolvers.putIfAbsent(project, futureTask);
				if (future == null) {
					future = futureTask;
					futureTask.run();
				}
			}
			
			try {
				return future.get();
			}
			catch (CancellationException e) {
				projectResolvers.remove(project, future);
				return null;
			}
			catch (ExecutionException e) {
				return null;
			} catch (InterruptedException e) {
				return null;
			}
		}
		
	}

	public void elementChanged(ElementChangedEvent event) {
		for (IJavaElementDelta delta : event.getDelta().getAffectedChildren()) {
			if ((delta.getFlags() & IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED) != 0
					|| (delta.getFlags() & IJavaElementDelta.F_CLASSPATH_CHANGED) != 0) {
				resetForChangedElement(delta.getElement());
			} else if ((delta.getFlags() & IJavaElementDelta.F_CLOSED) != 0) {
				resetForChangedElement(delta.getElement());
			} else if ((delta.getFlags() & IJavaElementDelta.F_OPENED) != 0) {
				resetForChangedElement(delta.getElement());
			} else if ((delta.getKind() & IJavaElementDelta.REMOVED) != 0) {
				resetForChangedElement(delta.getElement());
			} else if ((delta.getKind() & IJavaElementDelta.ADDED) != 0) {
				resetForChangedElement(delta.getElement());
			}
		}
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (SpringXmlNamespacesPlugin.DISABLE_CACHING_FOR_NAMESPACE_LOADING_ID
				.equals(event.getProperty())) {
			projectResolvers.clear();
		}
	}

	public void preferenceChange(PreferenceChangeEvent event) {
		if (KEY_DISABLE_CACHING_PREFERENCE.equals(event.getKey())) {
			projectResolvers.clear();
		}
	}

	private void resetForChangedElement(IJavaElement element) {
		if (element instanceof IJavaProject) {
			IProject project = ((IJavaProject) element).getProject();
			projectResolvers.remove(project);
			SpringCorePreferences
					.getProjectPreferences(project, SpringXmlNamespacesPlugin.PLUGIN_ID)
					.getProjectPreferences()
					.removePreferenceChangeListener(this);
		}
		
		for (IProject project : projectResolvers.keySet()) {
			IJavaProject javaProject = JdtUtils.getJavaProject(project);
			if (javaProject != null) {
				if (javaProject.isOnClasspath(element)) {
					projectResolvers.remove(project);

					SpringCorePreferences
							.getProjectPreferences(project,
									SpringXmlNamespacesPlugin.PLUGIN_ID)
							.getProjectPreferences()
							.removePreferenceChangeListener(this);
				}
			}
		}
	}

	/**
	 * try to extract the system-id of the given namespace from the xml file
	 * 
	 * @since 2.6.0
	 */
	private String findSystemIdFromFile(IFile file, String publicIc) {
		InputStream contents = null;
		try {
			contents = file.getContents();
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory
					.newInstance();
			builderFactory.setValidating(false);
			builderFactory.setNamespaceAware(true);

			builderFactory.setFeature("http://xml.org/sax/features/validation", false);
			builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			XmlUtils.configureDocumentBuilderFactory(builderFactory);

			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document doc = builder.parse(contents);

			DocumentAccessor accessor = new DocumentAccessor();
			accessor.pushDocument(doc);
			SchemaLocations locations = accessor.getCurrentSchemaLocations();

			String location = locations.getSchemaLocation(publicIc);
			return location;
		} catch (Exception e) {
			// do nothing, systemId cannot be identified
		} finally {
			if (contents != null) {
				try {
					contents.close();
				} catch (IOException e) {
					// do nothing, systemId cannot be identified
				}
			}
		}
		return null;
	}

	/**
	 * Check that the file has a valid file extension.
	 */
	private boolean checkFileExtension(IFile file/*, IBeansProject project*/) {
		if (/*project.*/getConfigSuffixes() != null) {
			for (String extension : /*project.*/getConfigSuffixes()) {
				if (file.getName().endsWith(extension)) {
					return true;
				}
			}
		}

		if (file.getName().endsWith(".xsd")) {
			return true;
		}

		return false;
	}

	private String[] getConfigSuffixes() {
		return new String[]{ ".xml" };
	}

}
