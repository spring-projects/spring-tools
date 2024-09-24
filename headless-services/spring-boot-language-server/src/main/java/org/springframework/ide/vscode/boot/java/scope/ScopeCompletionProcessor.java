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
package org.springframework.ide.vscode.boot.java.scope;

import java.util.List;

import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeCompletionProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * @author Martin Lippert
 */
public class ScopeCompletionProcessor implements AnnotationAttributeCompletionProvider {
	
	private static final List<String> SCOPE_COMPLETIONS = List.of(
		"application",
		"globalSession",
		"prototype",
		"request",
		"session",
		"singleton",
		"websocket"
	);

	@Override
	public List<String> getCompletionCandidates(IJavaProject project) {
		return SCOPE_COMPLETIONS;
	}

//	@Override
//	public void provideCompletions(ASTNode node, Annotation annotation, ITypeBinding type,
//			int offset, TextDocument doc, Collection<ICompletionProposal> completions) {
//
//		try {
//			if (node instanceof SimpleName && node.getParent() instanceof MemberValuePair) {
//				MemberValuePair memberPair = (MemberValuePair) node.getParent();
//
//				// case: @Scope(value=<*>)
//				if ("value".equals(memberPair.getName().toString()) && memberPair.getValue().toString().equals("$missing$")) {
//					for (ScopeNameCompletion completion : ScopeNameCompletionProposal.COMPLETIONS) {
//						ICompletionProposal proposal = new ScopeNameCompletionProposal(completion, doc, offset, offset, "");
//						completions.add(proposal);
//					}
//				}
//			}
//			// case: @Scope(<*>)
//			else if (node == annotation && doc.get(offset - 1, 2).endsWith("()")) {
//				for (ScopeNameCompletion completion : ScopeNameCompletionProposal.COMPLETIONS) {
//					ICompletionProposal proposal = new ScopeNameCompletionProposal(completion, doc, offset, offset, "");
//					completions.add(proposal);
//				}
//			}
//			else if (node instanceof StringLiteral && node.getParent() instanceof Annotation) {
//				// case: @Scope("...")
//				if (node.toString().startsWith("\"") && node.toString().endsWith("\"")) {
//					String prefix = doc.get(node.getStartPosition(), offset - node.getStartPosition());
//					for (ScopeNameCompletion completion : ScopeNameCompletionProposal.COMPLETIONS) {
//						if (completion.getValue().startsWith(prefix)) {
//							ICompletionProposal proposal = new ScopeNameCompletionProposal(completion, doc, node.getStartPosition(), node.getStartPosition() + node.getLength(), prefix);
//							completions.add(proposal);
//						}
//					}
//				}
//			}
//			else if (node instanceof StringLiteral && node.getParent() instanceof MemberValuePair) {
//				MemberValuePair memberPair = (MemberValuePair) node.getParent();
//
//				// case: @Scope(value=<*>)
//				if ("value".equals(memberPair.getName().toString()) && node.toString().startsWith("\"") && node.toString().endsWith("\"")) {
//					String prefix = doc.get(node.getStartPosition(), offset - node.getStartPosition());
//					for (ScopeNameCompletion completion : ScopeNameCompletionProposal.COMPLETIONS) {
//						if (completion.getValue().startsWith(prefix)) {
//							ICompletionProposal proposal = new ScopeNameCompletionProposal(completion, doc, node.getStartPosition(), node.getStartPosition() + node.getLength(), prefix);
//							completions.add(proposal);
//						}
//					}
//				}
//			}
//		}
//		catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

}
