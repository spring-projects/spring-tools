/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.spring.AddSpringProperty;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigJavaIndexer;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.UriUtil;

public class WebApiVersioningReconciler implements JdtAstReconciler {

	private static final String SPRING_WEBFLUX = "spring.webflux";

	private static final String PROP_PREFIX_SPRING_MVC = "spring.mvc";

	private static final String PROBLEM_LABEL = "API versioning not configured anywhere";

	private final SpringMetamodelIndex springIndex;

	private final QuickfixRegistry registry;

	public WebApiVersioningReconciler(SpringMetamodelIndex springIndex, QuickfixRegistry registry) {
		this.springIndex = springIndex;
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(4, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.API_VERSIONING_NOT_CONFIGURED;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return new ASTVisitor() {

			/**
			 * this in the piece of the reconciler that validates the version attribute of annotations on controllers
			 */
			@Override
			public boolean visit(NormalAnnotation annotation) {
				IAnnotationBinding annotationBinding = annotation.resolveAnnotationBinding();
				if (annotationBinding == null) return super.visit(annotation);
				
				ITypeBinding annotationType = annotationBinding.getAnnotationType();
				if (annotationType == null) return super.visit(annotation);
				
				boolean isWebController = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.SPRING_REQUEST_MAPPING);
				if (!isWebController) return super.visit(annotation);
				
				@SuppressWarnings("unchecked")
				List<MemberValuePair> attributes = annotation.values();
				attributes.stream()
					.filter(pair -> "version".equals(pair.getName().toString()))
					.forEach(pair -> {
						if (!context.isIndexComplete()) {
							throw new RequiredCompleteIndexException();
						}
						
					if (!isApiVersioningConfigured(project, context)) {
						ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
								pair.getStartPosition(), pair.getLength());
						
						// Add quick fixes to configure versioning via properties files
						addVersioningQuickfixes(project, problem);
						
						context.getProblemCollector().accept(problem);
					}
					}
				);
				
				return super.visit(annotation);
			}
			
			/**
			 * this is the piece of the reconciler that looks for changes to web configs and then marks all the potentially affected
			 * controller classes for re-indexing
			 */
			@Override
			public boolean visit(TypeDeclaration type) {
				if (WebConfigJavaIndexer.getWebConfig(type) == null) {
					return super.visit(type);
				}
				
				identifyBeansToReconcile(project)
					.map(bean -> UriUtil.toFileString(bean.getLocation().getUri()))
					.forEach(file -> context.markForAffetcedFilesIndexing(file));
					
				return super.visit(type);
			}
		};
	}
	
	@Override
	public List<String> identifyFilesToReconcile(IJavaProject project, List<String> changedPropertyFiles) {
		return identifyBeansToReconcile(project)
				.map(bean -> bean.getLocation().getUri())
				.toList();
	}
	
	private Stream<Bean> identifyBeansToReconcile(IJavaProject project) {
		return Arrays.stream(springIndex.getBeansOfProject(project.getElementName()))
				.filter(bean -> isAnnotatedWith(bean, Annotations.CONTROLLER));
	}
	
	private boolean isApiVersioningConfigured(IJavaProject project, ReconcilingContext context) {
		return WebApiReconcilerUtil.getWebConfigs(springIndex, project, context)
			.filter(webConfig -> webConfig.getVersionSupportStrategies() != null && webConfig.getVersionSupportStrategies().size() > 0)
			.findAny()
			.isPresent();
	}
	
	/**
	 * Adds quick fixes to configure API versioning via properties files
	 */
	private void addVersioningQuickfixes(IJavaProject project, ReconcileProblemImpl problem) {
		// Find all Boot properties files
		List<Path> propFiles = SpringProjectUtil.findBootPropertiesFiles(project);
		
		// If no properties files found, don't add any quick fixes
		if (propFiles.isEmpty()) {
			return;
		}
		
		// Convert paths to URIs
		List<String> fileUris = propFiles.stream()
			.map(path -> path.toUri().toASCIIString())
			.toList();
		
		// Create quick fixes - one per strategy per framework, targeting all files
		List<FixDescriptor> allFixes = createVersioningQuickfixes(project, fileUris);
		
		// Add all quick fixes to the problem
		if (!allFixes.isEmpty()) {
			ReconcileUtils.setRewriteFixes(registry, problem, allFixes);
		}
	}
	
	private boolean isAnnotatedWith(Bean bean, String annotationType) {
		return Arrays.stream(bean.getAnnotations())
				.filter(annotation -> annotation.getAnnotationType().equals(annotationType))
				.findAny()
				.isPresent();
	}
	
	/**
	 * Creates quick fix descriptors for configuring API versioning.
	 * Creates one fix per strategy per framework, with each fix targeting all property files.
	 * 
	 * @param project the Java project
	 * @param fileUris the list of property file URIs to target
	 * @return list of fix descriptors (4 strategies per framework, each targeting all files)
	 */
	public static List<FixDescriptor> createVersioningQuickfixes(IJavaProject project, List<String> fileUris) {
		List<FixDescriptor> allFixes = new ArrayList<>();
		
		boolean hasWebMvc = hasWebMvc(project);
		boolean hasWebFlux = hasWebFlux(project);
		
		// If neither detected, default to WebMVC
		if (!hasWebMvc && !hasWebFlux) {
			hasWebMvc = true;
		}
		
		// Create fixes for WebMVC if present
		if (hasWebMvc) {
			boolean preferred = !hasWebFlux; // Only mark as preferred if WebFlux is not also present
			allFixes.add(createHeaderVersioningFix(fileUris, PROP_PREFIX_SPRING_MVC, "WebMVC", false));
			allFixes.add(createPathSegmentVersioningFix(fileUris, PROP_PREFIX_SPRING_MVC, "WebMVC", preferred));
			allFixes.add(createQueryParamVersioningFix(fileUris, PROP_PREFIX_SPRING_MVC, "WebMVC", false));
			allFixes.add(createMediaTypeVersioningFix(fileUris, PROP_PREFIX_SPRING_MVC, "WebMVC", false));
		}
		
		// Create fixes for WebFlux if present
		if (hasWebFlux) {
			boolean preferred = !hasWebMvc; // Only mark as preferred if WebMVC is not also present
			allFixes.add(createHeaderVersioningFix(fileUris, SPRING_WEBFLUX, "WebFlux", false));
			allFixes.add(createPathSegmentVersioningFix(fileUris, SPRING_WEBFLUX, "WebFlux", preferred));
			allFixes.add(createQueryParamVersioningFix(fileUris, SPRING_WEBFLUX, "WebFlux", false));
			allFixes.add(createMediaTypeVersioningFix(fileUris, SPRING_WEBFLUX, "WebFlux", false));
		}
		
		return allFixes;
	}
	
	/**
	 * Checks if the project has Spring WebMVC on the classpath
	 */
	private static boolean hasWebMvc(IJavaProject project) {
		return project.getClasspath().findBinaryLibraryByPrefix("spring-webmvc").isPresent();
	}
	
	/**
	 * Checks if the project has Spring WebFlux on the classpath
	 */
	private static boolean hasWebFlux(IJavaProject project) {
		return project.getClasspath().findBinaryLibraryByPrefix("spring-webflux").isPresent();
	}
	
	private static FixDescriptor createHeaderVersioningFix(List<String> fileUris, String propertyPrefix, String frameworkLabel, boolean preferred) {
		String property = propertyPrefix + ".apiversion.use.header";
		String value = "X-API-Version";
		String label = "Configure %s API versioning via request header".formatted(frameworkLabel);
		
		return new FixDescriptor(AddSpringProperty.class.getName(), fileUris, label)
			.withRecipeScope(RecipeScope.FILE)
			.withParameters(Map.of(
				"property", property,
				"value", value
			))
			.withPreferred(preferred);
	}
	
	private static FixDescriptor createPathSegmentVersioningFix(List<String> fileUris, String propertyPrefix, String frameworkLabel, boolean preferred) {
		String property = propertyPrefix + ".apiversion.use.path-segment";
		String value = "0";
		String label = "Configure %s API versioning via path segment".formatted(frameworkLabel);
		
		return new FixDescriptor(AddSpringProperty.class.getName(), fileUris, label)
			.withRecipeScope(RecipeScope.FILE)
			.withParameters(Map.of(
				"property", property,
				"value", value
			))
			.withPreferred(preferred);
	}
	
	private static FixDescriptor createQueryParamVersioningFix(List<String> fileUris, String propertyPrefix, String frameworkLabel, boolean preferred) {
		String property = propertyPrefix + ".apiversion.use.query-parameter";
		String value = "version";
		String label = "Configure %s API versioning via query parameter".formatted(frameworkLabel);
		
		return new FixDescriptor(AddSpringProperty.class.getName(), fileUris, label)
			.withRecipeScope(RecipeScope.FILE)
			.withParameters(Map.of(
				"property", property,
				"value", value
			))
			.withPreferred(preferred);
	}
	
	private static FixDescriptor createMediaTypeVersioningFix(List<String> fileUris, String propertyPrefix, String frameworkLabel, boolean preferred) {
		String property = propertyPrefix + ".apiversion.use.media-type-parameter";
		String value = "v";
		String label = "Configure %s API versioning via media type parameter".formatted(frameworkLabel);
		
		return new FixDescriptor(AddSpringProperty.class.getName(), fileUris, label)
			.withRecipeScope(RecipeScope.FILE)
			.withParameters(Map.of(
				"property", property,
				"value", value
			))
			.withPreferred(preferred);
	}
}
