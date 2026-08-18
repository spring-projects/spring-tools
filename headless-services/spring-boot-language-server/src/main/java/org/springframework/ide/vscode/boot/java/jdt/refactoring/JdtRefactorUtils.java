/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Shared utility methods for JDT-based refactorings.
 *
 * @author Alex Boyko
 */
public final class JdtRefactorUtils {

	public static void removeImports(CompilationUnit cu, ASTRewrite rewrite, String... fqns) {
		Set<String> fqnsToCheck = new HashSet<>();
		Map<String, List<ImportDeclaration>> fqnToImports = new HashMap<>();
		
		for (String fqn : fqns) {
			for (Object importObj : cu.imports()) {
				ImportDeclaration imp = (ImportDeclaration) importObj;
				if (!imp.isOnDemand() && imp.getName().getFullyQualifiedName().equals(fqn)) {
					fqnsToCheck.add(fqn);
					fqnToImports.computeIfAbsent(fqn, k -> new ArrayList<>()).add(imp);
				}
			}
		}
		
		if (fqnsToCheck.isEmpty()) {
			return;
		}

		Set<String> usedFqns = getUsedTypes(cu, rewrite, fqnsToCheck);
		
		ListRewrite importsRewrite = null;
		for (String fqn : fqnsToCheck) {
			if (!usedFqns.contains(fqn)) {
				if (importsRewrite == null) {
					importsRewrite = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
				}
				for (ImportDeclaration imp : fqnToImports.get(fqn)) {
					importsRewrite.remove(imp, null);
				}
			}
		}
	}

	private static Set<String> getUsedTypes(CompilationUnit cu, ASTRewrite rewrite, Set<String> fqnsToCheck) {
		Set<String> usedFqns = new HashSet<>();
		
		cu.accept(new ASTVisitor() {
			
			private void checkTypeRef(Name node) {
				if (usedFqns.size() == fqnsToCheck.size()) return; // All found
				
				if (node == null) return;
				
				// Get the leftmost qualifier
				while (node.isQualifiedName()) {
					node = ((QualifiedName) node).getQualifier();
				}
				
				IBinding binding = node.resolveBinding();
				String fqn = null;
				
				if (binding instanceof ITypeBinding) {
					fqn = ((ITypeBinding) binding).getErasure().getQualifiedName();
				}
				
				if (fqn != null && fqnsToCheck.contains(fqn) && !usedFqns.contains(fqn)) {
					if (survivesRewrite(node, rewrite)) {
						usedFqns.add(fqn);
					}
				}
			}

			@Override
			public boolean visit(SimpleName node) {
				// If we get here directly, it might be a static reference
				if (!isInsideImport(node)) {
					checkTypeRef(node);
				}
				return true;
			}
			
		});
		return usedFqns;
	}

	private static boolean isInsideImport(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof ImportDeclaration) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	private static boolean survivesRewrite(ASTNode node, ASTRewrite rewrite) {
		ASTNode current = node;
		while (current != null) {
			ASTNode parent = current.getParent();
			if (parent != null) {
				StructuralPropertyDescriptor prop = current.getLocationInParent();
				if (prop != null) {
					if (prop.isChildListProperty()) {
						ListRewrite listRewrite = rewrite.getListRewrite(parent, (ChildListPropertyDescriptor) prop);
						
						List<?> originalList = listRewrite.getOriginalList();
						List<?> rewrittenList = listRewrite.getRewrittenList();
						
						boolean inOriginal = false;
						for (Object o : originalList) {
							if (o == current) {
								inOriginal = true;
								break;
							}
						}
						
						boolean inRewritten = false;
						for (Object o : rewrittenList) {
							if (o == current) {
								inRewritten = true;
								break;
							}
						}
						
						if (inOriginal && !inRewritten) {
							return false; // Removed from list
						}
					} else {
						Object rewrittenNode = rewrite.get(parent, prop);
						if (rewrittenNode != current) {
							return false; // Replaced or removed
						}
					}
				}
			}
			current = parent;
		}
		return true;
	}

	/**
	 * Add an import for the given {@link ClassType} to the compilation unit, unless
	 * the import is unnecessary.
	 * <p>
	 * An import is considered unnecessary when any of the following is true:
	 * <ul>
	 *   <li>The type is in the {@code java.lang} package</li>
	 *   <li>The type is in the default (unnamed) package</li>
	 *   <li>The type is in the same package as the compilation unit</li>
	 *   <li>An exact import for the type already exists</li>
	 *   <li>A wildcard (on-demand) import already covers the type's package</li>
	 * </ul>
	 * <p>
	 * When an import is added, it is inserted in lexicographic sorted order among
	 * the existing imports.
	 *
	 * @param rewrite   the {@link ASTRewrite} to record the change
	 * @param ast       the AST factory
	 * @param cu        the compilation unit
	 * @param className the class name to import
	 */
	public static void addImport(ASTRewrite rewrite, AST ast, CompilationUnit cu, ClassType className) {
		String packageName = className.getPackageName();

		// Don't add import for java.lang types
		if ("java.lang".equals(packageName)) {
			return;
		}

		// Don't add import for default package types
		if (packageName.isEmpty()) {
			return;
		}

		// Don't add import if type is in the same package
		if (cu.getPackage() != null) {
			String cuPackage = cu.getPackage().getName().getFullyQualifiedName();
			if (cuPackage.equals(packageName)) {
				return;
			}
		}

		String fullyQualifiedName = className.getFullyQualifiedName();

		// Check if import already exists (exact or wildcard)
		for (Object importObj : cu.imports()) {
			ImportDeclaration imp = (ImportDeclaration) importObj;
			if (imp.isOnDemand()) {
				// Wildcard import like "import java.util.*"
				if (imp.getName().getFullyQualifiedName().equals(packageName)) {
					return;
				}
			} else if (imp.getName().getFullyQualifiedName().equals(fullyQualifiedName)) {
				return;
			}
		}

		ImportDeclaration importDecl = ast.newImportDeclaration();
		importDecl.setName(ast.newName(fullyQualifiedName));

		ListRewrite importsRewrite = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);

		// Insert in sorted order
		ImportDeclaration insertBefore = null;
		for (Object importObj : cu.imports()) {
			ImportDeclaration existing = (ImportDeclaration) importObj;
			if (existing.getName().getFullyQualifiedName().compareTo(fullyQualifiedName) > 0) {
				insertBefore = existing;
				break;
			}
		}

		if (insertBefore != null) {
			importsRewrite.insertBefore(importDecl, insertBefore, null);
		} else {
			importsRewrite.insertLast(importDecl, null);
		}
	}

	/**
	 * Returns the {@code value} or {@code path} member of the given annotation,
	 * whichever is present, or {@code null} if neither is set.
	 */
	public static MemberValuePair findValueOrPathMemberValuePair(NormalAnnotation annotation) {
		for (Object o : annotation.values()) {
			MemberValuePair pair = (MemberValuePair) o;
			String name = pair.getName().getIdentifier();
			if ("value".equals(name) || "path".equals(name)) {
				return pair;
			}
		}
		return null;
	}

	/**
	 * Creates a {@link MarkerAnnotation} (no arguments) with the same type name as
	 * {@code original}, e.g. to replace a {@link org.eclipse.jdt.core.dom.SingleMemberAnnotation}
	 * or {@link NormalAnnotation} once its only argument has been stripped.
	 */
	public static MarkerAnnotation markerAnnotationLike(AST ast, Annotation original) {
		MarkerAnnotation marker = ast.newMarkerAnnotation();
		marker.setTypeName((Name) ASTNode.copySubtree(ast, original.getTypeName()));
		return marker;
	}

	/**
	 * Creates a {@link StringLiteral} with the given raw (unescaped) value.
	 */
	public static StringLiteral newStringLiteral(AST ast, String value) {
		StringLiteral literal = ast.newStringLiteral();
		literal.setLiteralValue(value);
		return literal;
	}

	/**
	 * Convert a JDT {@link org.eclipse.text.edits.TextEdit} tree into an LSP
	 * {@link TextDocumentEdit}, using the given {@link TextDocument} for
	 * offset-to-position translation and document identity (URI + version).
	 */
	public static TextDocumentEdit toLspTextDocumentEdit(org.eclipse.text.edits.TextEdit jdtEdit,
			TextDocument doc) throws BadLocationException {
		List<TextEdit> lspEdits = new ArrayList<>();
		collectLspEdits(jdtEdit, doc, lspEdits);

		TextDocumentEdit docEdit = new TextDocumentEdit();
		docEdit.setTextDocument(new VersionedTextDocumentIdentifier(doc.getUri(), doc.getVersion()));
		docEdit.setEdits(lspEdits.stream()
				.map(e -> Either.<TextEdit, org.eclipse.lsp4j.SnippetTextEdit>forLeft(e))
				.toList());
		return docEdit;
	}

	private static void collectLspEdits(org.eclipse.text.edits.TextEdit jdtEdit, TextDocument doc,
			List<TextEdit> lspEdits) throws BadLocationException {
		if (jdtEdit.hasChildren()) {
			for (org.eclipse.text.edits.TextEdit child : jdtEdit.getChildren()) {
				collectLspEdits(child, doc, lspEdits);
			}
		}
		else if (jdtEdit instanceof ReplaceEdit re) {
			TextEdit lspEdit = new TextEdit();
			lspEdit.setRange(doc.toRange(re.getOffset(), re.getLength()));
			lspEdit.setNewText(re.getText());
			lspEdits.add(lspEdit);
		}
		else if (jdtEdit instanceof InsertEdit ie) {
			TextEdit lspEdit = new TextEdit();
			lspEdit.setRange(doc.toRange(ie.getOffset(), 0));
			lspEdit.setNewText(ie.getText());
			lspEdits.add(lspEdit);
		}
		else if (jdtEdit instanceof DeleteEdit de) {
			TextEdit lspEdit = new TextEdit();
			lspEdit.setRange(doc.toRange(de.getOffset(), de.getLength()));
			lspEdit.setNewText("");
			lspEdits.add(lspEdit);
		}
	}

	/**
	 * Returns the simple class name from a fully qualified name.
	 * For example, {@code "org.example.Foo"} → {@code "Foo"}.
	 */
	public static String extractSimpleName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
	}

	/**
	 * Returns the package name from a fully qualified name.
	 * For example, {@code "org.example.Foo"} → {@code "org.example"}.
	 * Returns an empty string for default-package types.
	 */
	public static String extractPackageName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
	}

	/**
	 * Escapes a raw string value so it can be safely embedded inside a Java text
	 * block literal.
	 * <p>
	 * Backslashes must be doubled because Java recognises escape sequences (e.g.
	 * {@code \'} → {@code '}) inside text blocks, which would silently drop a bare
	 * {@code \} that appears before a quotable character.
	 *
	 * @param value the raw runtime string value to escape
	 * @return the escaped string, safe to embed between {@code """} delimiters
	 */
	public static String escapeForTextBlock(String value) {
		return value.replace("\\", "\\\\");
	}

	/**
	 * Finds the nearest enclosing node of the given {@code type} at {@code offset},
	 * e.g. the {@link org.eclipse.jdt.core.dom.MethodDeclaration} or
	 * {@link org.eclipse.jdt.core.dom.TypeDeclaration} a quickfix offset was recorded against.
	 */
	public static <T extends ASTNode> T findAncestorAtOffset(CompilationUnit cu, int offset, Class<T> type) {
		ASTNode node = NodeFinder.perform(cu, offset, 0);
		while (node != null && !type.isInstance(node)) {
			node = node.getParent();
		}
		return type.cast(node);
	}

	/**
	 * Finds an annotation among {@code declaration}'s modifiers whose type name matches
	 * {@code annotationFqn}, either fully qualified or as a simple name (e.g. when imported).
	 * Matches by name rather than resolved binding so this also works against ASTs parsed
	 * without a classpath (as JDT refactoring unit tests typically do).
	 */
	public static Annotation findAnnotationByName(BodyDeclaration declaration, String annotationFqn) {
		String simpleName = extractSimpleName(annotationFqn);
		for (Object mod : declaration.modifiers()) {
			if (mod instanceof Annotation a) {
				String name = a.getTypeName().getFullyQualifiedName();
				if (name.equals(annotationFqn) || name.equals(simpleName)) {
					return a;
				}
			}
		}
		return null;
	}

	/**
	 * Creates a copy of {@code original} with the same argument list (marker, single-member
	 * or normal annotation) but a different simple type name. Useful when merging several
	 * annotations into one that still needs to carry over a value from one of the originals,
	 * e.g. a bean-name attribute.
	 */
	public static Annotation copyAnnotationWithNewTypeName(AST ast, Annotation original, String newSimpleTypeName) {
		Annotation copy;
		if (original.isMarkerAnnotation()) {
			copy = ast.newMarkerAnnotation();
		} else if (original.isSingleMemberAnnotation()) {
			SingleMemberAnnotation sma = ast.newSingleMemberAnnotation();
			sma.setValue((Expression) ASTNode.copySubtree(ast, ((SingleMemberAnnotation) original).getValue()));
			copy = sma;
		} else {
			NormalAnnotation source = (NormalAnnotation) original;
			if (source.values().isEmpty()) {
				copy = ast.newMarkerAnnotation();
			} else {
				NormalAnnotation normal = ast.newNormalAnnotation();
				@SuppressWarnings("unchecked")
				List<MemberValuePair> values = normal.values();
				for (Object o : source.values()) {
					MemberValuePair pair = (MemberValuePair) o;
					MemberValuePair newPair = ast.newMemberValuePair();
					newPair.setName(ast.newSimpleName(pair.getName().getIdentifier()));
					newPair.setValue((Expression) ASTNode.copySubtree(ast, pair.getValue()));
					values.add(newPair);
				}
				copy = normal;
			}
		}
		copy.setTypeName(ast.newSimpleName(newSimpleTypeName));
		return copy;
	}

	/**
	 * Replaces the earliest of {@code toMerge} (by source position) with {@code newAnnotation}
	 * and removes the rest, all within the same {@code modifiersProperty} list on
	 * {@code declaration}. Every node in {@code toMerge} is added to {@code exactRangeNodes},
	 * so that a {@link TargetSourceRangeComputer} obtained from
	 * {@link #exactRangeSourceComputer(Set)} treats their source ranges as exact (not
	 * extending onto adjacent, unclaimed comments).
	 */
	public static void replaceWithMergedAnnotation(ASTRewrite rewrite, BodyDeclaration declaration,
			ChildListPropertyDescriptor modifiersProperty, List<Annotation> toMerge, Annotation newAnnotation,
			Set<ASTNode> exactRangeNodes) {
		Annotation earliest = toMerge.stream().min(Comparator.comparingInt(ASTNode::getStartPosition)).orElseThrow();
		exactRangeNodes.addAll(toMerge);

		ListRewrite modifiersRewrite = rewrite.getListRewrite(declaration, modifiersProperty);
		modifiersRewrite.replace(earliest, newAnnotation, null);
		for (Annotation a : toMerge) {
			if (a != earliest) {
				modifiersRewrite.remove(a, null);
			}
		}
	}

	/**
	 * A {@link TargetSourceRangeComputer} that treats the source range of any node in
	 * {@code exactRangeNodes} as exactly its own start position and length, rather than
	 * JDT's default of extending onto adjacent, unclaimed leading comments (e.g. a line
	 * comment sitting between a Javadoc and the annotations it documents). Without this,
	 * replacing/removing one of several merged annotations can delete such a comment along
	 * with it.
	 */
	public static TargetSourceRangeComputer exactRangeSourceComputer(Set<ASTNode> exactRangeNodes) {
		return new TargetSourceRangeComputer() {
			@Override
			public SourceRange computeSourceRange(ASTNode node) {
				return exactRangeNodes.contains(node)
						? new SourceRange(node.getStartPosition(), node.getLength())
						: super.computeSourceRange(node);
			}
		};
	}

}
