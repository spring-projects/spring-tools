/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.completions;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.springframework.ide.eclipse.editor.support.hover.HoverInfo;
import org.springframework.ide.eclipse.editor.support.util.DocumentRegion;
import org.springframework.ide.eclipse.editor.support.yaml.YamlDocument;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNode;

/**
 * Abstract YamlAssistContext for the toplevel of a YamlDocument.
 * <p>
 * This context is typically a kind of 'dummy' context that is not
 * used to generate completions (it is not possible to type anything
 * in this context because... Wherever you type, you are always implicitly
 * typing in one of the 'subdocuments' of the YamlFile.
 * <p>
 * All this context needs to be able to do therefore, is to support traversal so that
 * it selects the appropriate context for a subdocument.
 *
 * @author Kris De Volder
 */
public abstract class TopLevelAssistContext implements YamlAssistContext {

	@Override
	public YamlAssistContext traverse(YamlPathSegment s) throws Exception {
		Integer documentSelector = s.toIndex();
		if (documentSelector != null) {
			return getDocumentContext(documentSelector);
		}
		return null;
	}

	@Override
	public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
		//This context really should never be used directly to create completions. But we provide
		// a dummy implementation anyway.
		return Collections.emptyList();
	}

	@Override
	public HoverInfo getHoverInfo() {
		return null;
	}

	public HoverInfo getHoverInfo(YamlPathSegment lastSegment) {
		return null;
	}

	@Override
	public HoverInfo getValueHoverInfo(YamlDocument doc, DocumentRegion documentRegion) {
		return null;
	}

	protected abstract YamlAssistContext getDocumentContext(int documentSelector);
}
