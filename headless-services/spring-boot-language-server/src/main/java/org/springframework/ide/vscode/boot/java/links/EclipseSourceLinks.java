/*******************************************************************************
 * Copyright (c) 2018, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.links;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IMember;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;

/**
 * Source links for Eclipse client. Eclipse IntroURLs.
 *
 * @author Alex Boyko
 *
 */
public class EclipseSourceLinks extends AbstractSourceLinks {

	private static final String URL_PREFIX = "http://org.eclipse.ui.intro/execute?command=";
	private static final String EQUALS = "=";
	private static final String PARAMETERS_SEPARATOR = ",";
	private static final String PARAMETERS_START = "(";
	private static final String PARAMETERS_END = ")";

	private static final String JAVA_ELEMENT_COMMAND = "org.springframework.tooling.ls.eclipse.commons.commands.OpenJavaElementInEditor";
	private static final String BINDING_KEY_PARAMETER_ID = "bindingKey";
	private static final String PROJECT_NAME_PARAMETER_ID = "projectName";

	private static final String RESOURCE_COMMAND = "org.springframework.tooling.ls.eclipse.commons.commands.OpenResourceInEditor";
	private static final String PATH = "path";

	private static final String JAR_ENTRY_COMMAND = "org.springframework.tooling.ls.eclipse.commons.commands.OpenJarEntryInEditor";
	private static final String JAR_URI_PARAM = "jarUri";

	private static final Logger log = LoggerFactory.getLogger(EclipseSourceLinks.class);

	private JavaProjectFinder projectFinder;

	public EclipseSourceLinks(JavaProjectFinder projectFinder) {
		this.projectFinder = projectFinder;
	}

	@Override
	public Optional<String> sourceLinkUrlForFQName(IJavaProject project, String fqName) {
		return findProjectForFQName(project, fqName).map(p -> eclipseIntroUri(p, fqName)).map(uri -> uri.toASCIIString());
	}

	private Optional<IJavaProject> findProjectForFQName(IJavaProject project, String fqName) {
		if (project != null && project.getIndex().findType(fqName) != null) {
			return Optional.of(project);
		} else {
			for (IJavaProject jp : projectFinder.all()) {
				if (jp != project) {
					if (jp.getIndex().findType(fqName) != null) {
						return Optional.of(jp);
					}
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<String> sourceLinkUrlForClasspathResource(String path) {
		return SourceLinks.sourceLinkUrlForClasspathResource(this, projectFinder, path);
	}

	@Override
	public Optional<String> sourceLinkForResourcePath(Path path) {
		return Optional.ofNullable(eclipseIntroUri(path)).map(uri -> uri.toASCIIString());
	}

	public static URI eclipseIntroUri(Path path) {
		if (path != null) {
			try {
				StringBuilder paramBuilder = new StringBuilder(RESOURCE_COMMAND);
				paramBuilder.append(PARAMETERS_START);
				paramBuilder.append(PATH);
				paramBuilder.append(EQUALS);
				paramBuilder.append(path);
				paramBuilder.append(PARAMETERS_END);

				StringBuilder urlBuilder = new StringBuilder(URL_PREFIX);
				urlBuilder.append(URLEncoder.encode(paramBuilder.toString(), "UTF8"));
				return URI.create(urlBuilder.toString());
			} catch (UnsupportedEncodingException e) {
				log.error("{}", e);
			}
		}
		return null;
	}

	public static URI eclipseIntroUri(IJavaProject project, String fqName) {
		IType type = project.getIndex().findType(fqName);
		return type == null ? null : eclipseIntroUri(project, type);
	}

	public static URI eclipseIntroUri(IJavaProject project, IMember member) {
		try {
			StringBuilder paramBuilder = new StringBuilder(JAVA_ELEMENT_COMMAND);
			paramBuilder.append(PARAMETERS_START);
			paramBuilder.append(BINDING_KEY_PARAMETER_ID);
			paramBuilder.append(EQUALS);
			paramBuilder.append(member.getBindingKey());
			if (project != null && project.getElementName() != null) {
				paramBuilder.append(PARAMETERS_SEPARATOR);
				paramBuilder.append(PROJECT_NAME_PARAMETER_ID);
				paramBuilder.append(EQUALS);
				paramBuilder.append(project.getElementName());
			}
			paramBuilder.append(PARAMETERS_END);

			StringBuilder urlBuilder = new StringBuilder(URL_PREFIX);
			urlBuilder.append(URLEncoder.encode(paramBuilder.toString(), "UTF8"));
			return URI.create(urlBuilder.toString());
		} catch (UnsupportedEncodingException e) {
			log.error("{}", e);
		}
		return null;
	}
	
	static URI eclipseIntroUriForJarEntry(String projectName, URI jarEntryUri) {
		try {
			StringBuilder paramBuilder = new StringBuilder(JAR_ENTRY_COMMAND);

			paramBuilder.append(PARAMETERS_START);
			paramBuilder.append(JAR_URI_PARAM);
			paramBuilder.append(EQUALS);
			paramBuilder.append(jarEntryUri.toString());

			paramBuilder.append(PARAMETERS_SEPARATOR);
			paramBuilder.append(PROJECT_NAME_PARAMETER_ID);
			paramBuilder.append(EQUALS);
			paramBuilder.append(projectName);

			paramBuilder.append(PARAMETERS_END);

			StringBuilder urlBuilder = new StringBuilder(URL_PREFIX);
			urlBuilder.append(URLEncoder.encode(paramBuilder.toString(), "UTF8"));
			return URI.create(urlBuilder.toString());
		} catch (UnsupportedEncodingException e) {
			log.error("{}", e);
		}
		return null;
	}

	@Override
	public Optional<URI> sourceLinkForJarEntry(IJavaProject contextProject, URI uri) {
		return super.sourceLinkForJarEntry(contextProject, uri)
				.map(u -> eclipseIntroUriForJarEntry(contextProject.getElementName(), u));
	}

}
