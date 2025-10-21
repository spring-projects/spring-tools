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

import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.Region;

/**
 * VSCode specific source links implementation
 *
 * @author Alex Boyko
 *
 */
public class VSCodeSourceLinks extends LegacySourceLinks {

	public VSCodeSourceLinks(CompilationUnitCache cuCache, JavaProjectFinder projectFinder) {
		super(cuCache, projectFinder);
	}

	@Override
	public Optional<String> sourceLinkForResourcePath(Path path) {
		return Optional.of(path.toUri().toASCIIString());
	}

	@Override
	protected String positionLink(CompilationUnit cu, String fqName) {
		if (cu != null) {
			Region region = findTypeRegion(cu, fqName);
			if (region != null) {
				int column = cu.getColumnNumber(region.getOffset());
				int line = cu.getLineNumber(region.getOffset());
				StringBuilder sb = new StringBuilder();
				sb.append('#');
				sb.append(line);
				sb.append(',');
				sb.append(column + 1); // 1-based columns?
				return sb.toString();
			}
		}
		return null;
	}

	@Override
	protected Optional<String> jarLinkUrl(IJavaProject project, String fqName, IJavaModuleData jarModuleData) {
		return Optional.ofNullable(JdtJavaDocumentUriProvider.uri(project, fqName)).map(uri -> uri.toASCIIString());
	}

}
