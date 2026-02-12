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
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * A self-contained JDT-based refactoring that injects a Spring bean into a class
 * via constructor parameter injection.
 * <p>
 * This refactoring performs three structural modifications:
 * <ol>
 *   <li>Adds a {@code private final} field declaration for the bean type</li>
 *   <li>Creates a new constructor (or modifies an existing one) with the bean
 *       as a parameter and adds the {@code this.field = field} assignment</li>
 *   <li>Adds the necessary import statement for the bean type</li>
 * </ol>
 * <p>
 * The refactoring operates on an already-parsed {@link CompilationUnit} and the
 * original source text, and returns a JDT {@link TextEdit}. It is independent of
 * the LSP completion infrastructure and can be reused by quick fixes, code actions,
 * commands, etc.
 * <p>
 * Usage:
 * <pre>
 * TextEdit edit = new InjectBeanConstructorRefactoring(
 *         cu, source, fullyQualifiedBeanType, fieldName, targetClassName,
 *         addFieldAssignment, formatterOptions
 * ).computeEdit();
 * </pre>
 *
 * @author Alex Boyko
 */
public class InjectBeanConstructorRefactoring {

	private final CompilationUnit cu;
	private final String source;
	private final String fullyQualifiedBeanType;
	private final String fieldName;
	private final String targetClassFqName;
	private final boolean addFieldAssignment;
	private final Map<String, String> formatterOptions;

	/**
	 * Create a new bean constructor injection refactoring.
	 *
	 * @param cu                   the already-parsed {@link CompilationUnit}. The caller provides
	 *                             it — in the completion flow it comes from the CU cache; in tests
	 *                             it can be a simple {@code ASTParser}-created CU.
	 * @param source               the full Java source text of the compilation unit. Required because
	 *                             the DOM {@link CompilationUnit} does not retain the original source
	 *                             text, and {@link ASTRewrite#rewriteAST(org.eclipse.jface.text.IDocument, Map)}
	 *                             needs it to compute correct text edits.
	 * @param fullyQualifiedBeanType the fully qualified type name of the bean to inject
	 *                               (e.g. {@code "org.springframework.samples.petclinic.owner.OwnerRepository"})
	 * @param fieldName            the desired field name for the injected bean
	 * @param targetClassFqName    the fully qualified name of the class to inject the bean into
	 * @param addFieldAssignment   whether to add the {@code this.field = field} assignment
	 *                             in the constructor body. Pass {@code false} when the caller
	 *                             handles the assignment separately (e.g. as the primary text edit
	 *                             in a completion proposal when the cursor is inside a constructor)
	 * @param formatterOptions     JavaCore formatter options controlling indentation, tab/space
	 *                             settings, etc. These are passed directly to
	 *                             {@link ASTRewrite#rewriteAST(org.eclipse.jface.text.IDocument, Map)}.
	 *                             A sensible default can be obtained via {@link JavaCore#getOptions()}.
	 */
	public InjectBeanConstructorRefactoring(
			CompilationUnit cu,
			String source,
			String fullyQualifiedBeanType,
			String fieldName,
			String targetClassFqName,
			boolean addFieldAssignment,
			Map<String, String> formatterOptions) {
		this.cu = cu;
		this.source = source;
		this.fullyQualifiedBeanType = fullyQualifiedBeanType;
		this.fieldName = fieldName;
		this.targetClassFqName = targetClassFqName;
		this.addFieldAssignment = addFieldAssignment;
		this.formatterOptions = formatterOptions;
	}

	/**
	 * Compute the JDT {@link TextEdit} representing all source modifications.
	 *
	 * @return a {@link TextEdit} that can be applied to the original source text,
	 *         or {@code null} if the target class could not be found
	 * @throws Exception if the source cannot be parsed or the rewrite fails
	 */
	public TextEdit computeEdit() throws Exception {
		AST ast = cu.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);

		TypeDeclaration targetClass = findClassByName(cu, targetClassFqName);
		if (targetClass == null) {
			return null;
		}

		// Parse the (possibly parameterized) type string using the type model
		JavaType javaType = JavaType.parse(fullyQualifiedBeanType);

		// Step 1: Add private final field
		Type fieldType = javaType.toType(ast);
		FieldDeclaration newField = addField(rewrite, ast, targetClass, fieldType);

		// Step 2: Find or create constructor and add parameter + assignment
		MethodDeclaration constructor = findConstructor(targetClass);
		if (constructor == null) {
			Type ctorParamType = javaType.toType(ast);
			createConstructor(rewrite, ast, targetClass, ctorParamType, newField);
		} else {
			Type ctorParamType = javaType.toType(ast);
			addParameterToConstructor(rewrite, ast, constructor, ctorParamType);
			if (addFieldAssignment) {
				addFieldAssignment(rewrite, ast, constructor);
			}
		}

		// Step 3: Add imports for all referenced class types (sorted for correct insertion order)
		List<ClassName> classNames = javaType.getAllClassNames();
		List<String> sortedImports = new ArrayList<>();
		for (ClassName cn : classNames) {
			sortedImports.add(cn.getFullyQualifiedName());
		}
		sortedImports.sort(String::compareTo);
		for (String fqName : sortedImports) {
			addImport(rewrite, ast, cu, fqName);
		}

		// Generate Eclipse TextEdit
		Document jdtDoc = new Document(source);
		return rewrite.rewriteAST(jdtDoc, formatterOptions);
	}

	// ========== Class lookup ==========

	private static TypeDeclaration findClassByName(CompilationUnit cu, String fullyQualifiedName) {
		String simpleName = fullyQualifiedName.contains(".")
				? fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1)
				: fullyQualifiedName;

		for (Object type : cu.types()) {
			if (type instanceof TypeDeclaration td) {
				TypeDeclaration found = findClassByNameRecursive(td, simpleName);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static TypeDeclaration findClassByNameRecursive(TypeDeclaration td, String simpleName) {
		if (td.getName().getIdentifier().equals(simpleName)) {
			return td;
		}
		for (TypeDeclaration nested : td.getTypes()) {
			TypeDeclaration found = findClassByNameRecursive(nested, simpleName);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	// ========== Field ==========

	/**
	 * Add a {@code private final} field declaration to the class.
	 *
	 * @return the new (or existing) field AST node, used as an insertion anchor for the constructor
	 */
	@SuppressWarnings("unchecked")
	private FieldDeclaration addField(ASTRewrite rewrite, AST ast, TypeDeclaration typeDecl, Type type) {
		// Check if field already exists
		for (FieldDeclaration field : typeDecl.getFields()) {
			for (Object fragObj : field.fragments()) {
				VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
				if (frag.getName().getIdentifier().equals(fieldName)) {
					return field;
				}
			}
		}

		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(fieldName));

		FieldDeclaration fieldDecl = ast.newFieldDeclaration(fragment);
		fieldDecl.setType(type);
		fieldDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		fieldDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));

		ListRewrite membersRewrite = rewrite.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

		// Insert before the first existing member
		if (!typeDecl.bodyDeclarations().isEmpty()) {
			membersRewrite.insertBefore(fieldDecl, (ASTNode) typeDecl.bodyDeclarations().get(0), null);
		} else {
			membersRewrite.insertFirst(fieldDecl, null);
		}

		return fieldDecl;
	}

	// ========== Constructor ==========

	private static MethodDeclaration findConstructor(TypeDeclaration typeDecl) {
		for (MethodDeclaration method : typeDecl.getMethods()) {
			if (method.isConstructor()) {
				return method;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void createConstructor(ASTRewrite rewrite, AST ast, TypeDeclaration typeDecl,
			Type paramType, FieldDeclaration newField) {
		MethodDeclaration constructor = ast.newMethodDeclaration();
		constructor.setConstructor(true);
		constructor.setName(ast.newSimpleName(typeDecl.getName().getIdentifier()));

		// Add parameter
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(paramType);
		param.setName(ast.newSimpleName(fieldName));
		constructor.parameters().add(param);

		// Create body with assignment: this.fieldName = fieldName;
		Block body = ast.newBlock();
		constructor.setBody(body);

		Assignment assignment = ast.newAssignment();
		FieldAccess fieldAccess = ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(ast.newSimpleName(fieldName));
		assignment.setLeftHandSide(fieldAccess);
		assignment.setRightHandSide(ast.newSimpleName(fieldName));

		ExpressionStatement assignStmt = ast.newExpressionStatement(assignment);
		body.statements().add(assignStmt);

		ListRewrite membersRewrite = rewrite.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

		// Insert after the last field declaration (including the newly added one)
		FieldDeclaration lastField = null;
		for (Object bodyDecl : typeDecl.bodyDeclarations()) {
			if (bodyDecl instanceof FieldDeclaration fd) {
				lastField = fd;
			}
		}

		if (lastField != null) {
			membersRewrite.insertAfter(constructor, lastField, null);
		} else {
			// No existing fields — insert after the newly added field node
			membersRewrite.insertAfter(constructor, newField, null);
		}
	}

	private void addParameterToConstructor(ASTRewrite rewrite, AST ast, MethodDeclaration constructor,
			Type paramType) {
		// Check if parameter already exists
		for (Object paramObj : constructor.parameters()) {
			if (paramObj instanceof SingleVariableDeclaration svd) {
				if (svd.getName().getIdentifier().equals(fieldName)) {
					return;
				}
			}
		}

		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(paramType);
		param.setName(ast.newSimpleName(fieldName));

		ListRewrite paramsRewrite = rewrite.getListRewrite(constructor, MethodDeclaration.PARAMETERS_PROPERTY);
		paramsRewrite.insertLast(param, null);
	}

	private void addFieldAssignment(ASTRewrite rewrite, AST ast, MethodDeclaration constructor) {
		// Check if assignment already exists
		if (constructor.getBody() != null) {
			for (Object stmtObj : constructor.getBody().statements()) {
				if (stmtObj instanceof ExpressionStatement es
						&& es.getExpression() instanceof Assignment a
						&& a.getLeftHandSide() instanceof FieldAccess fa
						&& fa.getExpression() instanceof ThisExpression
						&& fa.getName().getIdentifier().equals(fieldName)) {
					return;
				}
			}
		}

		Assignment assignment = ast.newAssignment();
		FieldAccess fieldAccess = ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(ast.newSimpleName(fieldName));
		assignment.setLeftHandSide(fieldAccess);
		assignment.setRightHandSide(ast.newSimpleName(fieldName));

		ExpressionStatement assignStmt = ast.newExpressionStatement(assignment);

		ListRewrite bodyRewrite = rewrite.getListRewrite(constructor.getBody(), Block.STATEMENTS_PROPERTY);
		bodyRewrite.insertLast(assignStmt, null);
	}

	// ========== Import ==========

	private static void addImport(ASTRewrite rewrite, AST ast, CompilationUnit cu, String fullyQualifiedName) {
		// Don't add import for java.lang types
		if (fullyQualifiedName.startsWith("java.lang.") && fullyQualifiedName.indexOf('.', 10) == -1) {
			return;
		}

		// Don't add import if type is in the same package
		if (cu.getPackage() != null) {
			String packageName = cu.getPackage().getName().getFullyQualifiedName();
			String typePackage = fullyQualifiedName.contains(".")
					? fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf('.'))
					: "";
			if (packageName.equals(typePackage)) {
				return;
			}
		}

		// Check if import already exists
		for (Object importObj : cu.imports()) {
			ImportDeclaration imp = (ImportDeclaration) importObj;
			if (imp.getName().getFullyQualifiedName().equals(fullyQualifiedName)) {
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

}
