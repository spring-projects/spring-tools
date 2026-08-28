/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse.github;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.SimpleCompletionFactory;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.yaml.completion.CompletionFactory;
import org.springframework.ide.vscode.commons.yaml.schema.ISubCompletionEngine;

import com.google.common.collect.ImmutableList;

public class GithubRepoContentAssistant implements ISubCompletionEngine {

	public static final String[] URI_PREFIXES = {
			"git@github.com:",
			"https://github.com/"
	};
	private GithubInfoProvider github;

	public GithubRepoContentAssistant(GithubInfoProvider github) {
		this.github = github;
	}

	@Override
	public List<ICompletionProposal> getCompletions(CompletionFactory f, DocumentRegion region, int offset) {
		DocumentRegion query = region.subSequence(0, offset);
		//If uri prefix is already there, we provide CA for owner / repo
		for (String uriPrefix : URI_PREFIXES) {
			if (query.startsWith(uriPrefix)) {
				return getOwnerOrRepoCompletions(f, query.subSequence(uriPrefix.length()));
			}
		}
		//If uri prefix is not yet there, maybe we can suggest it (if it matches the query)
		List<ICompletionProposal> proposals = new ArrayList<>(URI_PREFIXES.length);
		for (String uriPrefix : URI_PREFIXES) {
			if (FuzzyMatcher.matchScore(query, uriPrefix) != 0.0) {
				proposals.add(SimpleCompletionFactory.simpleProposal(query, CompletionItemKind.Text, uriPrefix, null, null));
			}
		}
		return proposals;
	}

	private List<ICompletionProposal> getOwnerOrRepoCompletions(CompletionFactory f, DocumentRegion ownerAndRepoRegion) {
		try {
			int slash = ownerAndRepoRegion.indexOf('/');
			if (slash >= 0) {
				DocumentRegion owner = ownerAndRepoRegion.subSequence(0, slash);
				return getRepoCompletions(f, owner, ownerAndRepoRegion.subSequence(slash+1));
			} else {
				Collection<String> owners = github.getOwners();
				DocumentRegion query = ownerAndRepoRegion;
				if (!owners.isEmpty()) {
					List<ICompletionProposal> proposals = new ArrayList<>(owners.size());
					for (String owner : owners) {
						if (FuzzyMatcher.matchScore(query, owner) != 0.0) {
							proposals.add(SimpleCompletionFactory.simpleProposal(query, CompletionItemKind.Text, owner+"/", null, null));
						}
					}
					return proposals;
				} else {
					return ImmutableList.of();
				}
			}
		} catch (Exception e) {
			return ImmutableList.of(f.errorMessage(ownerAndRepoRegion.toString(), ExceptionUtil.getMessageNoAppendedInformation(e)));
		}
	}

	private List<ICompletionProposal> getRepoCompletions(CompletionFactory f, DocumentRegion owner, DocumentRegion query) {
		try {
			Collection<String> repos = github.getReposForOwner(owner.toString());
			if (repos != null && !repos.isEmpty()) {
				List<ICompletionProposal> proposals = new ArrayList<>(repos.size());
				for (String repo : repos) {
					if (FuzzyMatcher.matchScore(query, repo) != 0.0) {
						proposals.add(SimpleCompletionFactory.simpleProposal(query, CompletionItemKind.Text, repo+".git", null, null));
					}
				}
				return proposals;
			} else {
				return ImmutableList.of();
			}
		} catch (Exception e) {
			return ImmutableList.of(f.errorMessage(query.toString(), ExceptionUtil.getMessageNoAppendedInformation(e)));
		}
	}

}
