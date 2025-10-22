/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
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
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.Region;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Source links for Atom client
 *
 * @author Alex Boyko
 *
 */
public class AtomSourceLinks extends LegacySourceLinks {

	private static Supplier<Logger> LOG = Suppliers.memoize(() -> LoggerFactory.getLogger(LegacySourceLinks.class));

	public AtomSourceLinks(CompilationUnitCache cuCache, JavaProjectFinder projectFinder) {
		super(cuCache, projectFinder);
	}

	@Override
	public Optional<String> sourceLinkForResourcePath(Path path) {
		try {
			return Optional.of("atom://core/open/file?filename=" + URLEncoder.encode(path.toString(), "UTF8"));
		} catch (UnsupportedEncodingException e) {
			LOG.get().error("Cannot build source URL for " + path, e);
		}
		return Optional.empty();
	}

	@Override
	protected String positionLink(CompilationUnit cu, String fqName) {
		if (cu != null) {
			Region region = findTypeRegion(cu, fqName);
			if (region != null) {
				int column = cu.getColumnNumber(region.getOffset());
				int line = cu.getLineNumber(region.getOffset());
				StringBuilder sb = new StringBuilder();
				sb.append("&line=");
				sb.append(line);
				sb.append("&column=");
				sb.append(column + 1); // 1-based columns?
				return sb.toString();
			}
		}
		return null;
	}

	@Override
	protected Optional<String> jarLinkUrl(IJavaProject project, String fqName, IJavaModuleData jarModuleData) {
		// JAR URLs are not supported yet
		return Optional.empty();
	}

}
