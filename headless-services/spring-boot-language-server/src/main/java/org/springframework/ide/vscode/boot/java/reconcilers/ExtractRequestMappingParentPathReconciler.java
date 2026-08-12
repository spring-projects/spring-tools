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
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ExtractRequestMappingParentPathRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.requestmapping.WebEndpointIndexer;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Reconciler that detects controllers whose method-level mapping annotations (e.g.
 * {@code @GetMapping}, {@code @PostMapping}) all share a common parent path, and offers
 * a quickfix to extract that common path into a class-level {@code @RequestMapping}.
 * <p>
 * If the controller already has a class-level {@code @RequestMapping} with a single literal
 * path, the common method-level prefix (if any, beyond what's already there) is merged into
 * it instead of being reported as a fresh extraction. If the class-level mapping is applied via
 * a composed/meta-annotation (not a literal {@code @RequestMapping}), or its path can't be
 * confidently resolved, the class is left alone entirely - merging into it safely isn't possible.
 * <p>
 * To keep the quickfix safe, the whole class is skipped (no problem reported) unless
 * every method-level mapping annotation has a single, literal, non-empty path. This
 * avoids silently changing the effective path of a mapping we cannot confidently rewrite
 * (e.g. a bare {@code @GetMapping} with no path, or one using multiple path values).
 *
 * @author Martin Lippert
 */
public class ExtractRequestMappingParentPathReconciler implements JdtAstReconciler {

	private static final String EXTRACT_FIX_LABEL_TEMPLATE = "Extract `%s` into class-level `@RequestMapping`";
	private static final String MERGE_FIX_LABEL_TEMPLATE = "Merge `%s` into existing class-level `@RequestMapping` (`%s` → `%s`)";

	private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
			Annotations.SPRING_REQUEST_MAPPING,
			Annotations.SPRING_GET_MAPPING,
			Annotations.SPRING_POST_MAPPING,
			Annotations.SPRING_PUT_MAPPING,
			Annotations.SPRING_DELETE_MAPPING,
			Annotations.SPRING_PATCH_MAPPING);

	private final QuickfixRegistry registry;

	public ExtractRequestMappingParentPathReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return true;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.EXTRACT_REQUEST_MAPPING_PARENT_PATH;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				if (typeDecl.isInterface()) {
					return true;
				}

				ITypeBinding typeBinding = typeDecl.resolveBinding();
				if (typeBinding == null || !annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.CONTROLLER)) {
					return true;
				}

				Annotation directClassMapping = ReconcileUtils.findAnnotation(annotationHierarchies, typeDecl, Annotations.SPRING_REQUEST_MAPPING, false);
				if (directClassMapping == null
						&& ReconcileUtils.findAnnotation(annotationHierarchies, typeDecl, Annotations.SPRING_REQUEST_MAPPING, true) != null) {
					// mapped via a composed/meta-annotation - too risky to rewrite, leave alone
					return true;
				}

				String existingClassPath = null;
				if (directClassMapping != null) {
					existingClassPath = getSingleLiteralPath(directClassMapping);
					if (existingClassPath == null) {
						// existing class-level mapping's own path can't be confidently determined
						return true;
					}
				}

				List<MethodDeclaration> mappingMethods = new ArrayList<>();
				List<String> normalizedPaths = new ArrayList<>();

				for (MethodDeclaration method : typeDecl.getMethods()) {
					Annotation mapping = findMappingAnnotation(method);
					if (mapping == null) {
						continue;
					}

					String path = getSingleLiteralPath(mapping);
					if (path == null) {
						// cannot safely determine this method's own path - bail out entirely
						// rather than risk silently changing its effective mapping
						return true;
					}

					mappingMethods.add(method);
					normalizedPaths.add(WebEndpointIndexer.combinePath("", path));
				}

				if (mappingMethods.size() < 2) {
					return true;
				}

				String commonPath = commonPathPrefix(normalizedPaths);
				if (commonPath == null) {
					return true;
				}

				boolean merging = directClassMapping != null;
				String classAnnotationPath = merging ? WebEndpointIndexer.combinePath(existingClassPath, commonPath) : commonPath;

				Annotation controllerAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, typeDecl, Annotations.CONTROLLER, true);
				ASTNode anchor = controllerAnnotation != null ? controllerAnnotation : typeDecl.getName();

				String message = merging
						? "All request mappings share the additional common parent path `%s`, which can be merged into the existing class-level `@RequestMapping` (`%s` → `%s`)"
								.formatted(commonPath, existingClassPath, classAnnotationPath)
						: "All request mappings share the common parent path `%s`".formatted(commonPath);
				ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), message,
						anchor.getStartPosition(), anchor.getLength());

				QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
				if (quickfixType != null) {
					String fixLabel = merging
							? MERGE_FIX_LABEL_TEMPLATE.formatted(commonPath, existingClassPath, classAnnotationPath)
							: EXTRACT_FIX_LABEL_TEMPLATE.formatted(commonPath);
					int[] methodOffsets = mappingMethods.stream().mapToInt(ASTNode::getStartPosition).toArray();
					JdtFixDescriptor fix = new JdtFixDescriptor(
							new ExtractRequestMappingParentPathRefactoring(typeDecl.getStartPosition(), commonPath, classAnnotationPath, methodOffsets),
							List.of(docUri.toASCIIString()),
							fixLabel);
					problem.addQuickfix(new QuickfixData<>(quickfixType, fix, fixLabel, true));
				}

				context.getProblemCollector().accept(problem);

				return true;
			}

		});
	}

	private static Annotation findMappingAnnotation(MethodDeclaration method) {
		for (Object mod : method.modifiers()) {
			if (mod instanceof Annotation a) {
				ITypeBinding type = a.resolveTypeBinding();
				if (type != null && MAPPING_ANNOTATIONS.contains(type.getQualifiedName())) {
					return a;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the single literal {@code value}/{@code path} of the given mapping
	 * annotation, or {@code null} if it has none, an unresolvable value, or more
	 * than one path value.
	 */
	private static String getSingleLiteralPath(Annotation mapping) {
		String[] values = ASTUtils.getAttribute(mapping, "value")
				.or(() -> ASTUtils.getAttribute(mapping, "path"))
				.map(expr -> ASTUtils.getExpressionValueAsArray(expr, dep -> {}))
				.orElse(null);

		if (values == null || values.length != 1 || values[0] == null || values[0].isEmpty()) {
			return null;
		}

		return values[0];
	}

	/**
	 * Returns the longest common leading sequence of path segments shared by all
	 * given (already {@code /}-normalized) paths, or {@code null} if they don't
	 * share any.
	 */
	private static String commonPathPrefix(List<String> normalizedPaths) {
		List<String[]> segmentsPerPath = normalizedPaths.stream().map(ExtractRequestMappingParentPathReconciler::splitSegments).toList();

		int minLength = segmentsPerPath.stream().mapToInt(s -> s.length).min().orElse(0);
		List<String> common = new ArrayList<>();

		for (int i = 0; i < minLength; i++) {
			String segment = segmentsPerPath.get(0)[i];
			int index = i;
			boolean allMatch = segmentsPerPath.stream().allMatch(segments -> segments[index].equals(segment));
			if (!allMatch) {
				break;
			}
			common.add(segment);
		}

		return common.isEmpty() ? null : "/" + String.join("/", common);
	}

	private static String[] splitSegments(String normalizedPath) {
		String trimmed = normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath;
		if (trimmed.isEmpty()) {
			return new String[0];
		}
		return trimmed.split("/");
	}

}
