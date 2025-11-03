/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom
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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
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
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(MethodInvocation node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(MethodDeclaration node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public void endVisit(MethodDeclaration node) {
		for (ASTVisitor astVisitor : visitors) {
			astVisitor.endVisit(node);
		}
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(SingleVariableDeclaration node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(NormalAnnotation node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}

	@Override
	public boolean visit(MarkerAnnotation node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(ImportDeclaration node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}

	@Override
	public boolean visit(SimpleType node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(QualifiedName node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}
	
	@Override
	public boolean visit(ReturnStatement node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}

	/*
	 * Needed for the JDT LS semantic tokens workaround
	 */
	@Override
	public boolean visit(SimpleName node) {
		boolean result = true;
		if (!checkOffset(node)) {
			return false;
		}
		for (ASTVisitor astVisitor : visitors) {
			result |= astVisitor.visit(node);
		}
		return result;
	}

	private boolean checkOffset(ASTNode n) {
		return (startOffset < 0 || n.getStartPosition() >= startOffset)
				|| (endOffset <0 || n.getStartPosition() + n.getLength() < endOffset);
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

}
