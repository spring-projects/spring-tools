/*******************************************************************************
 * Copyright (c) 2017, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.openrewrite.java.tree.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCompletionEngine;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposalWithScore;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.rewrite.java.InjectBeanCompletionRecipe;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * @author Udayani V
 * @author Alex Boyko
 */
public class BeanCompletionProposal implements ICompletionProposalWithScore {

	private static final Logger log = LoggerFactory.getLogger(BeanCompletionProposal.class);

	private static final String SHORT_DESCRIPTION = " - inject bean";

	private IDocument doc;
	private String beanId;
	private String beanType;
	private String fieldName;
	private String className;
	private RewriteRefactorings rewriteRefactorings;
	private double score;
	private ASTNode node;
	private int offset;

	private String prefix;
	private DocumentEdits edits;

	public BeanCompletionProposal(ASTNode node, int offset, IDocument doc, String beanId, String beanType,
			String fieldName, String className, RewriteRefactorings rewriteRefactorings) {
		this.node = node;
		this.offset = offset;
		this.doc = doc;
		this.beanId = beanId;
		this.beanType = beanType;
		this.fieldName = fieldName;
		this.className = className;
		this.rewriteRefactorings = rewriteRefactorings;
		this.prefix = computePrefix();
		this.edits = computeEdit();
		this.score = /*FuzzyMatcher.matchScore*/computeJaroWinklerScore(prefix, beanId);
	}

	@Override
	public String getLabel() {
		return beanId;
	}

	@Override
	public CompletionItemKind getKind() {
		return CompletionItemKind.Field;
	}
	
	private static double computeJaroWinklerScore(CharSequence pattern, CharSequence data) {
		return pattern.isEmpty() ? 1 / (double) Integer.MAX_VALUE : new JaroWinklerSimilarity().apply(pattern, data);
	}

	private String computePrefix() {
		String prefix = "";
		try {
			// Empty SimpleName usually comes from unresolved FieldAccess, i.e. `this.owner`
			// where `owner` field is not defined
			if (node instanceof SimpleName sn) {
				FieldAccess fa = getFieldAccessFromIncompleteThisAssignment(sn);
				if (fa != null) {
					prefix = fa.getName().toString();
				} else if (!BootJavaCompletionEngine.$MISSING$.equals(sn.toString())) {
					prefix = sn.toString();
				}
			} else if (isIncompleteThisFieldAccess()) {
				FieldAccess fa = (FieldAccess) node;
				int start = fa.getExpression().getStartPosition() + fa.getExpression().getLength();
				while (start < doc.getLength() && doc.getChar(start) != '.') {
					start++;
				}
				prefix = doc.get(start + 1, offset - start - 1);
			}
		} catch (BadLocationException e) {
			log.error("Failed to compute prefix for completion proposal", e);
		}
		return prefix;
	}
	
	private boolean isIncompleteThisFieldAccess() {
		return node instanceof FieldAccess fa && fa.getExpression() instanceof ThisExpression;
	}
	
	private FieldAccess getFieldAccessFromIncompleteThisAssignment(SimpleName sn) {
		if ((node.getLength() == 0 || BootJavaCompletionEngine.$MISSING$.equals(sn.toString()))
				&& sn.getParent() instanceof Assignment assign && assign.getLeftHandSide() instanceof FieldAccess fa
				&& fa.getExpression() instanceof ThisExpression) {
			return fa;
		}
		return null;
	}
	
	private DocumentEdits computeEdit() {
		DocumentEdits edits = new DocumentEdits(doc, false);
		if (isInsideConstructor(node)) {
			if (node instanceof Block) {
				edits.insert(offset, "this.%s = %s;".formatted(fieldName, fieldName));
			} else {
				if (node.getParent() instanceof Assignment || node.getParent() instanceof FieldAccess) {
					edits.replace(offset - prefix.length(), offset, "%s = %s;".formatted(fieldName, fieldName));
				} else {
					edits.replace(offset - prefix.length(), offset, "this.%s = %s;".formatted(fieldName, fieldName));
				}
			}
		} else {
			if (node instanceof Block) {
				edits.insert(offset, fieldName);
			} else {
				edits.replace(offset - prefix.length(), offset, fieldName);
			}
		}
		return edits;
	}

	@Override
	public DocumentEdits getTextEdit() {
		return edits;
	}

	@Override
	public String getDetail() {
		return "Autowire a bean";
	}

	@Override
	public CompletionItemLabelDetails getLabelDetails() {
		CompletionItemLabelDetails labelDetails = new CompletionItemLabelDetails();
		labelDetails.setDetail(SHORT_DESCRIPTION);
		labelDetails.setDescription(JavaType.ShallowClass.build(beanType).getClassName());
		return labelDetails;
	}

	@Override
	public Renderable getDocumentation() {
		return Renderables.text("Inject bean `%s` of type `%s` as a constructor parameter and add corresponding field"
				.formatted(beanId, beanType));
	}

	@Override
	public Optional<Command> getCommand() {
		FixDescriptor f = new FixDescriptor(InjectBeanCompletionRecipe.class.getName(), List.of(this.doc.getUri()),
				"Inject bean completions")
				.withParameters(Map.of("fullyQualifiedName", beanType, "fieldName", fieldName, "classFqName", className))
				.withRecipeScope(RecipeScope.FILE);
		return Optional.of(rewriteRefactorings.createFixCommand("Inject bean '%s'".formatted(beanId), f));
	}

	@Override
	public double getScore() {
		return score;
	}

	private boolean isInsideConstructor(ASTNode node) {
		for (ASTNode n = node; n != null && !(n instanceof CompilationUnit); n = n.getParent()) {
			if (n instanceof MethodDeclaration md) {
				return md.isConstructor() || md.isCompactConstructor();
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(beanId, beanType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BeanCompletionProposal other = (BeanCompletionProposal) obj;
		return Objects.equals(beanId, other.beanId) && Objects.equals(beanType, other.beanType);
	}
	
	

}
