/*******************************************************************************
 * Copyright (c) 2009, 2015 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.xml.namespaces.classpath;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Preferences.IPropertyChangeListener;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.springframework.ide.eclipse.xml.namespaces.SpringXmlNamespacesPlugin;
import org.springsource.ide.eclipse.commons.core.JdtUtils;

/**
 * Internal cache of classpath urls and corresponding resourceloaders.
 * @author Christian Dupuis
 * @author Martin Lippert
 * @since 2.2.5
 */
@SuppressWarnings("deprecation")
public class ProjectResourceLoaderCache {

	private static final String FILE_SCHEME = "file";
	private static final int CACHE_SIZE = 200;
	private static final List<ResourceLoaderCacheEntry> RESOURCELOADER_CACHE = new ArrayList<ResourceLoaderCacheEntry>(CACHE_SIZE);

	private static final String DEBUG_OPTION = SpringXmlNamespacesPlugin.PLUGIN_ID + "/java/resourceloader/debug";
	private static final boolean DEBUG_RESOURCELOADER = SpringXmlNamespacesPlugin.isDebug(DEBUG_OPTION);

	private static IResourceChangeListener resourceChangeListener = null;

	private static ResourceLoader addResourceLoaderToCache(IProject project, List<URL> urls, ResourceLoader parentResourceLoader) {
		synchronized (RESOURCELOADER_CACHE) {
			int nEntries = RESOURCELOADER_CACHE.size();
			if (nEntries >= CACHE_SIZE) {
				// find obsolete entries or remove entry that was least recently accessed
				ResourceLoaderCacheEntry oldest = null;
				List<ResourceLoaderCacheEntry> obsoleteResourceLoaders = new ArrayList<ResourceLoaderCacheEntry>(CACHE_SIZE);
				for (int i = 0; i < nEntries; i++) {
					ResourceLoaderCacheEntry entry = (ResourceLoaderCacheEntry) RESOURCELOADER_CACHE.get(i);
					IProject curr = entry.getProject();
					if (!curr.exists() || !curr.isAccessible() || !curr.isOpen()) {
						obsoleteResourceLoaders.add(entry);
					}
					else {
						if (oldest == null || entry.getLastAccess() < oldest.getLastAccess()) {
							oldest = entry;
						}
					}
				}
				if (!obsoleteResourceLoaders.isEmpty()) {
					for (int i = 0; i < obsoleteResourceLoaders.size(); i++) {
						removeResourceLoaderEntryFromCache((ResourceLoaderCacheEntry) obsoleteResourceLoaders.get(i));
					}
				}
				else if (oldest != null) {
					removeResourceLoaderEntryFromCache(oldest);
				}
			}
			ResourceLoaderCacheEntry newEntry = new ResourceLoaderCacheEntry(project, urls, parentResourceLoader);
			RESOURCELOADER_CACHE.add(newEntry);
			return newEntry.getResourceLoader();
		}
	}

	/**
	 * Add {@link URL}s to the given set of <code>paths</code>.
	 */
	private static void addClassPathUrls(IProject project, List<URL> paths, Set<IProject> resolvedProjects) {

		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

		// add project to local cache to prevent adding its classpaths multiple times
		if (resolvedProjects.contains(project)) {
			return;
		}
		else {
			resolvedProjects.add(project);
		}

		try {
			if (JdtUtils.isJavaProject(project)) {
				IJavaProject jp = JavaCore.create(project);
				// configured classpath
				IClasspathEntry[] classpath = jp.getResolvedClasspath(true);

				// add class path entries
				for (int i = 0; i < classpath.length; i++) {
					IClasspathEntry path = classpath[i];
					if (path.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
						IPath entryPath = path.getPath();
						File file = entryPath.toFile();
						if (file.exists()) {
							paths.add(file.toURI().toURL());
						}
						else {
							// case for project relative links
							String projectName = entryPath.segment(0);
							IProject pathProject = root.getProject(projectName);
							covertPathToUrl(pathProject, paths, entryPath);
						}
					}
					else if (path.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						// add source path as well for non java resources
						IPath sourcePath = path.getPath();
						covertPathToUrl(project, paths, sourcePath);
						// add source output locations for different source
						// folders
						IPath sourceOutputPath = path.getOutputLocation();
						covertPathToUrl(project, paths, sourceOutputPath);
					}
				}
				// add all depending java projects
				for (IJavaProject p : JdtUtils.getAllDependingJavaProjects(jp)) {
					addClassPathUrls(p.getProject(), paths, resolvedProjects);
				}

				// get default output directory
				IPath outputPath = jp.getOutputLocation();
				covertPathToUrl(project, paths, outputPath);
			}
			else {
				for (IProject p : project.getReferencedProjects()) {
					addClassPathUrls(p, paths, resolvedProjects);
				}
			}
		}
		catch (Exception e) {
			// ignore
		}
	}

	private static void addUri(List<URL> paths, URI uri) throws MalformedURLException {
		File file = new File(uri);
		// If we keep the following check, non-existing output folders will never be used for the lifetime of
		// a resourceloader. this causes issues with clean projects with not-yet existing output folders.
		// if (file.exists()) {
		if (file.isDirectory()) {
			paths.add(new URL(uri.toString() + File.separator));
		}
		else {
			paths.add(uri.toURL());
		}
		// }
	}

	private static void covertPathToUrl(IProject project, List<URL> paths, IPath path) throws MalformedURLException {
		if (path != null && project != null && path.removeFirstSegments(1) != null
				&& project.findMember(path.removeFirstSegments(1)) != null) {

			URI uri = project.findMember(path.removeFirstSegments(1)).getRawLocationURI();

			if (uri != null) {
				String scheme = uri.getScheme();
				if (FILE_SCHEME.equalsIgnoreCase(scheme)) {
					addUri(paths, uri);
				}
				else if ("sourcecontrol".equals(scheme)) {
					// special case of Rational Team Concert
					IPath sourceControlPath = project.findMember(path.removeFirstSegments(1)).getLocation();
					File sourceControlFile = sourceControlPath.toFile();
					if (sourceControlFile.exists()) {
						addUri(paths, sourceControlFile.toURI());
					}
				}
				else {
					IPathVariableManager variableManager = ResourcesPlugin.getWorkspace().getPathVariableManager();
					addUri(paths, variableManager.resolveURI(uri));
				}
			}
		}
	}

	private static ResourceLoader findResourceLoaderInCache(IProject project, ResourceLoader parentResourceLoader) {
		synchronized (RESOURCELOADER_CACHE) {
			for (int i = RESOURCELOADER_CACHE.size() - 1; i >= 0; i--) {
				ResourceLoaderCacheEntry entry = (ResourceLoaderCacheEntry) RESOURCELOADER_CACHE.get(i);
				IProject curr = entry.getProject();
				if (curr == null || !curr.exists() || !curr.isAccessible() || !curr.isOpen()) {
					removeResourceLoaderEntryFromCache(entry);
					if (DEBUG_RESOURCELOADER) {
						System.out.println(String.format("> removing resourceloader for '%s' : total %s",
								entry.getProject(), RESOURCELOADER_CACHE.size()));
					}
				}
				else {
					if (entry.matches(project, parentResourceLoader)) {
						entry.markAsAccessed();
						return entry.getResourceLoader();
					}
				}
			}
		}
		return null;
	}

	/**
	 * Iterates all class path entries of the given <code>project</code> and all depending projects.
	 * <p>
	 * Note: if <code>useParentResourceLoader</code> is true, the Spring, AspectJ, Commons Logging and ASM bundles are
	 * automatically added to the paths.
	 * @param project the {@link IProject}
	 * @param useParentResourceLoader use the OSGi resourceloader as parent
	 * @return a set of {@link URL}s that can be used to construct a {@link URLResourceLoader}
	 */
	public static List<URL> getClassPathUrls(IProject project, ResourceLoader parentResourceLoader) {

		// needs to be linked to preserve ordering
		List<URL> paths = new ArrayList<URL>();
		Set<IProject> resolvedProjects = new HashSet<IProject>();
		addClassPathUrls(project, paths, resolvedProjects);

		return paths;
	}

	/**
	 * Registers internal listeners that listen to changes relevant to clear out stale cache entries.
	 */
	private static void registerListenersIfRequired() {
		if (resourceChangeListener == null) {
			resourceChangeListener = new SourceAndOutputLocationResourceChangeListener();
			ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener);
		}
	}

	/**
	 * Removes the given {@link ResourceLoaderCacheEntry} from the internal cache.
	 * @param entry the entry to remove
	 */
	private static void removeResourceLoaderEntryFromCache(ResourceLoaderCacheEntry entry) {
		synchronized (RESOURCELOADER_CACHE) {
			if (DEBUG_RESOURCELOADER) {
				System.out.println(String.format("> removing resourceloader for '%s' : total %s", entry.getProject()
						.getName(), RESOURCELOADER_CACHE.size()));
			}
			entry.dispose();
			RESOURCELOADER_CACHE.remove(entry);
		}
	}

	public static boolean shouldFilter(String name) {
		if ("commons-logging.properties".equals(name)) return true;
		if (name != null && name.startsWith("META-INF/services/")) {
			return (name.indexOf('/', 18) == -1
					&& !name.startsWith("org.springframework", 18));
		}
		return false;
	}

	/**
	 * Returns a {@link ResourceLoader} for the given project.
	 */
	public static ResourceLoader getResourceLoader(IProject project, ResourceLoader parentResourceLoader) {
		synchronized (ProjectResourceLoaderCache.class) {
			// Setup the root class loader to be used when no explicit parent class loader is given
			if (parentResourceLoader == null) {
				parentResourceLoader = ResourceLoader.NULL;
			}
			if (project == null) {
				return parentResourceLoader;
			}

			registerListenersIfRequired();
		}

		ResourceLoader classLoader = findResourceLoaderInCache(project, parentResourceLoader);
		if (classLoader == null) {
			List<URL> urls = getClassPathUrls(project, parentResourceLoader);
			classLoader = addResourceLoaderToCache(project, urls, parentResourceLoader);
			if (DEBUG_RESOURCELOADER) {
				System.out.println(String.format("> creating new resourceloader for '%s' with parent '%s' : total %s",
						project.getName(), parentResourceLoader, RESOURCELOADER_CACHE.size()));
			}
		}
		return classLoader;
	}
	
	/**
	 * Removes any cached {@link ResourceLoaderCacheEntry} for the given {@link IProject}.
	 * @param project the project to remove {@link ResourceLoaderCacheEntry} for
	 */
	protected static void removeResourceLoaderEntryFromCache(IProject project) {
		synchronized (RESOURCELOADER_CACHE) {
			if (DEBUG_RESOURCELOADER) {
				System.out.println(String.format("> removing resourceloader for '%s' : total %s", project.getName(),
						RESOURCELOADER_CACHE.size()));
			}
			for (ResourceLoaderCacheEntry entry : new ArrayList<ResourceLoaderCacheEntry>(RESOURCELOADER_CACHE)) {
				if (project.equals(entry.getProject())) {
					entry.dispose();
					RESOURCELOADER_CACHE.remove(entry);
				}
			}
		}
	}
	
	/**
	 * Internal cache entry
	 */
	static class ResourceLoaderCacheEntry implements IElementChangedListener {

		private URL[] directories;

		private ResourceLoader jarResourceLoader;

		private long lastAccess;

		private ResourceLoader parentResourceLoader;

		private IProject project;

		private URL[] urls;

		public ResourceLoaderCacheEntry(IProject project, List<URL> urls, ResourceLoader parentResourceLoader) {
			this.project = project;
			this.urls = urls.toArray(new URL[urls.size()]);
			this.parentResourceLoader = parentResourceLoader;
			markAsAccessed();
			JavaCore.addElementChangedListener(this, ElementChangedEvent.POST_CHANGE);
		}

		public void dispose() {
			JavaCore.removeElementChangedListener(this);
			this.urls = null;
			this.jarResourceLoader = null;
		}

		public void elementChanged(ElementChangedEvent event) {
			IJavaProject javaProject = JdtUtils.getJavaProject(project);
			if (javaProject != null) {
				for (IJavaElementDelta delta : event.getDelta().getAffectedChildren()) {
					if ((delta.getFlags() & IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED) != 0
							|| (delta.getFlags() & IJavaElementDelta.F_CLASSPATH_CHANGED) != 0) {
						if (javaProject.equals(delta.getElement()) || javaProject.isOnClasspath(delta.getElement())) {
							removeResourceLoaderEntryFromCache(this);
						}
					}
				}
			}
		}

		public ResourceLoader getResourceLoader() {
			ResourceLoader parent = getJarResourceLoader();
			return new FilteringURLResourceLoader(directories, parent);
		}

		public long getLastAccess() {
			return lastAccess;
		}

		public IProject getProject() {
			return this.project;
		}

		public void markAsAccessed() {
			lastAccess = System.currentTimeMillis();
		}

		public boolean matches(IProject project, ResourceLoader parentResourceLoader) {
			return this.project.equals(project)
					&& ((parentResourceLoader == null && this.parentResourceLoader == null) || (parentResourceLoader != null && parentResourceLoader
							.equals(this.parentResourceLoader)));
		}

		private synchronized ResourceLoader getJarResourceLoader() {
			if (jarResourceLoader == null) {
				Set<URL> jars = new LinkedHashSet<URL>();
				List<URL> dirs = new ArrayList<URL>();
				for (URL url : urls) {
					if (shouldLoadFromParent(url)) {
						jars.add(url);
					}
					else {
						dirs.add(url);
					}
				}
				if (parentResourceLoader == null) {
					parentResourceLoader = ResourceLoader.NULL;
				}
				jarResourceLoader = new FilteringURLResourceLoader((URL[]) jars.toArray(new URL[jars.size()]),
						parentResourceLoader);
				directories = dirs.toArray(new URL[dirs.size()]);
			}
			return jarResourceLoader;
		}

		private boolean shouldLoadFromParent(URL url) {
			String path = url.getPath();
			if (path.endsWith(".jar") || path.endsWith(".zip")) {
				return true;
			}
			else if (path.contains("/org.eclipse.osgi/bundles/")) {
				return true;
			}
			return false;
		}
	}
	
	/**
	 * {@link IResourceChangeListener} to clear the cache whenever new source or output folders are being added.
	 * @since 2.5.2
	 */
	static class SourceAndOutputLocationResourceChangeListener implements IResourceChangeListener {

		private static final int VISITOR_FLAGS = IResourceDelta.ADDED | IResourceDelta.CHANGED;

		/**
		 * {@inheritDoc}
		 */
		public void resourceChanged(IResourceChangeEvent event) {
			if (event.getSource() instanceof IWorkspace) {
				int eventType = event.getType();
				switch (eventType) {
				case IResourceChangeEvent.POST_CHANGE:
					IResourceDelta delta = event.getDelta();
					if (delta != null) {
						try {
							delta.accept(getVisitor(), VISITOR_FLAGS);
						}
						catch (CoreException e) {
							SpringXmlNamespacesPlugin.log("Error while traversing resource change delta", e);
						}
					}
					break;
				}
			}
			else if (event.getSource() instanceof IProject) {
				int eventType = event.getType();
				switch (eventType) {
				case IResourceChangeEvent.POST_CHANGE:
					IResourceDelta delta = event.getDelta();
					if (delta != null) {
						try {
							delta.accept(getVisitor(), VISITOR_FLAGS);
						}
						catch (CoreException e) {
							SpringXmlNamespacesPlugin.log("Error while traversing resource change delta", e);
						}
					}
					break;
				}
			}

		}

		protected IResourceDeltaVisitor getVisitor() {
			return new SourceAndOutputLocationResourceVisitor();
		}

		/**
		 * Internal resource delta visitor.
		 */
		protected class SourceAndOutputLocationResourceVisitor implements IResourceDeltaVisitor {

			public final boolean visit(IResourceDelta delta) throws CoreException {
				IResource resource = delta.getResource();
				switch (delta.getKind()) {
				case IResourceDelta.ADDED:
					return resourceAdded(resource);
				}
				return true;
			}

			protected boolean resourceAdded(IResource resource) {
				if (resource instanceof IFolder && JdtUtils.isJavaProject(resource)) {
					try {
						IJavaProject javaProject = JdtUtils.getJavaProject(resource);
						// Safe guard once again
						if (javaProject == null) {
							return false;
						}

						// Check the default output location
						if (javaProject.getOutputLocation() != null
								&& javaProject.getOutputLocation().equals(resource.getFullPath())) {
							removeResourceLoaderEntryFromCache(resource.getProject());
							return false;
						}

						// Check any source and output folder location
						for (IClasspathEntry entry : javaProject.getRawClasspath()) {
							if (resource.getFullPath() != null && resource.getFullPath().equals(entry.getPath())) {
								removeResourceLoaderEntryFromCache(resource.getProject());
								return false;
							}
							else if (resource.getFullPath() != null
									&& resource.getFullPath().equals(entry.getOutputLocation())) {
								removeResourceLoaderEntryFromCache(resource.getProject());
								return false;
							}
						}
					}
					catch (JavaModelException e) {
						SpringXmlNamespacesPlugin.log("Error traversing resource change delta", e);
					}
				}
				return true;
			}
		}
	}

}
