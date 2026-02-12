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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.checkerframework.checker.signature.qual.FullyQualifiedName;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.openrewrite.java.tree.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCompletionEngine;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.InjectBeanConstructorRefactoring;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposalWithScore;
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
	private IJavaProject project;
	private String beanId;
	private String beanType;
	private String fieldName;
	private String className;
	private double score;
	private ASTNode node;
	private int offset;

	private String prefix;
	private DocumentEdits edits;

	public BeanCompletionProposal(ASTNode node, int offset, IDocument doc, IJavaProject project, String beanId, String beanType,
			String fieldName, String className) {
		this.node = node;
		this.offset = offset;
		this.doc = doc;
		this.project = project;
		this.beanId = beanId;
		this.beanType = beanType;
		this.fieldName = fieldName;
		this.className = className;
		this.prefix = computePrefix();
		this.edits = computeEdit();
		this.score = computeJaroWinklerScore(prefix, beanId);
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
//		labelDetails.setDescription(((FullyQualifiedName) JavaType.parse(beanType)).getSimpleName());
		return labelDetails;
	}

	@Override
	public Renderable getDocumentation() {
		return Renderables.text("Inject bean `%s` of type `%s` as a constructor parameter and add corresponding field"
				.formatted(beanId, beanType));
	}

	@Override
	public Optional<Supplier<DocumentEdits>> getAdditionalEdit() {
		return Optional.of(() -> {
			try {
				return computeAdditionalEdits();
			} catch (Exception e) {
				log.error("Failed to compute additional edits for bean injection", e);
				return null;
			}
		});
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

	// ========== Additional edits via InjectBeanConstructorRefactoring ==========

	private DocumentEdits computeAdditionalEdits() throws Exception {
		String source = doc.get();

		// When the cursor is inside a constructor, the main textEdit already inserts
		// the assignment statement, so we tell the refactoring to skip it.
		boolean cursorInsideConstructor = isInsideConstructor(node);

		Map<String, String> javaCoreOptions = project.getJavaCoreOptions();
		Map<String, String> formatterOptions = javaCoreOptions.isEmpty() ? JavaCore.getOptions() : javaCoreOptions;

		CompilationUnit cu = (CompilationUnit) node.getRoot();

		InjectBeanConstructorRefactoring refactoring = new InjectBeanConstructorRefactoring(
				cu, source, beanType, fieldName, className,
				!cursorInsideConstructor, formatterOptions);

		TextEdit jdtEdit = refactoring.computeEdit();
		if (jdtEdit == null) {
			return null;
		}

		return convertToDocumentEdits(jdtEdit);
	}

	/**
	 * Convert JDT TextEdit tree into DocumentEdits
	 */
	private DocumentEdits convertToDocumentEdits(TextEdit jdtEdit) {
		DocumentEdits docEdits = new DocumentEdits(doc, false);
		convertToDocumentEditsRecursive(jdtEdit, docEdits);
		return docEdits;
	}

	private void convertToDocumentEditsRecursive(TextEdit edit, DocumentEdits docEdits) {
		if (edit.getChildrenSize() == 0) {
			if (edit instanceof ReplaceEdit re) {
				if (re.getLength() == 0) {
					docEdits.insert(re.getOffset(), re.getText());
				} else {
					docEdits.replace(re.getOffset(), re.getOffset() + re.getLength(), re.getText());
				}
			} else if (edit instanceof InsertEdit ie) {
				docEdits.insert(ie.getOffset(), ie.getText());
			} else if (edit instanceof DeleteEdit de) {
				docEdits.delete(de.getOffset(), de.getOffset() + de.getLength());
			}
		}
		for (TextEdit child : edit.getChildren()) {
			convertToDocumentEditsRecursive(child, docEdits);
		}
	}

}
