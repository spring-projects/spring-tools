/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.ConvertAutowiredFieldIntoConstructorParameter;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class AutowiredFieldIntoConstructorParameterReconciler implements JdtAstReconciler {
	
	private static final String LABEL = "Convert @Autowired field into Constructor Parameter";
	
	private QuickfixRegistry registry;

	public AutowiredFieldIntoConstructorParameterReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_CONSTRUCTOR_PARAMETER_INJECTION;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		Path sourceFile = Paths.get(docUri);
		// Check if source file belongs to non-test java sources folder
		if (IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
				.anyMatch(f -> sourceFile.startsWith(f.toPath()))) {
			final AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
			
			return Optional.of(new ASTVisitor() {

				@Override
				public boolean visit(FieldDeclaration field) {
					if (field.fragments().size() == 1) {
						Annotation annotation = ReconcileUtils.findAnnotation(annotationHierarchies, field, Annotations.AUTOWIRED,
								false);
						if (annotation != null && field.getParent() instanceof TypeDeclaration) {
							TypeDeclaration typeDecl = (TypeDeclaration) field.getParent();
							List<MethodDeclaration> constructors = Arrays.stream(typeDecl.getMethods())
									.filter(c -> c.isConstructor()).collect(Collectors.toList());

							VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) field
									.fragments().get(0);
							String fieldName = variableDeclarationFragment.getName().getIdentifier();

							if (constructors.isEmpty()) {
								context.getProblemCollector().accept(createProblem(cu, field, fieldName, docUri));
							} else if (constructors.size() == 1) {
								if (!context.isCompleteAst()) {
									throw new RequiredCompleteAstException();
								}
								if (!isAssigningField(constructors.get(0), variableDeclarationFragment.resolveBinding(),
										fieldName)) {
									context.getProblemCollector().accept(createProblem(cu, field, fieldName, docUri));
								}
							} else {
								List<MethodDeclaration> autowiredConstructors = constructors.stream()
										.filter(constr -> ReconcileUtils.findAnnotation(annotationHierarchies, constr,
												Annotations.AUTOWIRED, true) != null)
										.limit(2).collect(Collectors.toList());
								if (autowiredConstructors.size() == 1) {
									if (!context.isCompleteAst()) {
										throw new RequiredCompleteAstException();
									} else if (!isAssigningField(autowiredConstructors.get(0),
											variableDeclarationFragment.resolveBinding(), fieldName)) {
										context.getProblemCollector().accept(createProblem(cu, field, fieldName, docUri));
									}
								}
							}

						}
					}
					return true;
				}

			});
		}
		else {
			return Optional.empty();
		}
	}
	
	private ReconcileProblemImpl createProblem(CompilationUnit cu, FieldDeclaration field, String fieldName,
			URI docUri) {
		ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL, field.getStartPosition(),
				field.getLength());
		TypeDeclaration typeDecl = (TypeDeclaration) field.getParent();
		String typeFqName = (cu.getPackage() != null && cu.getPackage().getName() != null
				? cu.getPackage().getName().getFullyQualifiedName() + "."
				: "") + typeDecl.getName().getFullyQualifiedName();
		ReconcileUtils.setRewriteFixes(registry, problem,
				List.of(new FixDescriptor(ConvertAutowiredFieldIntoConstructorParameter.class.getName(), List.of(docUri.toASCIIString()), LABEL)
						.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, typeDecl, null))
						.withParameters(Map.of("classFqName", typeFqName, "fieldName", fieldName))
						.withRecipeScope(RecipeScope.NODE)));
		return problem;
	}
	
	private static boolean isAssigningField(MethodDeclaration c, IVariableBinding binding, String fieldName) {
		for (Object n : c.getBody().statements()) {
			if (n instanceof ExpressionStatement && ((ExpressionStatement) n).getExpression() instanceof Assignment) {
				Assignment assignment = (Assignment) ((ExpressionStatement) n).getExpression();
				if (assignment.getLeftHandSide() instanceof FieldAccess) {
					FieldAccess fa = (FieldAccess) assignment.getLeftHandSide();
					if (fieldName.equals(fa.getName().getIdentifier()) && fa.getExpression() instanceof ThisExpression) {
						return true;
					}
				}
				if (assignment.getLeftHandSide() instanceof SimpleName) {
					SimpleName simpleName = (SimpleName) assignment.getLeftHandSide();
					if (fieldName.equals(simpleName.getIdentifier()) && binding.isEqualTo(simpleName.resolveBinding())) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
