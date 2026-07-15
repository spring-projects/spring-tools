package org.springframework.ide.vscode.xml.namespaces;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.xml.IJavaProjectProvider;
import org.springframework.ide.vscode.xml.IJavaProjectProvider.IJavaProjectData;
import org.springframework.ide.vscode.xml.namespaces.classpath.ProjectResourceLoaderCache;
import org.springframework.ide.vscode.xml.namespaces.util.DocumentAccessor;
import org.springframework.ide.vscode.xml.namespaces.util.DocumentAccessor.SchemaLocations;
import org.springframework.ide.vscode.xml.namespaces.util.XmlUtils;
import org.w3c.dom.Document;

import org.springframework.ide.vscode.xml.namespaces.util.Logger;

public class ProjectClasspathUriResolverExtension implements URIResolverExtension {
	
	private IJavaProjectProvider javaProjectProvider;
	
	private ConcurrentMap<IJavaProjectData, Future<ProjectClasspathUriResolver>> projectResolvers = 
			new ConcurrentHashMap<IJavaProjectData, Future<ProjectClasspathUriResolver>>();

	private ProjectResourceLoaderCache loaderCache;
	
	final private Consumer<IJavaProjectData> projectListener = project -> {
		projectResolvers.remove(project);

		List<String> projectSources = project.getClasspath().getEntries().stream()
				.filter(cpe -> Classpath.isProjectSource(cpe))
				.map(cpe -> cpe.getPath())
				.collect(Collectors.toList());

		for (IJavaProjectData javaProject : projectResolvers.keySet()) {
			if (javaProject != null) {
				for (CPE cpe : javaProject.getClasspath().getEntries()) {
					if (Classpath.isSource(cpe) && !cpe.isOwn() && !cpe.isSystem() && !cpe.isTest()) {
						if (projectSources.contains(cpe.getPath())) {
							projectResolvers.remove(javaProject);
							break;
						}
					}
				}
			}
		}
	};

	public ProjectClasspathUriResolverExtension(IJavaProjectProvider javaProjectProvider, ProjectResourceLoaderCache loaderCache) {
		this.javaProjectProvider = javaProjectProvider;
		this.loaderCache = loaderCache;
		
		javaProjectProvider.addListener(projectListener);
	}
	
	@Override
	public String resolve(String file, String publicId, String systemId) {
		Logger.DEFAULT.log("Resolve XML from classpath.");
		Logger.DEFAULT.log("BaseLocation=" + file + " publicId=" + publicId + " systemId=" + systemId);
		
		// systemId is already resolved; so don't touch
		if (systemId != null && systemId.startsWith("jar:")) {
			return null;
		}
					
		// identify the correct project
		IJavaProjectData project = null;
		
		if (file != null) {
			if (file.startsWith(ProjectAwareUrlStreamHandlerFactory.PROJECT_AWARE_PROTOCOL_HEADER)) {
				String nameAndLocation = file
						.substring(ProjectAwareUrlStreamHandlerFactory.PROJECT_AWARE_PROTOCOL_HEADER
								.length());
				String projectName = nameAndLocation.substring(0, nameAndLocation.indexOf('/'));
				project = javaProjectProvider.get(projectName);
			} else {
				project = getBestMatchingProject(file);
			}
		}
		
		if (project == null) {
			Logger.DEFAULT.log("Resolve XML from classpath failed. No project.");
			return null;
		}
		
		if (systemId == null && file != null) {
			systemId = findSystemIdFromFile(file, publicId);
		}

		if (systemId == null && publicId == null) {
			Logger.DEFAULT.log("Resolve XML from classpath failed. No systemId && publicId.");
			return null;
		}
		
		ProjectClasspathUriResolver resolver = getProjectResolver(file, project);
		if (resolver != null) {
			String resolved = resolver.resolveOnClasspath(publicId, systemId);
			if (resolved != null) {
				resolved = ProjectAwareUrlStreamHandlerFactory.createProjectAwareUrl(project.getName(), resolved);
			}
			Logger.DEFAULT.log("Resolve XML from classpath => "+resolved);
			return resolved;
		}

		Logger.DEFAULT.log("Resolve XML from classpath failed. End of method");
		return null;
	}
	
	private boolean isCachingDisabled() {
		// TODO Auto-generated method stub
		return false;
	}
	
	private ProjectClasspathUriResolver getProjectResolver(String fileUrl, final IJavaProjectData project) {

//		if (!XmlNamespaceUtils.useNamespacesFromClasspath(project)) {
//			return null;
//		}

		if (fileUrl != null && !fileUrl.endsWith(".xml") && !fileUrl.endsWith(".xsd")) {
			return null;
		}

		// Special case for 'pom.xml'. We can skip it entirely because it is not used to define spring beans.
		// Also... m2e apparantly causes this to be called directly from the UI thread causing major hangs / annoyance.
		// See: https://github.com/spring-projects/sts4/issues/318
		if (fileUrl != null && fileUrl.endsWith("pom.xml")) {
			return null;
		}

		while (true) {
			Future<ProjectClasspathUriResolver> future = projectResolvers.get(project);
			if (future == null) {
				
				Callable<ProjectClasspathUriResolver> createResolver = new Callable<ProjectClasspathUriResolver>() {
					public ProjectClasspathUriResolver call() throws InterruptedException {
						ProjectClasspathUriResolver resolver = new ProjectClasspathUriResolver(loaderCache, project, isCachingDisabled());						
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
	
	private IJavaProjectData getBestMatchingProject(String file) {
		try {
			String fileUri = new URL(file).toURI().toASCIIString();
			return javaProjectProvider.findProject(fileUri);
		} catch (MalformedURLException | URISyntaxException e) {
			throw new IllegalStateException(e); 
		}
	}

	private String findSystemIdFromFile(String file, String publicId) {
		InputStream contents = null;
		try {
			contents = new URL(file).openStream();
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory
					.newInstance();
			builderFactory.setValidating(false);
			builderFactory.setNamespaceAware(true);
			
			builderFactory.setFeature("http://xml.org/sax/features/validation", false);
			builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			XmlUtils.configureDocumentBuilderFactory(builderFactory);

			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document doc = builder.parse(contents);

			DocumentAccessor accessor = new DocumentAccessor();
			accessor.pushDocument(doc);
			SchemaLocations locations = accessor.getCurrentSchemaLocations();

			String location = locations.getSchemaLocation(publicId);
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

}
