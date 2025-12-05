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
package org.springframework.ide.vscode.boot.java.value;

import static org.springframework.ide.vscode.commons.util.StringUtil.camelCaseToHyphens;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeCompletionProposal;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeProposal;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.FuzzyMap.Match;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class ValueCompletionProcessor implements CompletionProvider {
	
	private static final Logger log = LoggerFactory.getLogger(ValueCompletionProcessor.class);

	private final SpringPropertyIndexProvider indexProvider;
	private final ProjectBasedPropertyIndexProvider adHocIndexProvider;
	private final JavaProjectFinder projectFinder;

	public ValueCompletionProcessor(JavaProjectFinder projectFinder, SpringPropertyIndexProvider indexProvider, ProjectBasedPropertyIndexProvider adHocIndexProvider) {
		this.projectFinder = projectFinder;
		this.indexProvider = indexProvider;
		this.adHocIndexProvider = adHocIndexProvider;
	}

	@Override
	public void provideCompletions(ASTNode node, Annotation annotation, ITypeBinding type,
			int offset, TextDocument doc, Collection<ICompletionProposal> completions) {

		try {
			Optional<IJavaProject> optionalProject = this.projectFinder.find(doc.getId());
			if (optionalProject.isEmpty()) {
				return;
			}
			
			IJavaProject project = optionalProject.get();
			
			// in case the node is embedded in an qualified name, e.g. "file.txt", use the fully qualified node instead just a part
			if (node instanceof Name && node.getParent() instanceof QualifiedName) {
				node = node.getParent();
			}
			
			// case: @Value(<*>)
			if (node == annotation && doc.get(offset - 1, 2).endsWith("()")) {
				List<Match<PropertyInfo>> matches = findMatches("", doc);

				for (Match<PropertyInfo> match : matches) {

					DocumentEdits edits = new DocumentEdits(doc, false);
					edits.replace(offset, offset, "\"${" + match.data.getId() + "}\"");

					// PT-160455522: create a proposal with `PlainText` format type, because for vscode (but not Eclipse), if you send it as a snippet
					// and it is "place holder" as such `"${debug}"`, vscode may treat it as a snippet place holder, and insert an empty string
					// if it cannot resolve it. If sending this as plain text, then insertion happens correctly
					ValuePropertyKeyProposal proposal = new ValuePropertyKeyProposal(edits, match);

					completions.add(proposal);
				}
				
				addClasspathResourceProposals(project, doc, offset, offset, "", true, completions);
			}
			// case: @Value(prefix<*>)
			else if (node instanceof Name && node.getParent() instanceof Annotation) {
				computeProposalsForSimpleName(project, node, completions, offset, doc);
			}
			// case: @Value(value=<*>)
			else if (node instanceof Name && node.getParent() instanceof MemberValuePair
					&& "value".equals(((MemberValuePair)node.getParent()).getName().toString())) {
				computeProposalsForSimpleName(project, node, completions, offset, doc);
			}
			// case: @Value(value=<*>)
			else if (node instanceof Name && node.getParent() instanceof QualifiedName && node.getParent().getParent() instanceof MemberValuePair
					&& "value".equals(((MemberValuePair)node.getParent().getParent()).getName().toString())) {
				computeProposalsForSimpleName(project, node.getParent(), completions, offset, doc);
			}
			// case: @Value("prefix<*>")
			else if (node instanceof StringLiteral && node.getParent() instanceof Annotation) {
				if (node.toString().startsWith("\"") && node.toString().endsWith("\"")) {
					computeProposalsForStringLiteral(project, (StringLiteral) node, completions, offset, doc);
				}
			}
			// case: @Value(value="prefix<*>")
			else if (node instanceof StringLiteral && node.getParent() instanceof MemberValuePair
					&& "value".equals(((MemberValuePair)node.getParent()).getName().toString())) {
				if (node.toString().startsWith("\"") && node.toString().endsWith("\"")) {
					computeProposalsForStringLiteral(project, (StringLiteral) node, completions, offset, doc);
				}
			}
		}
		catch (Exception e) {
			log.error("problem while looking for value annotation proposals", e);
		}
	}

	private void computeProposalsForSimpleName(IJavaProject project, ASTNode node, Collection<ICompletionProposal> completions, int offset, TextDocument doc) {
		String prefix = identifyPropertyPrefix(node.toString(), offset - node.getStartPosition());

		int startOffset = node.getStartPosition();
		int endOffset = node.getStartPosition() + node.getLength();

		String proposalPrefix = "\"";
		String proposalPostfix = "\"";

		List<Match<PropertyInfo>> matches = findMatches(prefix, doc);

		for (Match<PropertyInfo> match : matches) {

			DocumentEdits edits = new DocumentEdits(doc, false);
			edits.replace(startOffset, endOffset, proposalPrefix + "${" + match.data.getId() + "}" + proposalPostfix);

			ValuePropertyKeyProposal proposal = new ValuePropertyKeyProposal(edits, match);

			completions.add(proposal);
		}
		
		String unfilteredPrefix = node.toString().substring(0, offset - node.getStartPosition());
		addClasspathResourceProposals(project, doc, startOffset, endOffset, unfilteredPrefix, true, completions);
	}

	private void computeProposalsForStringLiteral(IJavaProject project, StringLiteral node, Collection<ICompletionProposal> completions, int offset, TextDocument doc) throws BadLocationException {
		if (node.getStartPosition() + node.getLength() <= offset) {
			return;
		}
		
		String prefix = identifyPropertyPrefix(doc.get(node.getStartPosition() + 1, offset - (node.getStartPosition() + 1)), offset - (node.getStartPosition() + 1));

		int startOffset = offset - prefix.length();
		int endOffset = offset;

		String prePrefix = doc.get(node.getStartPosition() + 1, offset - prefix.length() - node.getStartPosition() - 1);

		String preCompletion;
		if (prePrefix.endsWith("${")) {
			preCompletion = "";
		}
		else if (prePrefix.endsWith("$")) {
			preCompletion = "{";
		}
		else {
			preCompletion = "${";
		}

		String fullNodeContent = doc.get(node.getStartPosition(), node.getLength());
		String postCompletion = isClosingBracketMissing(fullNodeContent + preCompletion) ? "}" : "";

		List<Match<PropertyInfo>> matches = findMatches(prefix, doc);

		for (Match<PropertyInfo> match : matches) {

			DocumentEdits edits = new DocumentEdits(doc, false);
			edits.replace(startOffset, endOffset, preCompletion + match.data.getId() + postCompletion);

			// PT 160455522: create a proposal with `PlainText` format type, because for vscode (but not Eclipse), if you send it as a snippet
			// and the proposal value is "place holder" as such `"${debug}"`, vscode may treat it as a snippet place holder, and insert an empty string
			// if it cannot resolve it. If sending this as plain text, then insertion happens correctly
			ValuePropertyKeyProposal proposal = new ValuePropertyKeyProposal(edits, match);

			completions.add(proposal);
		}

		String literalValue = ASTUtils.getLiteralValue(node);
		String unfilteredPrefix = literalValue.substring(0, offset - (node.getStartPosition() + 1));
		addClasspathResourceProposals(project, doc, startOffset, endOffset, unfilteredPrefix, false, completions);
	}

	private boolean isClosingBracketMissing(String fullNodeContent) {
		int bracketOpens = 0;

		for (int i = 0; i < fullNodeContent.length(); i++) {
			if (fullNodeContent.charAt(i) == '{') {
				bracketOpens++;
			}
			else if (fullNodeContent.charAt(i) == '}') {
				bracketOpens--;
			}
		}

		return bracketOpens > 0;
	}

	public String identifyPropertyPrefix(String nodeContent, int offset) {
		String result = nodeContent.substring(0, offset);

		int i = offset - 1;
		while (i >= 0) {
			char c = nodeContent.charAt(i);
			if (c == '}' || c == '{'  || c == '$' || c == '#') {
				result = result.substring(i + 1, offset);
				break;
			}
			i--;
		}

		return result;
	}

	private List<Match<PropertyInfo>> findMatches(String prefix, IDocument doc) {
		FuzzyMap<PropertyInfo> index = indexProvider.getIndex(doc).getProperties();
		List<Match<PropertyInfo>> matches = index.find(camelCaseToHyphens(prefix));

		// First the 'real' properties.
		Set<String> suggestedKeys = new HashSet<>();
		for (Match<PropertyInfo> m : matches) {
			suggestedKeys.add(m.data.getId());
		}

		// Then also add 'ad-hoc' properties
		Optional<IJavaProject> p = projectFinder.find(new TextDocumentIdentifier(doc.getUri()));
		if (p.isPresent()) {
			index = adHocIndexProvider.getIndex(p.get());
			for (Match<PropertyInfo> m : index.find(prefix)) {
				if (suggestedKeys.add(m.data.getId())) {
					matches.add(m);
				}
			}
		}
		return matches;
	}

	private void addClasspathResourceProposals(IJavaProject project, TextDocument doc, int startOffset, int endOffset, String prefix, boolean includeQuotes, Collection<ICompletionProposal> completions) {
		String[] resources = findResources(project, prefix);

		double score = resources.length + 1000;
		for (String resource : resources) {

			DocumentEdits edits = new DocumentEdits(doc, false);
			
			if (includeQuotes) {
				edits.replace(startOffset, endOffset, "\"classpath:" + resource + "\"");
			}
			else {
				edits.replace(startOffset, endOffset, "classpath:" + resource);
			}
			
			String label = "classpath:" + resource;
			
			AnnotationAttributeProposal coreProposal = new AnnotationAttributeProposal(label);
			ICompletionProposal proposal = new AnnotationAttributeCompletionProposal(edits, coreProposal, null, score--);
			completions.add(proposal);
		}

	}

	private String[] findResources(IJavaProject project, String prefix) {
		String[] resources = IClasspathUtil.getClasspathResources(project.getClasspath()).stream()
			.distinct()
			.sorted(new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					return Paths.get(o1).compareTo(Paths.get(o2));
				}
			})
			.map(r -> r.replaceAll("\\\\", "/"))
			.filter(r -> ("classpath:" + r).contains(prefix))
			.toArray(String[]::new);

		return resources;
	}

}
