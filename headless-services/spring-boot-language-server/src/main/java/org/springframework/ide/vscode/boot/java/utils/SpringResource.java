/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.Renderables;

/**
 * Helper class, represents parsed info from a Resource, and provide method(s) to
 * display it somehow.
 */
public class SpringResource {

	public static final String FILE = "file";
	public static final String CLASS_PATH_RESOURCE = "class path resource";
	public static final String URL = "URL";
	private static final String CF_CLASSPATH_PREFIX = "/home/vcap/app/";

	private SourceLinks sourceLinks;
	private String type;
	private String path;
	private IJavaProject project;

	private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]");

	private static final String ID_PATTERN = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final String REGEX_FQCN = ID_PATTERN + "(\\." + ID_PATTERN + ")*";

	public SpringResource(SourceLinks sourceLinks, String toParse, IJavaProject project) {
		this.sourceLinks = sourceLinks;
		this.project = project;
		Matcher matcher = BRACKETS.matcher(toParse);
		if (matcher.find()) {
			type = toParse.substring(0, matcher.start()).trim();
			path = toParse.substring(matcher.start()+1, matcher.end()-1);
			if (type.equals("file") && path.startsWith(CF_CLASSPATH_PREFIX)) {
				type = CLASS_PATH_RESOURCE;
				path = path.substring(CF_CLASSPATH_PREFIX.length());
			}
		} else if (Pattern.matches(REGEX_FQCN, toParse)) {
			// Resource is fully qualified Java type name
			type = CLASS_PATH_RESOURCE;
			path = toParse.replace('.','/') + SourceLinks.CLASS;
		} else {
			path = toParse;
		}
	}

	public String toMarkdown() {
		if (type == null) {
			return path; //path is just the raw text in this case
		}
		Optional<String> linkUrl = Optional.empty();
		switch (type) {
		case FILE:
		case URL:
			linkUrl = sourceLinks.sourceLinkUrlForClasspathResource(path);
			if (!linkUrl.isPresent()) {
				linkUrl = sourceLinks.sourceLinkForResourcePath(Paths.get(path));
			}
			// not a project relative path
			return linkUrl.isPresent() ? Renderables.link(projectRelativePath(project, path), linkUrl.get()).toMarkdown()
					: Renderables.inlineSnippet(projectRelativePath(project, path)).toMarkdown();
		case CLASS_PATH_RESOURCE:
			int idx = path.lastIndexOf(SourceLinks.CLASS);
			if (idx >= 0) {
				Path p = Paths.get(path.substring(0, idx));
				linkUrl = sourceLinks.sourceLinkUrlForFQName(project, p.toString().replace(File.separator, "."));
			}
			return linkUrl.isPresent() ? Renderables.link(path, linkUrl.get()).toMarkdown() : Renderables.inlineSnippet(path).toMarkdown();
		default:
			return path;
		}
	}

	public static String projectRelativePath(IJavaProject project, String pathStr) {
		Path path = Paths.get(pathStr);
		IClasspath classpath = project.getClasspath();
		Iterable<File> ofs = () -> IClasspathUtil.getOutputFolders(classpath).iterator();
		for (File _outputFolder : ofs) {
			Path outputFolder = _outputFolder.toPath();
			if (path.startsWith(outputFolder)) {
				return outputFolder.relativize(path).toString();
			}
		}
		return pathStr;
	}

}
