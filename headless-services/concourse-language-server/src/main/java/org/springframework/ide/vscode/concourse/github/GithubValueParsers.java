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

import java.util.Collection;

import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileException;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.concourse.PipelineYmlSchemaProblems;

public class GithubValueParsers {

	public static class GithubRepoReference {
		public final DocumentRegion owner;
		public final DocumentRegion name;
		public GithubRepoReference(DocumentRegion owner, DocumentRegion name) {
			super();
			this.owner = owner;
			this.name = name;
		}
	}

	private static ReconcileException unknownEntity(String msg, DocumentRegion highlight) {
		return new ReconcileException(msg, PipelineYmlSchemaProblems.UNKNOWN_GITHUB_ENTITY, highlight.getStart(), highlight.getEnd());
	}

	public static ValueParser uri(GithubInfoProvider github) {

		return new ValueParser() {

			@Override public GithubRepoReference parse(String _str) throws Exception {
				TextDocument doc = new TextDocument(null, LanguageId.PLAINTEXT);
				doc.setText(_str);
				DocumentRegion str = new DocumentRegion(doc);
				GithubRepoReference repo = parseFormat(str);
				if (repo != null) {
					Collection<String> knownRepos;
					try {
						knownRepos = github.getReposForOwner(repo.owner.toString());
					} catch (Exception e) {
						//Couldn't read info from github. Ignore this in reconciler context.
						return repo;
					}
					if (knownRepos == null) {
						throw unknownEntity("User or Organization not found: '"+repo.owner+"'", repo.owner);
					} else {
						if (!knownRepos.contains(repo.name.toString())) {
							throw unknownEntity("Repo not found: '"+repo.name+"'", repo.name);
						}
					}
				}
				return repo;
			}

			private GithubRepoReference parseFormat(DocumentRegion str) throws Exception {
				String prefix = checkPrefix(str);
				if (prefix != null) {
					DocumentRegion ownerAndName = str.subSequence(prefix.length());
					//Should end with '.git'
					if (ownerAndName.endsWith(".git")) {
						ownerAndName = ownerAndName.subSequence(0, ownerAndName.length()-4);
					} else {
						DocumentRegion highlight = ownerAndName.textAtEnd(1);
						throw new ValueParseException("GitHub repo uri should end with '.git'", highlight.getStart(), highlight.getEnd());
					}
					int slash = ownerAndName.indexOf('/');
					if (slash >= 0) {
						return new GithubRepoReference(ownerAndName.subSequence(0, slash), ownerAndName.subSequence(slash+1));
					} else {
						throw new ValueParseException("Expecting something of the form '${owner}/${repo}'.", ownerAndName.getStart(), ownerAndName.getEnd());
					}
				}
				return null;
			}

			private String checkPrefix(DocumentRegion str) throws ValueParseException {
				for (String expectedPrefix : GithubRepoContentAssistant.URI_PREFIXES) {
					int lastChar = expectedPrefix.length()-1;
					if (str.startsWith(expectedPrefix.substring(0, lastChar))) {
						char actualSeparator = str.safeCharAt(lastChar);
						char expectedSeparator = expectedPrefix.charAt(lastChar);
						if (actualSeparator == expectedSeparator) {
							return expectedPrefix;
						}
						if (actualSeparator == ':' || actualSeparator == '/') {
							throw new ValueParseException("Expecting a '"+expectedSeparator+"'", lastChar, lastChar+1);
						}
					}
				}
				return null;
			}
		};
	}

}
