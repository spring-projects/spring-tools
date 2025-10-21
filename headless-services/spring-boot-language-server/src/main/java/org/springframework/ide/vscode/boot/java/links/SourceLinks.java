/*******************************************************************************
 * Copyright (c) 2018, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.links;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.commands.Misc;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.javadoc.TypeUrlProviderFromContainerUrl;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * Instance is able to provide client specific URL links to navigate to a
 * specific file on the client given various types of data such as fully
 * qualified java type name or classpath resource or just some file resource
 * given by its path.
 *
 * @author Alex Boyko
 *
 */
public interface SourceLinks {

	static final String WEB_INF_CLASSES = "/WEB-INF/classes/";

	static final Logger log = LoggerFactory.getLogger(SourceLinks.class);

	static final String JAR = ".jar";
	static final String CLASS = ".class";

	public static Optional<Path> sourceFromSourceFolder(String fqName, IClasspath classpath) {
		return IClasspathUtil.getSourceFolders(classpath)
			.map(sourceFolder -> {
				try {
					return sourceFolder.toURI().toURL();
				} catch (MalformedURLException e) {
					log.warn("Failed to convert source folder {} to URI {}", sourceFolder, fqName, e);
					return null;
				}
			})
			.map(url -> {
				try {
					return TypeUrlProviderFromContainerUrl.SOURCE_FOLDER_URL_SUPPLIER.url(url, fqName, /* module */ null);
				} catch (Exception e) {
					log.warn("Failed to determine source URL from url={} fqName=", url, fqName, e);
					return null;
				}
			})
			.map(url -> {
				try {
					// .toFile().toPath() to get on Win: file:
					return Paths.get(url.toURI());
				} catch (URISyntaxException e) {
					log.warn("Failed to convert URL {} to path. {}", url, fqName, e);
					return null;
				}
			})
			.filter(sourcePath -> sourcePath != null && Files.exists(sourcePath))
			.findFirst();
	}

	public static Optional<URI> source(IJavaProject project, String fqName) {
		// Try to find in a source JAR
		IJavaModuleData classpathResourceContainer = project.getIndex().findClasspathResourceContainer(fqName);
		if (classpathResourceContainer != null) {
			Optional<URI> uri = IClasspathUtil.sourceContainer(project.getClasspath(), classpathResourceContainer.getContainer()).map(file -> {
				try {
					return TypeUrlProviderFromContainerUrl.JAR_SOURCE_URL_PROVIDER.url(file, fqName, classpathResourceContainer.getModule()).toURI();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});

			if (!uri.isPresent()) {
				// Try Source folder
				uri = sourceFromSourceFolder(fqName, project.getClasspath()).map(p -> p.toUri());
			}
			return uri;
		}
		return Optional.empty();
	}

	static Optional<String> sourceLinkUrlForClasspathResourceOnTomcat(SourceLinks sourceLinks, JavaProjectFinder projectFinder, String path) {
		int indexOfWenInfClasses = path.indexOf(WEB_INF_CLASSES);
		String projectName = Paths.get(path.substring(0, indexOfWenInfClasses)).getFileName().toString();
		String fqName = path.substring(0, path.lastIndexOf(CLASS)).substring(indexOfWenInfClasses + WEB_INF_CLASSES.length()).replace('/', '.');
		for (IJavaProject p : projectFinder.all()) {
			if (projectName.equals(p.getElementName())) {
				return sourceLinks.sourceLinkUrlForFQName(p, fqName);
			}
		}
		return sourceLinks.sourceLinkUrlForFQName(null, fqName);
	}

	public static Optional<String> sourceLinkUrlForClasspathResource(SourceLinks sourceLinks, JavaProjectFinder projectFinder, String path) {
		if (projectFinder != null) {
			int idx = path.lastIndexOf(CLASS);
			if (idx >= 0) {
				int web_inf_index = path.indexOf(WEB_INF_CLASSES);
				if (web_inf_index >= 0) {
					return sourceLinkUrlForClasspathResourceOnTomcat(sourceLinks, projectFinder, path);
				}
				Path filePath = Paths.get(path.substring(0, idx));
				IJavaProject project = projectFinder.find(new TextDocumentIdentifier(filePath.toUri().toASCIIString())).orElse(null);
				if (project == null) {
					try {
						// URL for CF resources looks like jar:file:/home/vcap/app/lib/gs-rest-service-complete.jar!/hello/MyService.class
						// The above doesn't wotk with "filePath.toUri().toURL()"
						URL url = new URL(path.substring(0, idx));
						if (url.getProtocol().equals(Misc.JAR)) {
							URLConnection connection = url.openConnection();
							if (connection instanceof JarURLConnection) {
								JarURLConnection jarConnection = (JarURLConnection) connection;
								String fqName = jarConnection.getEntryName().replace(File.separator, ".");
								return sourceLinks.sourceLinkUrlForFQName(null, fqName);
							}
						}
					} catch (MalformedURLException e) {
						log.error("", e);
					} catch (IOException e) {
						log.error("", e);
					}
				} else {
					try {
						for (CPE cpe : project.getClasspath().getClasspathEntries()) {
							if (Classpath.isSource(cpe)) {
								Path cpeBinaryPath = IClasspathUtil.binaryLocation(cpe).toPath();
								if (filePath.startsWith(cpeBinaryPath)) {
									String fqName = cpeBinaryPath.relativize(filePath).toString().replace(File.separator, ".");
									Optional<String> link = sourceLinks.sourceLinkUrlForFQName(project, fqName);
									if (link.isPresent()) {
										return link;
									}
								}
							}
						}
					} catch (Exception e) {
						log.error("", e);
					}
				}

			}
		}
		return Optional.empty();
	}


	/**
	 * Creates link to source file defining the type passed with it's fully qualified name
	 * @param project Java project in the context of which source file link is calculated
	 * @param fqName type's fully qualified name
	 * @return the link URL optional
	 */
	Optional<String> sourceLinkUrlForFQName(IJavaProject project, String fqName);

	/**
	 * Creates link to source file corresponding to a classpath resource
	 * @param project project Java project in the context of which source file link is calculated
	 * @param path the path to the classpath resource
	 * @return the link URL optional
	 */
	Optional<String> sourceLinkUrlForClasspathResource(String path);

	/**
	 * Creates link to a file specified by it's path
	 * @param path the resource path
	 * @return the link URL optional
	 */
	Optional<String> sourceLinkForResourcePath(Path path);
	
	/**
	 * From URI produces a source link compatible with the client
	 * @param contextProject
	 * @param uri
	 * @return
	 */
	Optional<URI> sourceLinkForJarEntry(IJavaProject contextProject, URI uri);
}
