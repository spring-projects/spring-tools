/*******************************************************************************
 * Copyright (c) 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

public class ChangeMethodVisibilityRefactoring implements JdtRefactoring {

	public enum Visibility {
		PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE
	}

	private final Visibility targetVisibility;
	private final int[] methodOffsets;

	public ChangeMethodVisibilityRefactoring(Visibility targetVisibility, int... methodOffsets) {
		this.targetVisibility = targetVisibility;
		this.methodOffsets = methodOffsets;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		AST ast = cu.getAST();
		for (int offset : methodOffsets) {
			MethodDeclaration method = findMethodAtOffset(cu, offset);
			if (method != null) {
				ListRewrite modifiersRewrite = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
				
				// Remove existing visibility modifiers
				Modifier existingVisibilityModifier = null;
				for (Object mod : method.modifiers()) {
					if (mod instanceof Modifier modifier) {
						if (modifier.isPublic() || modifier.isProtected() || modifier.isPrivate()) {
							existingVisibilityModifier = modifier;
							modifiersRewrite.remove(modifier, null);
							break;
						}
					}
				}

				// Add new visibility modifier if not package private
				ModifierKeyword keyword = getModifierKeyword(targetVisibility);
				if (keyword != null) {
					Modifier newModifier = ast.newModifier(keyword);
					if (existingVisibilityModifier != null) {
						modifiersRewrite.insertAfter(newModifier, existingVisibilityModifier, null);
					} else {
						modifiersRewrite.insertFirst(newModifier, null);
					}
				}
			}
		}
	}

	private ModifierKeyword getModifierKeyword(Visibility visibility) {
		switch (visibility) {
			case PUBLIC:
				return ModifierKeyword.PUBLIC_KEYWORD;
			case PROTECTED:
				return ModifierKeyword.PROTECTED_KEYWORD;
			case PRIVATE:
				return ModifierKeyword.PRIVATE_KEYWORD;
			case PACKAGE_PRIVATE:
			default:
				return null;
		}
	}

	private MethodDeclaration findMethodAtOffset(CompilationUnit cu, int offset) {
		MethodDeclaration[] result = new MethodDeclaration[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getStartPosition() == offset) {
					result[0] = node;
				}
				return result[0] == null; // stop visiting if found
			}
		});
		return result[0];
	}
}
