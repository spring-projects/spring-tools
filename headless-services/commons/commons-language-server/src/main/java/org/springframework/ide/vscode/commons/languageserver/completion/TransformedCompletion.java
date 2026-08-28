/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.completion;

import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.ide.vscode.commons.util.Renderable;

/**
 * Abstract base-class to implement a Completion that is derived from
 * another completion with some transformations applied to it. (E.g. add
 * extra indentation for relaxed indentation proposals).
 *
 * @author Kris De Volder
 */
public abstract class TransformedCompletion extends AbstractScoreableProposal {
	
	protected final ICompletionProposal original;

	private DocumentEdits transformedEdit = null;
	
	public TransformedCompletion(ICompletionProposal proposal) {
		this.original = proposal;
	}

	protected String tranformLabel(String originalLabel) {
		return originalLabel;
	}
	protected DocumentEdits transformEdit(DocumentEdits textEdit) {
		return textEdit;
	}

	@Override
	public synchronized DocumentEdits getTextEdit() {
		if (transformedEdit == null) {
			transformedEdit = transformEdit(original.getTextEdit());
		}
		return transformedEdit;
	}


	@Override
	public String getLabel() {
		return tranformLabel(original.getLabel());
	}

	@Override
	public CompletionItemKind getKind() {
		return original.getKind();
	}

	@Override
	public Renderable getDocumentation() {
		return original.getDocumentation();
	}

	@Override
	public String getDetail() {
		return original.getDetail();
	}

	@Override
	public double getBaseScore() {
		if (original instanceof AbstractScoreableProposal) {
			return ((AbstractScoreableProposal) original).getScore();
		}
		return 0;
	}
	
	@Override
	public String getFilterText() {
		return original.getFilterText();
	}

	@Override
	public Optional<Supplier<DocumentEdits>> getAdditionalEdit() {
		return original.getAdditionalEdit();
	}

}