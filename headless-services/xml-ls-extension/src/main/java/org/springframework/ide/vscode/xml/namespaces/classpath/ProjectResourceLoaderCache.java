package org.springframework.ide.vscode.xml.namespaces.classpath;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.xml.IJavaProjectProvider;
import org.springframework.ide.vscode.xml.IJavaProjectProvider.IJavaProjectData;

/**
 * Internal cache of classpath urls and corresponding resourceloaders.
 * @author Christian Dupuis
 * @author Martin Lippert
 * @since 2.2.5
 */
public class ProjectResourceLoaderCache {

	private static Logger LOGGER = Logger.getLogger(ProjectResourceLoaderCache.class.getName());

	private static final int CACHE_SIZE = 200;
	
	private final List<ResourceLoaderCacheEntry> resourceLoaderCache = new ArrayList<ResourceLoaderCacheEntry>(CACHE_SIZE);

//	private IResourceChangeListener resourceChangeListener = null;
	
	private IJavaProjectProvider javaProjectProvider;
	
	public ProjectResourceLoaderCache(IJavaProjectProvider javaProjectProvider) {
		this.javaProjectProvider = javaProjectProvider;
	}

	private ResourceLoader addResourceLoaderToCache(IJavaProjectData project, List<URL> urls, ResourceLoader parentResourceLoader) {
		synchronized (resourceLoaderCache) {
			int nEntries = resourceLoaderCache.size();
			if (nEntries >= CACHE_SIZE) {
				// find obsolete entries or remove entry that was least recently accessed
				ResourceLoaderCacheEntry oldest = null;
				List<ResourceLoaderCacheEntry> obsoleteResourceLoaders = new ArrayList<ResourceLoaderCacheEntry>(CACHE_SIZE);
				for (int i = 0; i < nEntries; i++) {
					ResourceLoaderCacheEntry entry = (ResourceLoaderCacheEntry) resourceLoaderCache.get(i);
					IJavaProjectData curr = entry.getProject();
					if (javaProjectProvider.exists(curr.getName())) {
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
			resourceLoaderCache.add(newEntry);
			return newEntry.getResourceLoader();
		}
	}

	/**
	 * Add {@link URL}s to the given set of <code>paths</code>.
	 */
	private void addClassPathUrls(IJavaProjectData project, List<URL> paths, Set<IJavaProjectData> resolvedProjects) {

		// add project to local cache to prevent adding its classpaths multiple times
		if (resolvedProjects.contains(project)) {
			return;
		} else {
			resolvedProjects.add(project);
		}

		// configured classpath
		Classpath classpath = project.getClasspath();

		// add class path entries
		for (CPE cpe : classpath.getEntries()) {
			try {
				if (Classpath.isBinary(cpe)) {
					addFile(paths, new File(cpe.getPath()));
				} else if (Classpath.isSource(cpe)) {
					addFile(paths, new File(cpe.getPath()));
					addFile(paths, new File(cpe.getOutputFolder()));
				}
			} catch (MalformedURLException e) {
				LOGGER.log(Level.SEVERE, e, null);
			}
		}
	}

	private static void addFile(List<URL> paths, File file) throws MalformedURLException {
		if (file.exists()) {
			if (file.isDirectory()) {
				paths.add(new URL(file.toURI().toASCIIString() + File.separator));
			}
			else {
				paths.add(file.toURI().toURL());
			}
		} else {
			LOGGER.log(Level.WARNING, "Classpath entry '" + file + "' does not exist!");
		}
	}

	private ResourceLoader findResourceLoaderInCache(IJavaProjectData project, ResourceLoader parentResourceLoader) {
		synchronized (resourceLoaderCache) {
			for (int i = resourceLoaderCache.size() - 1; i >= 0; i--) {
				ResourceLoaderCacheEntry entry = (ResourceLoaderCacheEntry) resourceLoaderCache.get(i);
				IJavaProjectData curr = entry.getProject();
				if (javaProjectProvider.exists(curr.getName())) {
					removeResourceLoaderEntryFromCache(entry);
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
	public List<URL> getClassPathUrls(IJavaProjectData project, ResourceLoader parentResourceLoader) {

		// needs to be linked to preserve ordering
		List<URL> paths = new ArrayList<URL>();
		Set<IJavaProjectData> resolvedProjects = new HashSet<IJavaProjectData>();
		addClassPathUrls(project, paths, resolvedProjects);

		return paths;
	}

//	/**
//	 * Registers internal listeners that listen to changes relevant to clear out stale cache entries.
//	 */
//	private void registerListenersIfRequired() {
//		if (resourceChangeListener == null) {
//			resourceChangeListener = new SourceAndOutputLocationResourceChangeListener();
//			ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener);
//		}
//	}

	/**
	 * Removes the given {@link ResourceLoaderCacheEntry} from the internal cache.
	 * @param entry the entry to remove
	 */
	private void removeResourceLoaderEntryFromCache(ResourceLoaderCacheEntry entry) {
		synchronized (resourceLoaderCache) {
			entry.dispose();
			resourceLoaderCache.remove(entry);
		}
	}

	/**
	 * Returns a {@link ResourceLoader} for the given project.
	 */
	public ResourceLoader getResourceLoader(IJavaProjectData project, ResourceLoader parentResourceLoader) {
		synchronized (ProjectResourceLoaderCache.class) {
			// Setup the root class loader to be used when no explicit parent class loader is given
			if (parentResourceLoader == null) {
				parentResourceLoader = ResourceLoader.NULL;
			}
			if (project == null) {
				return parentResourceLoader;
			}

//			registerListenersIfRequired();
		}

		ResourceLoader classLoader = findResourceLoaderInCache(project, parentResourceLoader);
		if (classLoader == null) {
			List<URL> urls = getClassPathUrls(project, parentResourceLoader);
			classLoader = addResourceLoaderToCache(project, urls, parentResourceLoader);
		}
		return classLoader;
	}
	
	/**
	 * Removes any cached {@link ResourceLoaderCacheEntry} for the given {@link IProject}.
	 * @param project the project to remove {@link ResourceLoaderCacheEntry} for
	 */
	protected void removeResourceLoaderEntryFromCache(IJavaProjectData project) {
		synchronized (resourceLoaderCache) {
			for (ResourceLoaderCacheEntry entry : new ArrayList<ResourceLoaderCacheEntry>(resourceLoaderCache)) {
				if (project.equals(entry.getProject())) {
					entry.dispose();
					resourceLoaderCache.remove(entry);
				}
			}
		}
	}
	
	/**
	 * Internal cache entry
	 */
	class ResourceLoaderCacheEntry /*implements IElementChangedListener*/ {

		private URL[] directories;

		private ResourceLoader jarResourceLoader;

		private long lastAccess;

		private ResourceLoader parentResourceLoader;

		private IJavaProjectData project;

		private URL[] urls;

		public ResourceLoaderCacheEntry(IJavaProjectData project, List<URL> urls, ResourceLoader parentResourceLoader) {
			this.project = project;
			this.urls = urls.toArray(new URL[urls.size()]);
			this.parentResourceLoader = parentResourceLoader;
			markAsAccessed();
		}

		public void dispose() {
			this.urls = null;
			this.jarResourceLoader = null;
		}

//		public void elementChanged(ElementChangedEvent event) {
//			IJavaProject javaProject = JdtUtils.getJavaProject(project);
//			if (javaProject != null) {
//				for (IJavaElementDelta delta : event.getDelta().getAffectedChildren()) {
//					if ((delta.getFlags() & IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED) != 0
//							|| (delta.getFlags() & IJavaElementDelta.F_CLASSPATH_CHANGED) != 0) {
//						if (javaProject.equals(delta.getElement()) || javaProject.isOnClasspath(delta.getElement())) {
//							removeResourceLoaderEntryFromCache(this);
//						}
//					}
//				}
//			}
//		}

		public ResourceLoader getResourceLoader() {
			ResourceLoader parent = getJarResourceLoader();
			return new FilteringURLResourceLoader(directories, parent);
		}

		public long getLastAccess() {
			return lastAccess;
		}

		public IJavaProjectData getProject() {
			return this.project;
		}

		public void markAsAccessed() {
			lastAccess = System.currentTimeMillis();
		}

		public boolean matches(IJavaProjectData project, ResourceLoader parentResourceLoader) {
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
	
//	/**
//	 * {@link IResourceChangeListener} to clear the cache whenever new source or output folders are being added.
//	 * @since 2.5.2
//	 */
//	static class SourceAndOutputLocationResourceChangeListener implements IResourceChangeListener {
//
//		private static final int VISITOR_FLAGS = IResourceDelta.ADDED | IResourceDelta.CHANGED;
//
//		/**
//		 * {@inheritDoc}
//		 */
//		public void resourceChanged(IResourceChangeEvent event) {
//			if (event.getSource() instanceof IWorkspace) {
//				int eventType = event.getType();
//				switch (eventType) {
//				case IResourceChangeEvent.POST_CHANGE:
//					IResourceDelta delta = event.getDelta();
//					if (delta != null) {
//						try {
//							delta.accept(getVisitor(), VISITOR_FLAGS);
//						}
//						catch (CoreException e) {
//							SpringXmlNamespacesPlugin.log("Error while traversing resource change delta", e);
//						}
//					}
//					break;
//				}
//			}
//			else if (event.getSource() instanceof IProject) {
//				int eventType = event.getType();
//				switch (eventType) {
//				case IResourceChangeEvent.POST_CHANGE:
//					IResourceDelta delta = event.getDelta();
//					if (delta != null) {
//						try {
//							delta.accept(getVisitor(), VISITOR_FLAGS);
//						}
//						catch (CoreException e) {
//							SpringXmlNamespacesPlugin.log("Error while traversing resource change delta", e);
//						}
//					}
//					break;
//				}
//			}
//
//		}
//
//		protected IResourceDeltaVisitor getVisitor() {
//			return new SourceAndOutputLocationResourceVisitor();
//		}
//
//		/**
//		 * Internal resource delta visitor.
//		 */
//		protected class SourceAndOutputLocationResourceVisitor implements IResourceDeltaVisitor {
//
//			public final boolean visit(IResourceDelta delta) throws CoreException {
//				IResource resource = delta.getResource();
//				switch (delta.getKind()) {
//				case IResourceDelta.ADDED:
//					return resourceAdded(resource);
//				}
//				return true;
//			}
//
//			protected boolean resourceAdded(IResource resource) {
//				if (resource instanceof IFolder && JdtUtils.isJavaProject(resource)) {
//					try {
//						IJavaProject javaProject = JdtUtils.getJavaProject(resource);
//						// Safe guard once again
//						if (javaProject == null) {
//							return false;
//						}
//
//						// Check the default output location
//						if (javaProject.getOutputLocation() != null
//								&& javaProject.getOutputLocation().equals(resource.getFullPath())) {
//							removeResourceLoaderEntryFromCache(resource.getProject());
//							return false;
//						}
//
//						// Check any source and output folder location
//						for (IClasspathEntry entry : javaProject.getRawClasspath()) {
//							if (resource.getFullPath() != null && resource.getFullPath().equals(entry.getPath())) {
//								removeResourceLoaderEntryFromCache(resource.getProject());
//								return false;
//							}
//							else if (resource.getFullPath() != null
//									&& resource.getFullPath().equals(entry.getOutputLocation())) {
//								removeResourceLoaderEntryFromCache(resource.getProject());
//								return false;
//							}
//						}
//					}
//					catch (JavaModelException e) {
//						SpringXmlNamespacesPlugin.log("Error traversing resource change delta", e);
//					}
//				}
//				return true;
//			}
//		}
//		
//	}

}
