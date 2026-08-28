/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.xml.namespaces.classpath;

import static org.springframework.ide.eclipse.xml.namespaces.classpath.ProjectResourceLoaderCache.shouldFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.core.runtime.URIUtil;
import org.springframework.ide.eclipse.xml.namespaces.SpringXmlNamespacesPlugin;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

import reactor.core.publisher.Flux;

public class FilteringURLResourceLoader extends ResourceLoader {
	
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
	
	private static AtomicLong timeUsed = new AtomicLong();
	private static AtomicLong request = new AtomicLong();

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
				SpringXmlNamespacesPlugin.log(e);
//			} finally {
//				long duration = System.currentTimeMillis() - start;
//				long total = timeUsed.addAndGet(duration);
//				long requestCount = request.incrementAndGet();
//				System.out.println("Time spent finding resources:");
//				System.out.println("  requests = " + requestCount);
//				System.out.println("  avg      = " + total / requestCount);
//				System.out.println("  total    = " + total);
//				System.out.println("  index built/reused    = " + indexBuilt.get() +" / "+indexReused.get());

//				System.out.println("Jar scan counts:");
//				for (Entry<String, AtomicLong> e : jarScanned.entrySet()) {
//					System.out.println("   "+e.getValue().get() +": "+e.getKey());
//				}
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
					File file = URIUtil.toFile(URIUtil.toURI(url));
					if (file.isDirectory()) {
						fetchResourceFromDirectory(interestingResourceNames, file, resources);
					} else {
						fetchResourceFromZip(interestingResourceNames, url, resources);
					}
				}
			} catch (Exception e) {
				SpringXmlNamespacesPlugin.log(e);
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
							resources.put(name, file.toUri().toString());
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
			SpringXmlNamespacesPlugin.log(e);
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
//						System.out.println("FOUND "+resourceName+" in "+url);
						requestor.put(resourceName, "jar:"+url+"!/"+ze);
//					} else {
//						System.out.println("mismatch: "+ze.getName());
					}
					ze = zip.getNextEntry();
				}
			}
		} catch (Exception e) {
			SpringXmlNamespacesPlugin.log(e);
		}
	}

	private boolean isZip(URL url) {
		String path = url.getPath();
		return path.endsWith(".jar") || path.endsWith(".zip");
	}

	@Override
	public Flux<URL> getResources(String resourceName) {
		if (!shouldFilter(resourceName)) {
			Flux<URL> localResources = 
					Flux.defer(() -> Flux.fromIterable(getResourcesCollection(resourceName)))
					.map(resource -> {
						try {
							return new URL(resource);
						} catch (MalformedURLException e) {
							throw new RuntimeException(e);
						}
					});
			return parent.getResources(resourceName)
					.concatWith(localResources);
		}
		return Flux.empty();
	}
}
