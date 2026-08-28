/*******************************************************************************
 * Copyright (c) 2005, 2008, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genady Beryozkin, me@genady.org - #getSuggestions implementation copied from HippieCompleteAction
 *     Kris De Volder - Copied and modified HippieCompletionProcessor to become 'SpringPropertiesCompletionProcessor'.
 *                    - later on modified some more to make it more reusable as a 'general' wrapper around
 *                      a ICompletionEngine
 ********************************************************************************/
package org.springframework.ide.eclipse.editor.support.completions;

import java.util.Collection;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;

/**
 * Wraps a {@link ICompletionEngine} so it can be use as a {@link IContentAssistProcessor}
 *
 * @author Kris De Volder
 */
public class ProposalProcessor implements IContentAssistProcessor {

	private static final ICompletionProposal[] NO_PROPOSALS= new ICompletionProposal[0];
	private static final IContextInformation[] NO_CONTEXTS= new IContextInformation[0];
	public static final char[] AUTO_ACTIVATION_CHARS = ".ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

	private final ICompletionEngine fEngine;

	public ProposalProcessor(ICompletionEngine engine) {
		this.fEngine = engine;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeCompletionProposals(org.eclipse.jface.text.ITextViewer, int)
	 */
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		try {
			Collection<ICompletionProposal> proposals = fEngine.getCompletions(viewer.getDocument(), offset);
			if (proposals == null || proposals.isEmpty()) {
				return NO_PROPOSALS;
			} else {
				return proposals.toArray(new ICompletionProposal[proposals.size()]);
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
			return NO_PROPOSALS;
		}
	}


	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#computeContextInformation(org.eclipse.jface.text.ITextViewer, int)
	 */
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		// no context informations for hippie completions
		return NO_CONTEXTS;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getCompletionProposalAutoActivationCharacters()
	 */
	public char[] getCompletionProposalAutoActivationCharacters() {
		return AUTO_ACTIVATION_CHARS;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationAutoActivationCharacters()
	 */
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.IContentAssistProcessor#getContextInformationValidator()
	 */
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposalComputer#getErrorMessage()
	 */
	public String getErrorMessage() {
		return null; // no custom error message
	}

}
