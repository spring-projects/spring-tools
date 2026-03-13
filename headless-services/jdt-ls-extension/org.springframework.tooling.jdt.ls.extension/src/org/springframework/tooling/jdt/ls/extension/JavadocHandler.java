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
package org.springframework.tooling.jdt.ls.extension;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.javadoc.JavadocContentAccess2;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.ide.vscode.commons.protocol.java.JavaDataParams;
import org.springframework.tooling.jdt.ls.commons.java.JavaData;

public class JavadocHandler implements IDelegateCommandHandler {
	
	@Override
	public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
		Map<String, Object> obj = (Map<String, Object>) arguments.get(0);
		String uri = (String) obj.get("projectUri");
		URI projectUri = URI.create(uri);
		String bindingKey = (String) obj.get("bindingKey");
		Boolean lookInOtherProjects = (Boolean) obj.get("lookInOtherProjects");
		IJavaElement element = JavaData.findElement(projectUri, bindingKey, JavaDataParams.isLookInOtherProjects(uri, lookInOtherProjects));
		String content = element == null ? null : JavadocContentAccess2.getMarkdownContent(element);
		if (content == null) {
			return null;
		} else {
			MarkupContent mc = new MarkupContent();
			mc.setKind(MarkupKind.MARKDOWN);
			mc.setValue(content);
			return mc;
		}
	}

}
