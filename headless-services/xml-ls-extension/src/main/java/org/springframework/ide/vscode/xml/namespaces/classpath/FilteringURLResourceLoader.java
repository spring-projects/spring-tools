package org.springframework.ide.vscode.xml.namespaces.classpath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

public class FilteringURLResourceLoader extends ResourceLoader {
	
	private static Logger LOGGER = Logger.getLogger(FilteringURLResourceLoader.class.getName());

	
//	private static Map<String, AtomicLong> jarScanned = Collections.synchronizedMap(new HashMap<String, AtomicLong>());
	
//	private static final boolean DEBUG = false;
	
	private static Set<String> _fetchedResources = Collections.synchronizedSet(new HashSet<>());
	private static ImmutableSet<String> fetchedResources = ImmutableSet.copyOf(_fetchedResources);
	
	private URL[] urls;
	private ResourceLoader parent;
	
	private Set<String> _indexValidFor = ImmutableSet.of();
	private ImmutableSetMultimap<String, String> resourcesIndex = null;


	public FilteringURLResourceLoader(URL[] directories, ResourceLoader parent) {
		this.urls = directories;
		this.parent = parent == null ? ResourceLoader.NULL : parent;
	}
	
	private static AtomicLong indexBuilt = new AtomicLong();
	private static AtomicLong indexReused = new AtomicLong();
	
	
	
//	@Override
//	public URL getResource(String resourceName) {
//		try {
//			if (!shouldFilter(resourceName)) {
//				URL fromParent = parent.getResource(resourceName);
//				if (fromParent != null) {
//					return fromParent;
//				}
//				Collection<String> resources = getResourcesCollection(resourceName);
//				if (!resources.isEmpty()) {
//					return new URL(resources.iterator().next());
//				}
//			}
//		} catch (Exception e) {
//			SpringXmlNamespacesPlugin.log(e);
//		}
//		return null;
//	}
//
	/**
	 * Get's collection of resources from this resource loader, but excluding resources
	 * from the parent.
	 */
	private Collection<String> getResourcesCollection(String resourceName) {
		if (!shouldFilter(resourceName)) {
//			long start = System.currentTimeMillis();
			try {
				ensureIndexed(resourceName);
				Collection<String> r = resourcesIndex.get(resourceName);
				return r != null ? r : ImmutableList.of(); 
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, e, null);
			}
		}
		return ImmutableSet.of();
	}

	private synchronized void ensureIndexed(String resourceName) {
		if (resourcesIndex != null && isIndexValidFor(resourceName)) {
			indexReused.incrementAndGet();
		} else {
			indexBuilt.incrementAndGet();
			synchronized (_fetchedResources) {
				if (_fetchedResources.add(resourceName)) {
					fetchedResources = ImmutableSet.copyOf(_fetchedResources);
//					save(_fetchedResources);
				}
			}
			resourcesIndex = buildIndex(name -> isInterestingByDefault(name) || fetchedResources.contains(name));
			_indexValidFor = fetchedResources;
		}
	}

	private boolean isIndexValidFor(String resourceName) {
		return isInterestingByDefault(resourceName) || _indexValidFor.contains(resourceName);
	}

	/**
	 * If you can predict, based on looking at a resourceName that it is likely to
	 * be 'interesting' for future lookups, then this method can be overridden so it returns true
	 * for those 'interesting' resources. This will allow the resource-loader to pre-cache
	 * the interesting values from the get-go, and thereby avoid rebuilding the
	 * index multiple times.
	 * <p>
	 * The default implementation provided here is optimised specifically for resolving 
	 * spring .xsd schemas. 
	 */
	protected boolean isInterestingByDefault(String resourceName) {
		return resourceName.startsWith("META-INF/spring") || resourceName.endsWith(".xsd");
	}

	private ImmutableSetMultimap<String, String> buildIndex(Predicate<String> interestingResourceNames) {
		ImmutableSetMultimap.Builder<String, String> resources = ImmutableSetMultimap.builder();
		//find in our urls
		for (URL url : urls) {
			try {
				if (isZip(url)) {
					fetchResourceFromZip(interestingResourceNames, url, resources);
				} else {
					url.getProtocol().equals("file");
					File file = Paths.get(url.toURI()).toFile();
					if (file.isDirectory()) {
						fetchResourceFromDirectory(interestingResourceNames, file, resources);
					} else {
						fetchResourceFromZip(interestingResourceNames, url, resources);
					}
				}
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, e, null);
			}
		}
		return resources.build();
	}

	private void fetchResourceFromDirectory(Predicate<String> interesttingResourceNames, 
			File file, 
			ImmutableSetMultimap.Builder<String, String> resources
	) {
		try {
			Path rootDir = file.toPath();
			FileVisitor<Path> visitor = new FileVisitor<Path>() {
				
				final FileVisitResult fvr = FileVisitResult.CONTINUE;
	
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return fvr;
				}
	
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (attrs.isRegularFile()) {
						String name = rootDir.relativize(file).toString();
						if (interesttingResourceNames.test(name)) {
							resources.put(name, file.toUri().toASCIIString());
						}
					}
					return fvr;
				}
	
				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return fvr;
				}
	
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return fvr;
				}
			};
			Files.walkFileTree(rootDir, visitor);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e, null);
		}
	}

	private void fetchResourceFromZip(Predicate<String> interestingResourceNames, URL url, ImmutableSetMultimap.Builder<String, String> requestor) {
//		AtomicLong counter = jarScanned.computeIfAbsent(url.toString(), s -> new AtomicLong());
//		counter.incrementAndGet();
		try {
			try (InputStream input = url.openStream()) {
				ZipInputStream zip = new ZipInputStream(input);
				ZipEntry ze = zip.getNextEntry();
				while (ze != null) {
					String resourceName = ze.getName();
					if (interestingResourceNames.test(resourceName)) {
						//Example url: jar:file:/home/kdvolder/.m2/repository/org/springframework/boot/spring-boot/2.1.4.RELEASE/spring-boot-2.1.4.RELEASE.jar!/META-INF/spring.factories
						requestor.put(resourceName, "jar:"+url+"!/"+ze);
					}
					ze = zip.getNextEntry();
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, e, null);
		}
	}

	private boolean isZip(URL url) {
		String path = url.getPath();
		return path.endsWith(".jar") || path.endsWith(".zip");
	}

	@Override
	public Stream<URL> getResources(String resourceName) {
		if (!shouldFilter(resourceName)) {
			Stream<URL> localResources = 
					getResourcesCollection(resourceName).stream()
					.map(resource -> {
						try {
							return new URL(resource);
						} catch (MalformedURLException e) {
							throw new RuntimeException(e);
						}
					});
			return Stream.concat(parent.getResources(resourceName), localResources);
		}
		return Stream.of();
	}
	
	public static boolean shouldFilter(String name) {
		if ("commons-logging.properties".equals(name)) return true;
		if (name != null && name.startsWith("META-INF/services/")) {
			return (name.indexOf('/', 18) == -1
					&& !name.startsWith("org.springframework", 18));
		}
		return false;
	}

}
