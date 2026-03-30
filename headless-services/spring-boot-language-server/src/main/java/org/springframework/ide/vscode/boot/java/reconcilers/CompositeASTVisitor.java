/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class CompositeASTVisitor extends ASTVisitor {
	
	List<ASTVisitor> visitors = new ArrayList<>();
	private int startOffset = -1;
	private int endOffset = -1;

	public void add(ASTVisitor visitor) {
		visitors.add(visitor);
	}
	
	@Override
	public boolean visit(TypeDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(RecordDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(MethodInvocation node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(MethodDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public void endVisit(MethodDeclaration node) {
		for (ASTVisitor astVisitor : visitors) {
			astVisitor.endVisit(node);
		}
	}

	@Override
	public void endVisit(CompilationUnit node) {
		for (ASTVisitor astVisitor : visitors) {
			astVisitor.endVisit(node);
		}
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(SingleVariableDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(NormalAnnotation node) {
		return delegateVisit(node, v -> v.visit(node));
	}

	@Override
	public boolean visit(MarkerAnnotation node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(ImportDeclaration node) {
		return delegateVisit(node, v -> v.visit(node));
	}

	@Override
	public boolean visit(SimpleType node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(QualifiedName node) {
		return delegateVisit(node, v -> v.visit(node));
	}
	
	@Override
	public boolean visit(ReturnStatement node) {
		return delegateVisit(node, v -> v.visit(node));
	}

	/*
	 * Needed for the JDT LS semantic tokens workaround
	 */
	@Override
	public boolean visit(SimpleName node) {
		return delegateVisit(node, v -> v.visit(node));
	}

	private boolean delegateVisit(ASTNode node, Function<ASTVisitor, Boolean> visitFn) {
		if (!checkOffset(node)) {
			return false;
		}
		boolean result = false;
		for (ASTVisitor visitor : visitors) {
			result |= visitFn.apply(visitor);
		}
		return result;
	}

	public int getStartOffset() {
		return startOffset;
	}

	public void setStartOffset(int startOffset) {
		this.startOffset = startOffset;
	}

	public int getEndOffset() {
		return endOffset;
	}

	public void setEndOffset(int endOffset) {
		this.endOffset = endOffset;
	}

	private boolean checkOffset(ASTNode n) {
		return (startOffset < 0 || n.getStartPosition() >= startOffset)
				|| (endOffset <0 || n.getStartPosition() + n.getLength() < endOffset);
	}

}
