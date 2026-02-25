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

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.AddOrUpdateAnnotationAttribute;
import org.openrewrite.java.spring.boot2.AddConfigurationAnnotationIfBeansPresent;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class AddConfigurationIfBeansPresentReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "'@Configuration' is missing on a class defining Spring Beans";
	private static final String FIX_LABEL = "Add missing '@Configuration' annotations over classes";

	private final QuickfixRegistry quickfixRegistry;
	private final SpringMetamodelIndex springIndex;

	public AddConfigurationIfBeansPresentReconciler(QuickfixRegistry quickfixRegistry, SpringMetamodelIndex springIndex) {
		this.quickfixRegistry = quickfixRegistry;
		this.springIndex = springIndex;
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByName(project, "spring-context");
		return version != null && version.compareTo(new Version(3, 0, 0, null)) >= 0;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration classDecl) {
				if (isApplicableClass(project, cu, classDecl, context)) {
					SimpleName nameAst = classDecl.getName();
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
							nameAst.getStartPosition(), nameAst.getLength());
					
					String configId = AddConfigurationAnnotationIfBeansPresent.class.getName();

					List<FixDescriptor> fixes = new ArrayList<>();
					fixes.add(new FixDescriptor(configId, List.of(docUri.toASCIIString()),
							ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.FILE))
							.withRecipeScope(RecipeScope.FILE));
					fixes.add(new FixDescriptor(configId, List.of(docUri.toASCIIString()),
							ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.PROJECT))
							.withRecipeScope(RecipeScope.PROJECT));

					String beanClassName = ReconcileUtils.getDeepErasureType(classDecl.resolveBinding()).getQualifiedName();
					Bean[] beans = springIndex != null ? springIndex.getBeansOfProject(project.getElementName()) : null;
					if (beans != null && beans.length > 0) {
						createFixesForClientAnnotation(fixes, beans, beanClassName, Annotations.FEIGN_CLIENT);
						createFixesForClientAnnotation(fixes, beans, beanClassName, Annotations.LOAD_BALANCER_CLIENT);
					}

					ReconcileUtils.setRewriteFixes(quickfixRegistry, problem, fixes);

					context.getProblemCollector().accept(problem);
				}
				return true;
			}

		};
	}

	private boolean isApplicableClass(IJavaProject project, CompilationUnit cu, TypeDeclaration classDecl, ReconcilingContext context) {
		if (classDecl.isInterface()) {
			return false;
		}

		if (Modifier.isAbstract(classDecl.getModifiers())) {
			return false;
		}

		boolean isStatic = Modifier.isStatic(classDecl.getModifiers());

		if (!isStatic) {
			// no static keyword? check if it is top level class in the CU

			for (ASTNode p = classDecl.getParent(); p != cu && p != null; p = p.getParent()) {
				if (p instanceof TypeDeclaration) {
					return false;
				}
			}
		}

		// check if '@Configuration' is already over the class
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		for (Iterator<?> itr = classDecl.modifiers().iterator(); itr.hasNext();) {
			Object mod = itr.next();
			if (mod instanceof Annotation) {
				Annotation a = (Annotation) mod;
				if (annotationHierarchies.isAnnotatedWith(a.resolveAnnotationBinding(), Annotations.CONFIGURATION)) {
					// Found '@Configuration' annotation
					return false;
				}
			}
		}
		
		// No '@Configuration' present. Check if any methods have '@Bean' annotation
		for (MethodDeclaration m : classDecl.getMethods()) {
			if (isBeanMethod(m, annotationHierarchies)) {
				if (context.isIndexComplete()) {
					if (!isException(project, classDecl, context)) {
						return true;
					}
				}
				else {
					throw new RequiredCompleteIndexException();
				}
			}
		}

		return false;
	}
	
	private boolean isException(IJavaProject project, TypeDeclaration classDecl, ReconcilingContext context) {
		if (springIndex == null) {
			return false;
		}

		final String beanClassName = ReconcileUtils.getDeepErasureType(classDecl.resolveBinding()).getQualifiedName();

		Bean[] beans = this.springIndex.getBeansOfProject(project.getElementName());

		if (beans == null || beans.length == 0) {
			return false;
		}

		// look for beans with that type
		if (Arrays.stream(beans).anyMatch(bean -> bean.getType().equals(beanClassName))) {
			return true;
		}

		// Check if the class is referenced as a configuration class in client annotations
		// (e.g., @FeignClient, @LoadBalancerClient)
		return checkClientConfigurationReferences(beans, beanClassName, context, 
				Annotations.FEIGN_CLIENT, Annotations.LOAD_BALANCER_CLIENT);
	}
	
	/**
	 * Checks if the given bean class is referenced as a configuration class in any of the specified client annotations.
	 * 
	 * @param beans all beans in the project
	 * @param beanClassName the fully qualified name of the bean class to check
	 * @param context the reconciling context to register dependencies
	 * @param clientAnnotationTypes the client annotation types to check (e.g., FeignClient, LoadBalancerClient)
	 * @return true if the bean class is referenced in any client annotation's configuration attribute
	 */
	private boolean checkClientConfigurationReferences(Bean[] beans, String beanClassName, ReconcilingContext context, 
			String... clientAnnotationTypes) {
		for (String clientAnnotationType : clientAnnotationTypes) {
			List<Bean> clientBeans = Arrays.stream(beans)
					.filter(bean -> isConfiguredAsClientConfigClass(bean, beanClassName, clientAnnotationType))
					.toList();

			if (!clientBeans.isEmpty()) {
				// record dependencies for clients that have this type configured as their configuration
				for (Bean clientBean : clientBeans) {
					context.addDependency(clientBean.getType());
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Checks if a bean is configured as a configuration class for a specific client annotation type.
	 * 
	 * @param bean the bean to check
	 * @param beanClassName the configuration class name to look for
	 * @param clientAnnotationType the client annotation type (e.g., FeignClient, LoadBalancerClient)
	 * @return true if the bean has the client annotation with the given class in its configuration attribute
	 */
	private boolean isConfiguredAsClientConfigClass(Bean bean, String beanClassName, String clientAnnotationType) {
		return Arrays.stream(bean.getAnnotations())
			.filter(annotation -> annotation.getAnnotationType().equals(clientAnnotationType))
			.map(annotation -> annotation.getAttributes().get("configuration"))
			.flatMap(attributeValues -> attributeValues != null ? Arrays.stream(attributeValues) : Stream.empty())
			.anyMatch(attributeValue -> attributeValue.getName().equals(beanClassName));
	}

	private void createFixesForClientAnnotation(List<FixDescriptor> fixes, Bean[] beans, String beanClassName,
			String clientAnnotationType) {
		List<Bean> clientBeans = Arrays.stream(beans)
				.filter(bean -> hasAnnotation(bean, clientAnnotationType))
				.filter(bean -> bean.getLocation() != null && bean.getLocation().getUri() != null)
				.toList();

		String clientAnnotationSimpleName = ReconcileUtils.getSimpleName(clientAnnotationType);
		for (Bean clientBean : clientBeans) {
			fixes.add(new FixDescriptor(AddOrUpdateAnnotationAttribute.class.getName(), List.of(clientBean.getLocation().getUri()),
					"Add to '@%s' configuration in '%s'".formatted(clientAnnotationSimpleName, clientBean.getName()))
					.withRecipeScope(RecipeScope.FILE)
					.withParameters(Map.of(
							"annotationType", clientAnnotationType,
							"attributeName", "configuration",
							"attributeValue", beanClassName + ".class",
							"appendArray", true
					)));
		}
	}

	private static boolean hasAnnotation(Bean bean, String annotationType) {
		return Arrays.stream(bean.getAnnotations())
				.anyMatch(a -> a.getAnnotationType().equals(annotationType));
	}

	private static boolean isBeanMethod(MethodDeclaration m, AnnotationHierarchies annotationHierarchies) {
		return annotationHierarchies.isAnnotatedWith(m.resolveBinding(), Annotations.BEAN);
	}

}
