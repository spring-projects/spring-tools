/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata.util;

import java.util.Optional;

import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.commons.java.IJavaElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IMember;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.javadoc.IJavadoc;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Boot properties documentation info utils
 *
 * @author Alex Boyko
 *
 */
public class PropertyDocUtils {

	/**
	 * Generates documentation for boot property coming from java element
	 *
	 * @param sourceLinks
	 * @param project
	 * @param je
	 * @return
	 */
	public static Renderable javadocContent(SourceLinks sourceLinks, IJavaProject project, IJavaElement je) {
		Builder<Renderable> renderableBuilder = ImmutableList.builder();
		IJavadoc javadoc = je.getJavaDoc();
		renderableBuilder.add(Renderables.lineBreak());
		renderableBuilder.add(Renderables.paragraph(javadoc == null ? Renderables.NO_DESCRIPTION: javadoc.getRenderable()));
		return Renderables.concat(renderableBuilder.build());
	}

	/**
	 * Generates documentation for the value of some Java type. Includes signature, javadoc, link to container type.
	 *
	 * @param sourceLinks
	 * @param project
	 * @param je
	 * @return
	 */
	public static Renderable documentJavaElement(SourceLinks sourceLinks, IJavaProject project, IJavaElement je) {
		Builder<Renderable> renderableBuilder = ImmutableList.builder();
		if (je instanceof IMember) {
			IMember member = (IMember) je;
			renderableBuilder.add(Renderables.lineBreak());
			renderableBuilder.add(Renderables.inlineSnippet(member.signature()));
			IType containingType = je instanceof IType ? (IType) je : ((IMember)je).getDeclaringType();
			if (je != null) {
				String type = containingType.getFullyQualifiedName();
				Optional<String> url = sourceLinks.sourceLinkUrlForFQName(project, type);
				renderableBuilder.add(Renderables.lineBreak());
				if (url.isPresent()) {
					renderableBuilder.add(Renderables.link(type, url.get()));
				} else {
					renderableBuilder.add(Renderables.inlineSnippet(type));
				}
			}
		}
		IJavadoc javadoc = je.getJavaDoc();
		renderableBuilder.add(Renderables.lineBreak());
		renderableBuilder.add(Renderables.paragraph(javadoc == null ? Renderables.NO_DESCRIPTION: javadoc.getRenderable()));
		return Renderables.concat(renderableBuilder.build());
	}

}
