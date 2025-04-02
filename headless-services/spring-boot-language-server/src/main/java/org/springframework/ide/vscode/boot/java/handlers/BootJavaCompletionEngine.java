/*******************************************************************************
 * Copyright (c) 2017, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippetManager;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.InternalCompletionList;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

/**
 * @author Martin Lippert
 */
public class BootJavaCompletionEngine implements ICompletionEngine, LanguageSpecific {

	public static final String $MISSING$ = "$missing$";
	
	private Map<String, CompletionProvider> completionProviders;
	private JavaSnippetManager snippets;
	private CompilationUnitCache cuCache;

	public BootJavaCompletionEngine(CompilationUnitCache cuCache, Map<String, CompletionProvider> specificProviders, JavaSnippetManager snippets) {
		this.cuCache = cuCache;
		this.completionProviders = specificProviders;
		this.snippets = snippets;
	}

	@Override
	public InternalCompletionList getCompletions(TextDocument document, int offset) throws Exception {
		return cuCache.withCompilationUnit(document, cu -> {
			if (cu != null) {
				ASTNode node = findNode(document, offset, cu);

				if (node != null) {
					Collection<ICompletionProposal> completions = new ArrayList<ICompletionProposal>();
					collectCompletionsForAnnotations(node, offset, document, completions);
					collectCompletions(node, offset, document, completions);
					snippets.getCompletions(document, offset, node, cu, completions);
					
					return new InternalCompletionList(completions, false);
				}
			}

			return new InternalCompletionList(Collections.emptyList(), false);
		});
	}

	private ASTNode findNode(TextDocument document, int offset, CompilationUnit cu) {
		ASTNode node = NodeFinder.perform(cu, offset, 0);

		// take special case into account, e.g. @DependsOn(value = <*>)
		// in this case the NodeFinder returns the outer AST node (the one where the annotation belongs to
		// (e.g. the type declaration). This happens because the $missing$ SimpleName node is inserted right after
		// the "=". Therefore, the NodeFinder doesn't find the SimpleName at the position behind the space
		// and the information about the surrounding annotation is lost.
		//
		// For that case, we look for spaces before the offset and a $missing$ SimpleName to identify this
		// exact situation and pass on the ASTNode for the SimpleName.
		try {
			int numberOfSpaces = 0;
			while (Character.isSpaceChar(document.getChar(offset - numberOfSpaces - 1))
					|| document.getChar(offset - numberOfSpaces - 1) == '.') {
				numberOfSpaces++;
			}

			if (numberOfSpaces > 0) {
				ASTNode leftNode = NodeFinder.perform(cu, offset - numberOfSpaces, 0);
				if ((leftNode instanceof SimpleName && $MISSING$.equals(((SimpleName)leftNode).getIdentifier()))
						|| leftNode instanceof ThisExpression) {
					node = leftNode;
				}
			}
		} catch (BadLocationException e) {
			// ignore, keep the original node
		}
		
		return node;
	}

	private void collectCompletionsForAnnotations(ASTNode node, int offset, TextDocument doc, Collection<ICompletionProposal> completions) {
		Annotation annotation = null;
		ASTNode exactNode = node;

		while (node != null && !(node instanceof Annotation)) {
			node = node.getParent();
		}

		if (node != null) {
			annotation = (Annotation) node;
			ITypeBinding type = annotation.resolveTypeBinding();
			if (type != null) {
				String qualifiedName = type.getQualifiedName();
				if (qualifiedName != null) {
					CompletionProvider provider = this.completionProviders.get(qualifiedName);
					if (provider != null) {
						provider.provideCompletions(exactNode, annotation, type, offset, doc, completions);
					}
				}
			}
		}
	}

	private void collectCompletions(ASTNode node, int offset, TextDocument document, Collection<ICompletionProposal> completions) {
		if (node != null) {
			for (CompletionProvider completionProvider : this.completionProviders.values()) {
				completionProvider.provideCompletions(node, offset, document, completions);
			}
		}
	}

	@Override
	public Collection<LanguageId> supportedLanguages() {
		return ImmutableList.of(LanguageId.JAVA);
	}

}
