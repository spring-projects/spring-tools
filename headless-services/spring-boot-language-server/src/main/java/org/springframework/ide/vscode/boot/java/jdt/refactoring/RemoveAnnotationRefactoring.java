/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * A JDT-based refactoring that removes annotations identified by their start positions.
 * <p>
 * Pass one or more annotation offsets to remove specific annotations.
 * When used with a single offset this corresponds to a node-scoped quickfix.
 * When used with multiple offsets (all occurrences in a file) this corresponds
 * to a file-scoped "fix all" quickfix.
 */
public class RemoveAnnotationRefactoring implements JdtRefactoring {

	private final int[] annotationOffsets;

	/**
	 * @param annotationOffsets start positions of the annotation nodes to remove
	 */
	public RemoveAnnotationRefactoring(int... annotationOffsets) {
		this.annotationOffsets = annotationOffsets;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		Set<String> fqnsToCheck = new HashSet<>();
		
		for (int offset : annotationOffsets) {
			Annotation annotation = findAnnotationAtOffset(cu, offset);
			if (annotation != null) {
				ASTNode parent = annotation.getParent();
				ChildListPropertyDescriptor property = (ChildListPropertyDescriptor) annotation.getLocationInParent();
				ListRewrite modifiersRewrite = rewrite.getListRewrite(parent, property);
				modifiersRewrite.remove(annotation, null);
				
				ITypeBinding binding = annotation.resolveTypeBinding();
				if (binding != null) {
					fqnsToCheck.add(binding.getErasure().getQualifiedName());
				}
			}
		}
		
		if (!fqnsToCheck.isEmpty()) {
			JdtRefactorUtils.removeImports(cu, rewrite, fqnsToCheck.toArray(new String[fqnsToCheck.size()]));
		}
	}

	private static Annotation findAnnotationAtOffset(CompilationUnit cu, int offset) {
		ASTNode node = NodeFinder.perform(cu, offset, 0);
		while (node != null) {
			if (node instanceof Annotation a) {
				int start = a.getStartPosition();
				int end = a.getTypeName().getStartPosition() + a.getTypeName().getLength();
				if (offset >= start && offset <= end) {
					return a;
				}
				return null;
			}
			node = node.getParent();
		}
		return null;
	}

}
