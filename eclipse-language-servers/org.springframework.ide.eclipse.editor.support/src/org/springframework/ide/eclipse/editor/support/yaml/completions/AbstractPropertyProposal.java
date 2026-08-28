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

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.completions.CompletionFactory.ScoreableProposal;
import org.springframework.ide.eclipse.editor.support.completions.ProposalApplier;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;

public abstract class AbstractPropertyProposal extends ScoreableProposal implements ICompletionProposalExtension3,
ICompletionProposalExtension4, ICompletionProposalExtension6
{

	protected final IDocument fDoc;
	private final ProposalApplier proposalApplier;
	private boolean isDeprecated = false;

	public AbstractPropertyProposal(IDocument doc, ProposalApplier applier) {
		this.proposalApplier = applier;
		this.fDoc = doc;
	}

	public Point getSelection(IDocument document) {
		try {
			return proposalApplier.getSelection(document);
		} catch (Exception e) {
			EditorSupportActivator.log(e);
			return null;
		}
	}


	public String getDisplayString() {
		StyledString styledText = getStyledDisplayString();
		return styledText.getString();
	}

	public Image getImage() {
		return null;
	}

	public IContextInformation getContextInformation() {
		return null;
	}

	@Override
	public StyledString getStyledDisplayString() {
		StyledString result = new StyledString();
		result = result.append(super.getStyledDisplayString());
		YType type = getType();
		if (type != null) {
			String typeStr = niceTypeName(type);
			result.append(" : "+typeStr, StyledString.DECORATIONS_STYLER);
		}
		return result;
	}

	protected boolean isDeprecated() {
		return isDeprecated;
	}
	public void deprecate() {
		if (!isDeprecated()) {
			deemphasize();
			deemphasize();
			isDeprecated = true;
		}
	}
	protected abstract YType getType();
	protected abstract String getHighlightPattern();
	protected abstract String getBaseDisplayString();
	protected abstract String niceTypeName(YType type);

	@Override
	public String toString() {
		return getBaseDisplayString();
	}

	@Override
	public void apply(IDocument document) {
		try {
			proposalApplier.apply(document);
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
	}
}