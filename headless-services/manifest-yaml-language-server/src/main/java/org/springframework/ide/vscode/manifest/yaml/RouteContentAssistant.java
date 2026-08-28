/*******************************************************************************
 * Copyright (c) 2017, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.yaml.completion.CompletionFactory;
import org.springframework.ide.vscode.commons.yaml.schema.ISubCompletionEngine;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;

import com.google.common.collect.ImmutableList;

/**
 * Custom content assistant for making 'domains' suggestions inside of a RouteUri.
 *
 * @author Kris De Volder
 */
public class RouteContentAssistant implements ISubCompletionEngine {
	
	private static final Logger log = LoggerFactory.getLogger(RouteContentAssistant.class);

	private static final Pattern STOP_AP = Pattern.compile("[#:/]");

	private Callable<Collection<YValueHint>> domainsProvider;

	private ManifestYmlSchema schema;

	public RouteContentAssistant(Callable<Collection<YValueHint>> domainsProvider, ManifestYmlSchema manifestYmlSchema) {
		this.domainsProvider = domainsProvider;
		this.schema = manifestYmlSchema;
	}

	@Override
	public List<ICompletionProposal> getCompletions(CompletionFactory f, DocumentRegion region, int offset) {
		// offset = 0 means: "<*>abc"
		// offset = 1 means: "a<*>bc"
		// offset = 2 means: "ab<*>c"
		try {
			region = chopEnd(region, offset);
			// region = "abc"
			// offset = 3 means: "abc<*>"
			// So offset > 3 means we are 'our of bounds for this content assistant
			if (offset <= region.length()) {
				String[] queries = getQueries(region.subSequence(0, offset));
				Collection<YValueHint> domains = domainsProvider.call();
				List<ICompletionProposal> proposals = new ArrayList<>();
				for (YValueHint domain : domains) {
					for (String query : queries) {
						double score = FuzzyMatcher.matchScore(query, domain.getValue());
						if (score != 0.0) {
							proposals.add(createProposal(f, region, offset, query, score, domain));
							break; //break here so we select the first (i.e. longest) query that matches
						}
					}
				}
				return proposals;
			}
		} catch (Exception e) {
			log.error("", e);
			//Ignore. This is somewhat expected. Stuff can go wrong resolving the domains
			// and CA engine just doesn't provide CA in that case.
		}
		return ImmutableList.of();
	}

	private String[] getQueries(DocumentRegion region) {
		// Example of what this should do
		//   region = "foo.bar.com"
		// We have to make a guess which part of this is the host-name and which part is domain-name
		// The split could be either at the very start, or at any one of the '.' chars.
		// So the possible queries are as follows:
		//   - "foo.bar.com"
		//   - "bar.com"
		//   - "com"
		region = region.trimStart();
		DocumentRegion[] pieces = region.split('.');
		String[] queries = new String[pieces.length];
		for (int i = pieces.length-1; i >= 0; i--) {
			if (i == pieces.length-1) {
				queries[i] = pieces[i].toString();
			} else {
				queries[i] = pieces[i] + "." + queries[i+1];
			}
		}
		return queries;
	}

	private ICompletionProposal createProposal(CompletionFactory f, DocumentRegion region, int offset, String query, double score, YValueHint domain) {
		DocumentEdits edits = new DocumentEdits(region.getDocument(), false);
		region = region.subSequence(offset - query.length());
		boolean needSpace = region.textBefore(1).charAt(0) == ':'; //Add extra space after ':' if needed!
		edits.replace(region.getStart(), region.getEnd(), needSpace ? " "+domain.getValue() : domain.getValue());
		return f.valueProposal(domain.getValue(), query, domain.getLabel(), schema.t_route_string,
				domain.getDocumentation(), score, edits, schema.getTypeUtil());
	}

	/**
	 * Chop-off the uninteresting parts of region (whitespace, comments, anything after the first ':' or '/'
	 */
	private DocumentRegion chopEnd(DocumentRegion region, int offset) {
		Matcher matcher = STOP_AP.matcher(region);
		if (matcher.find()) {
			region = region.subSequence(0, matcher.start());
		}
		if (offset <= region.getLength()) {
			//We want to trim whitespace of the end, but must be careful not to trim past the offset
			DocumentRegion trimmed = region.trimEnd();
			if (offset <= trimmed.length()) {
				return trimmed;
			} else {
				return region.subSequence(0, offset);
			}
		}
		return region;
	}

}
