/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleType;
import org.openrewrite.marker.Range;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class ReconcileUtils {
	
	public static Range createOpenRewriteRange(CompilationUnit cu, ASTNode node, UUID id) {
		
		int startOffset = node.getStartPosition();
		int startLine = cu.getLineNumber(startOffset);
		int startColumn = cu.getColumnNumber(startOffset);
		
		int endOffset = startOffset + node.getLength() - 1;
		int endLine = cu.getLineNumber(endOffset);
		int endColumn = cu.getColumnNumber(endOffset);
		
		Range.Position startPosition = new Range.Position(startOffset, startLine, startColumn);
		Range.Position endPosition = new Range.Position(endOffset, endLine, endColumn);
		
		return new Range(id, startPosition, endPosition);
	}
	
	public static QuickfixType getRewriteQuickFixType(QuickfixRegistry registry) {
		return registry.getQuickfixType(RewriteRefactorings.REWRITE_RECIPE_QUICKFIX);
	}
	
	public static void setRewriteFixes(QuickfixRegistry registry, ReconcileProblemImpl problem, Collection<FixDescriptor> fixDescritptors) {
		QuickfixType quickFixType = getRewriteQuickFixType(registry);
		for (FixDescriptor f : fixDescritptors) {
			problem.addQuickfix(new QuickfixData<>(quickFixType, f, f.getLabel(), f.isPreferred()));
		}
	}
	
	public static String buildLabel(String label, RecipeScope s) {
		switch (s) {
		case FILE:
			return label + " in file";
		case PROJECT:
			return label + " in project";
		default:
			return label;
		}
	}
	
	public static Annotation findAnnotation(AnnotationHierarchies annotationHiererachies, BodyDeclaration decl, String annotationFqType, boolean includeMetaHierarchy) {
		for (Iterator<?> itr = decl.modifiers().iterator(); itr.hasNext();) {
			Object mod = itr.next();
			if (mod instanceof Annotation) {
				Annotation a = (Annotation) mod;
				ITypeBinding aType = a.resolveTypeBinding();
				if (aType != null && 
						(annotationFqType.equals(aType.getQualifiedName()) || (includeMetaHierarchy && annotationHiererachies.isAnnotatedWith(aType, annotationFqType)))
				) {
					return (Annotation) mod;
				}
			}
		}
		return null;
	}

	public static ITypeBinding getDeepErasureType(ITypeBinding type) {
		for (; type != type.getErasure(); type = type.getErasure()) {}
		return type;
	}
	
	public static boolean isApplicableTypeWithoutResolving(CompilationUnit cu, Collection<String> types, Name typeNameNode) {
		String typeName = typeNameNode.getFullyQualifiedName();
		if (cu.getPackage() != null && cu.getPackage().getName() != null && types.contains(cu.getPackage().getName().getFullyQualifiedName() + "." + typeName)) {
			return true;
		}
		if (types.contains(typeName)) {
			return true;
		} else if (types.stream().anyMatch(t -> t.endsWith(typeName))) {
			for (Object im : cu.imports()) {
				ImportDeclaration importDecl = (ImportDeclaration) im;
				String importFqName = importDecl.getName().getFullyQualifiedName();
				if (importDecl.isOnDemand()) {
					if (types.contains(importFqName + "." + typeName)) {
						return true;
					}
				} else {
					String importSimpleName = getSimpleName(importFqName);
					String firstTokenOfTypeName = getFirstTokenBeforeDot(typeName);
					if (importSimpleName.equals(firstTokenOfTypeName)) {
						if (types.contains(importFqName + typeName.substring(firstTokenOfTypeName.length()))) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	public static boolean isAnyTypeUsed(CompilationUnit cu, Collection<String> types) {
		AtomicBoolean typeUsed = new AtomicBoolean(false);
		cu.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(ImportDeclaration node) {
				String fqName = node.getName().getFullyQualifiedName();
				if (types.contains(fqName)) {
					typeUsed.set(true);
				}
				return !typeUsed.get();
			}

			@Override
			public boolean visit(SimpleType node) {
				if (ReconcileUtils.isApplicableTypeWithoutResolving(cu, types, node.getName())) {
					typeUsed.set(true);
				}
				return !typeUsed.get();
			}
		});
		return typeUsed.get();
	}
	
	public static String getSimpleName(String fqName) {
		int idx = fqName.lastIndexOf('.');
		if (idx >= 0 && idx < fqName.length() - 1) {
			return fqName.substring(idx + 1);
		}
		return fqName;
	}
	
	public static String getFirstTokenBeforeDot(String fqName) {
		int idx = fqName.indexOf('.');
		if (idx > 0) {
			return fqName.substring(0, idx);
		}
		return fqName;
	}


}
