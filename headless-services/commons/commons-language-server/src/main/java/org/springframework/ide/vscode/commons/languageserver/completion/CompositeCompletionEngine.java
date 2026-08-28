/*******************************************************************************
 * Copyright (c) 2019, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.completion;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

/**
 * A completion engine implementation that dispatches completion requests to 
 * one or more 'sub' completion engines based on language id of the document.
 * <p>
 * If more than one sub-engine is applicable then completions are requested from
 * all of them and their results are merged into a single list.
 */
public class CompositeCompletionEngine implements ICompletionEngine {

	private static Logger log = LoggerFactory.getLogger(CompositeCompletionEngine.class);
	
	Multimap<LanguageId, ICompletionEngine> subEngines = LinkedListMultimap.create();
	
	public void add(LanguageId language, ICompletionEngine engine) {
		subEngines.put(language, engine);
	}
	
	public void add(ICompletionEngine engine) {
		Assert.isLegal(engine instanceof LanguageSpecific, "Only LanguageSpecific completion engines are currently supported "+engine.getClass().getName());
		Collection<LanguageId> languages = ((LanguageSpecific)engine).supportedLanguages();
		Assert.isLegal(!languages.isEmpty(), "Completion engine that doesn't support any language");
		for (LanguageId languageId : languages) {
			add(languageId, engine);
		}
	}
	
	@Override
	public InternalCompletionList getCompletions(TextDocument document, int offset) throws Exception {
		LanguageId language = document.getLanguageId();
		log.info("languageId = {}", language);
		Collection<ICompletionEngine> engines = subEngines.get(language);
		
		if (engines.size() == 1) {

			//Special case to avoid some collection copying
			ICompletionEngine engine = engines.iterator().next();
			return engine.getCompletions(document, offset);

		} else {

			boolean isIncomplete = false;
			ImmutableList.Builder<ICompletionProposal> completions = ImmutableList.builder();

			for (ICompletionEngine engine : engines) {
				InternalCompletionList c = engine.getCompletions(document, offset);
				if (c != null && c.completionItems() != null) {
					completions.addAll(c.completionItems());
					if (c.isIncomplete()) {
						isIncomplete = true;
					}
				}
			}
			return new InternalCompletionList(completions.build(), isIncomplete);
		}
	};
	
	@Override
	public boolean keepCompletionsOrder(IDocument doc) {
		Collection<ICompletionEngine> engines = subEngines.get(doc.getLanguageId());
		if (engines != null) {
			for (ICompletionEngine e : engines) {
				if (e.keepCompletionsOrder(doc)) {
					return true;
				}
			}
		}
		return false;
	}


}
