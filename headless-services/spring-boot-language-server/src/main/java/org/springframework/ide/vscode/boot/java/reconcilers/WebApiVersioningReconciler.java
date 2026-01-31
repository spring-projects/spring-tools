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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.AddApiVersionConfig;
import org.springframework.ide.vscode.commons.rewrite.java.AddApiVersioningConfigurationCall;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.UriUtil;

public class WebApiVersioningReconciler implements JdtAstReconciler {

	private static final String SPRING_WEBFLUX = "spring.webflux";

	private static final String PROP_PREFIX_SPRING_MVC = "spring.mvc";

	private static final String PROBLEM_LABEL = "API versioning not configured anywhere";

	// Label templates for quickfixes - all use exactly 2 parameters: framework (%s) and config type (%s)
	private static final String LABEL_CREATE_BEAN = "Create %s config bean with versioning via %s";
	private static final String LABEL_ADD_TO_BEAN = "Add %s versioning via %s to existing bean";
	private static final String LABEL_ADD_PROPERTY = "Add %s versioning via %s using properties";

	private final SpringMetamodelIndex springIndex;

	private final QuickfixRegistry registry;

	/**
	 * Record to hold path, package, and optional bean name information for a configurer bean
	 */
	private record ConfigurerBeanLocation(Path path, String packageName, String beanName) {}

	/**
	 * Enum defining the web framework type (MVC vs Flux) with their metadata
	 */
	private enum WebFrameworkType {
		MVC(
			Annotations.WEB_MVC_CONFIGURER_INTERFACE,
			PROP_PREFIX_SPRING_MVC,
			"WebMVC",
			"WebMvcConfigurer",
			false
		),
		FLUX(
			Annotations.WEB_FLUX_CONFIGURER_INTERFACE,
			SPRING_WEBFLUX,
			"WebFlux",
			"WebFluxConfigurer",
			true
		);

		private final String configurerInterface;
		private final String propertyPrefix;
		private final String label;
		private final String configurerSuffix;
		private final boolean isFlux;

		WebFrameworkType(String configurerInterface, String propertyPrefix, String label,
				String configurerSuffix, boolean isFlux) {
			this.configurerInterface = configurerInterface;
			this.propertyPrefix = propertyPrefix;
			this.label = label;
			this.configurerSuffix = configurerSuffix;
			this.isFlux = isFlux;
		}
	}

	/**
	 * Enum defining all API versioning configuration types with their metadata
	 */
	private enum VersioningConfigType {
		HEADER("header", "request header", "X-API-Version", AddApiVersioningConfigurationCall.ConfigType.HEADER),
		PATH_SEGMENT("path-segment", "path segment", "0", AddApiVersioningConfigurationCall.ConfigType.PATH),
		QUERY_PARAM("query-parameter", "query parameter", "version", AddApiVersioningConfigurationCall.ConfigType.QUERY),
		MEDIA_TYPE("media-type-parameter", "media type parameter", "v", AddApiVersioningConfigurationCall.ConfigType.MEDIA_TYPE);

		private final String propertyKey;
		private final String label;
		private final String defaultValue;
		private final AddApiVersioningConfigurationCall.ConfigType beanConfigType;

		VersioningConfigType(String propertyKey, String label, String defaultValue, AddApiVersioningConfigurationCall.ConfigType beanConfigType) {
			this.propertyKey = propertyKey;
			this.label = label;
			this.defaultValue = defaultValue;
			this.beanConfigType = beanConfigType;
		}
	}

	public WebApiVersioningReconciler(SpringMetamodelIndex springIndex, QuickfixRegistry registry) {
		this.springIndex = springIndex;
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.libraryVersionGreaterOrEqual(SpringProjectUtil.SPRING_WEB, 7, 0, 0).test(project);
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

		List<FixDescriptor> allFixes = new ArrayList<>();

		boolean hasWebMvc = hasWebMvc(project);
		boolean hasWebFlux = hasWebFlux(project);

		if (hasWebMvc || hasWebFlux) {
			List<String> propertyFiles = findPropertyFiles(project);
			// Create fixes for WebMVC if present
			if (hasWebMvc) {
				allFixes.addAll(createWebFrameworkFixes(project, propertyFiles, WebFrameworkType.MVC));
			}
			// Create fixes for WebFlux if present
			if (hasWebFlux) {
				allFixes.addAll(createWebFrameworkFixes(project, propertyFiles, WebFrameworkType.FLUX));
			}
		}

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

	private List<String> findPropertyFiles(IJavaProject project) {
		// Find all Boot properties files
		List<Path> propFiles = SpringProjectUtil.findBootPropertiesFiles(project);
		// Convert paths to URIs
		return propFiles.stream()
			.map(path -> path.toUri().toASCIIString())
			.toList();
	}

	/**
	 * Generic method to create fixes for a specific web framework type (MVC or Flux)
	 */
	private List<FixDescriptor> createWebFrameworkFixes(IJavaProject project, List<String> propFiles,
			WebFrameworkType frameworkType) {
		List<FixDescriptor> fixes = new ArrayList<>();
		List<Bean> configBeans = Arrays.stream(springIndex.getBeansOfProject(project.getElementName()))
				.filter(b -> b.isConfiguration())
				.filter(b -> b.getSupertypes().contains(frameworkType.configurerInterface))
				.toList();
		boolean beanFound = false;
		if (!configBeans.isEmpty()) {
			List<Path> sourceFolders = IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
					.map(f -> f.toPath())
					.toList();
			for (Bean configBean : configBeans) {
				Path beanPath = Paths.get(URI.create(configBean.getLocation().getUri()));
				if (sourceFolders.stream().anyMatch(beanPath::startsWith)) {
					beanFound = true;
					ConfigurerBeanLocation location = createBeanLocation(configBean);
					String labelWithBeanName = location.beanName() + ": " + LABEL_ADD_TO_BEAN;
					fixes.addAll(createBeanFixes(location, frameworkType, labelWithBeanName));
				}
			}
		}
		if (!beanFound) {
			String baseName = StringUtil.hyphensToCamelCase(project.getElementName(), true) + frameworkType.configurerSuffix;
			ConfigurerBeanLocation location = getNewConfigBeanLocation(project, baseName);
			if (location != null) {
				fixes.addAll(createBeanFixes(location, frameworkType, LABEL_CREATE_BEAN));
			}
		}
		if (!propFiles.isEmpty()) {
			fixes.addAll(createPropertiesFixes(propFiles, frameworkType, LABEL_ADD_PROPERTY));
		}
		return fixes;
	}

	private ConfigurerBeanLocation getNewConfigBeanLocation(IJavaProject project, String baseName) {
		ConfigurerBeanLocation location = null;
		for (Bean b : springIndex.getBeansOfProject(project.getElementName())) {
			if (Arrays.stream(b.getAnnotations()).map(am -> am.getAnnotationType()).anyMatch(Annotations.CONFIGURATION::equals)) {
				return createSiblingBeanLocation(b, baseName);
			} else if (location == null){
				location = createSiblingBeanLocation(b, baseName);
			}
		}
		return location;
	}

	private ConfigurerBeanLocation createBeanLocation(Bean bean) {
		Path beanPath = Paths.get(URI.create(bean.getLocation().getUri()));
		String packageName = bean.getType() == null ? "" : StringUtil.packageName(bean.getType());
		String beanName = bean.getType() == null ? "" : StringUtil.simpleName(bean.getType());
		return new ConfigurerBeanLocation(beanPath, packageName, beanName);
	}

	private ConfigurerBeanLocation createSiblingBeanLocation(Bean bean, String baseName) {
		Path beanPath = Paths.get(URI.create(bean.getLocation().getUri()));
		Path siblingPath = beanPath.getParent().resolve(baseName + ".java");

		if (Files.exists(siblingPath)) {
			for (int i = 0; i < Integer.MAX_VALUE && Files.exists(siblingPath); i++) {
				siblingPath = siblingPath.getParent().resolve("%s%s.java".formatted(baseName, i));
			}
		}

		String packageName = bean.getType() == null ? "" : StringUtil.packageName(bean.getType());
		// For new beans, the name is derived from the file name
		String beanName = siblingPath.getFileName().toString().replace(".java", "");
		return new ConfigurerBeanLocation(siblingPath, packageName, beanName);
	}

	private List<FixDescriptor> createPropertiesFixes(List<String> fileUris, WebFrameworkType frameworkType,
			String labelTemplate) {
		return Arrays.stream(VersioningConfigType.values())
				.map(type -> createPropertyVersioningFix(fileUris, frameworkType, type, labelTemplate, false))
				.toList();
	}

	private static FixDescriptor createPropertyVersioningFix(List<String> fileUris, WebFrameworkType frameworkType,
			VersioningConfigType configType, String labelTemplate, boolean preferred) {
		String property = frameworkType.propertyPrefix + ".apiversion.use." + configType.propertyKey;
		String label = labelTemplate.formatted(frameworkType.label, configType.label);

		return new FixDescriptor(AddSpringProperty.class.getName(), fileUris, label)
			.withRecipeScope(RecipeScope.FILE)
			.withParameters(Map.of(
				"property", property,
				"value", configType.defaultValue
			))
			.withPreferred(preferred);
	}

	private List<FixDescriptor> createBeanFixes(ConfigurerBeanLocation location, WebFrameworkType frameworkType,
			String labelTemplate) {
		return Arrays.stream(VersioningConfigType.values())
				.map(type -> createBeanVersioningFix(location, frameworkType, type, labelTemplate, false))
				.toList();
	}

	private static FixDescriptor createBeanVersioningFix(ConfigurerBeanLocation location, WebFrameworkType frameworkType,
			VersioningConfigType configType, String labelTemplate, boolean preferred) {
		String label = labelTemplate.formatted(frameworkType.label, configType.label);
		Path filePath = location.path();
		String packageName = location.packageName();

		return new FixDescriptor(
				AddApiVersionConfig.class.getName(),
				List.of(filePath.toUri().toASCIIString()),
				label)
			.withRecipeScope(RecipeScope.FILE)
			.withParameters(Map.of(
				"filePath", filePath.toString(),
				"pkgName", packageName,
				"isFlux", frameworkType.isFlux,
				"configType", configType.beanConfigType,
				"value", configType.defaultValue
			))
			.withPreferred(preferred);
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
}
