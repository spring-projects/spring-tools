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

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.Optional;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.openrewrite.marker.Range;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class ImplicitWebAnnotationNamesReconciler implements JdtAstReconciler {
	
	private static final String PROBLEM_LABEL = "Unnecessary path variable definition";
	private static final String FIX_LABEL = "Remove implicit web annotation name";
	private static final String FIX_LABEL_PLURAL = "Remove implicit web annotation names";
	
	private static final Set<String> PARAM_ANNOTATIONS = new HashSet<>(
	        Arrays.asList(
	            "PathVariable",
	            "RequestParam",
	            "RequestHeader",
	            "RequestAttribute",
	            "CookieValue",
	            "ModelAttribute",
	            "SessionAttribute"
	        )
	    );
	
	private QuickfixRegistry registry;

	public ImplicitWebAnnotationNamesReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public Boot2JavaProblemType getProblemType() {
		return Boot2JavaProblemType.WEB_ANNOTATION_NAMES;
	}
	
	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(NormalAnnotation node) {
				processWebAnnotation(node);
				return false;
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				processWebAnnotation(node);
				return false;
			}
			
			private void processWebAnnotation(Annotation a) {
				if (isApplicableWebAnnotation(a)) {

					ASTNode nodeForProblemRange = getNodeForProblemRange(a);
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, nodeForProblemRange.getStartPosition(), nodeForProblemRange.getLength());

					String uri = docUri.toASCIIString();
					Range range = ReconcileUtils.createOpenRewriteRange(cu, a, null);
					ReconcileUtils.setRewriteFixes(registry, problem, List.of(
							new FixDescriptor(org.openrewrite.java.spring.ImplicitWebAnnotationNames.class.getName(), List.of(uri), FIX_LABEL)
								.withRangeScope(range)
								.withRecipeScope(RecipeScope.NODE),
							new FixDescriptor(org.openrewrite.java.spring.ImplicitWebAnnotationNames.class.getName(), List.of(uri),
									ReconcileUtils.buildLabel(FIX_LABEL_PLURAL, RecipeScope.FILE))
								.withRecipeScope(RecipeScope.FILE),
							new FixDescriptor(org.openrewrite.java.spring.ImplicitWebAnnotationNames.class.getName(), List.of(uri),
									ReconcileUtils.buildLabel(FIX_LABEL_PLURAL, RecipeScope.PROJECT))
								.withRecipeScope(RecipeScope.PROJECT)
							));
					context.getProblemCollector().accept(problem);
				}
			}
		});
	}
	
	private static boolean isApplicableWebAnnotation(Annotation annotation) {
		if (annotation.isSingleMemberAnnotation() || annotation.isNormalAnnotation()) {
			String typeName = annotation.getTypeName().getFullyQualifiedName();
			String annotationParam = getAnnotationParameter(annotation);
			String variableName = getParameterName(annotation);
			if (PARAM_ANNOTATIONS.contains(typeName) && annotationParam != null && variableName != null) {
				if(Objects.equals(annotationParam, variableName))
					return true;
			}
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private static String getAnnotationParameter(Annotation annotation) {
		Expression value = null;
		
		if (annotation.isSingleMemberAnnotation()) {
			value = ((SingleMemberAnnotation) annotation).getValue();
		} else if (annotation.isNormalAnnotation()) {
			for (MemberValuePair pair : (List<MemberValuePair>) ((NormalAnnotation) annotation).values()) {
				String identifier = pair.getName().toString();
				value = identifier.equals("value") || identifier.equals("name") ? pair.getValue() : value;
			}
		}
		
		if (value instanceof StringLiteral) {
			return ASTUtils.getLiteralValue((StringLiteral) value);
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private static ASTNode getNodeForProblemRange(Annotation annotation) {
		if (annotation.isSingleMemberAnnotation()) {
			return ((SingleMemberAnnotation) annotation).getValue();
		} else if (annotation.isNormalAnnotation()) {
			for (MemberValuePair pair : (List<MemberValuePair>) ((NormalAnnotation) annotation).values()) {
				String identifier = pair.getName().toString();
				if (identifier.equals("value") || identifier.equals("name")) {
					return pair;
				}
			}
		}
		return annotation;
	}

	private static String getParameterName(Annotation annotation) {
		ASTNode parent = annotation.getParent();
		if (parent instanceof SingleVariableDeclaration) {
			SingleVariableDeclaration svd = (SingleVariableDeclaration) parent;
		    return svd.getName().getIdentifier();
		    
		}
		return null;
	}

}
