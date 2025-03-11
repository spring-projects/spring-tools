/*******************************************************************************
 * Copyright (c) 2020, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeCompletionProcessor;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.BeanNamesCompletionProcessor;
import org.springframework.ide.vscode.boot.java.beans.BeanTypesCompletionProcessor;
import org.springframework.ide.vscode.boot.java.beans.BeanCompletionProvider;
import org.springframework.ide.vscode.boot.java.beans.DependsOnCompletionProcessor;
import org.springframework.ide.vscode.boot.java.beans.NamedCompletionProvider;
import org.springframework.ide.vscode.boot.java.beans.ProfileCompletionProvider;
import org.springframework.ide.vscode.boot.java.beans.QualifierCompletionProvider;
import org.springframework.ide.vscode.boot.java.beans.ResourceCompletionProvider;
import org.springframework.ide.vscode.boot.java.conditionals.ConditionalOnPropertyCompletionProcessor;
import org.springframework.ide.vscode.boot.java.conditionals.ConditionalOnResourceCompletionProcessor;
import org.springframework.ide.vscode.boot.java.contextconfiguration.ContextConfigurationProcessor;
import org.springframework.ide.vscode.boot.java.cron.CronExpressionCompletionProvider;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryCompletionProcessor;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCompletionEngine;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.boot.java.scope.ScopeCompletionProcessor;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippet;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippetContext;
import org.springframework.ide.vscode.boot.java.snippets.JavaSnippetManager;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.java.value.ValueCompletionProcessor;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.common.collect.ImmutableList;

@Configuration
public class BootJavaCompletionEngineConfigurer {
	

	@Bean JavaSnippetManager javaSnippetManager(SimpleLanguageServer server) {
		JavaSnippetManager snippetManager = new JavaSnippetManager(server::createSnippetBuilder);

		// PT 160529904: Eclipse templates are duplicated, due to templates in Eclipse also being contributed by
		// STS3 bundle. Therefore do not include templates if client is Eclipse
		// TODO: REMOVE this check once STS3 is no longer supported
//		if (LspClient.currentClient() != LspClient.Client.ECLIPSE) {
			
			JavaSnippetContext webControllerContext = new CompositeJavaSnippetContext(
					JavaSnippetContext.AT_ROOT_LEVEL,
					new AnnotatedTypeDeclarationContext(Annotations.CONTROLLER));
			
			snippetManager.add(
					new JavaSnippet("@RequestMapping(..) {..}", webControllerContext, CompletionItemKind.Method,
							ImmutableList.of("org.springframework.web.bind.annotation.RequestMapping",
									"org.springframework.web.bind.annotation.RequestMethod",
									"org.springframework.web.bind.annotation.RequestParam"),
							"@RequestMapping(\"${1:path}\", method=RequestMethod.${GET})\n"
									+ "public ${2:String} ${3:requestMethodName}(@RequestParam ${4:String} ${5:param}) {\n"
									+ "	return new ${2:String}($0);\n" + "}\n",
							"RequestMapping"));
			snippetManager.add(
					new JavaSnippet("@GetMapping(..) {..}", webControllerContext, CompletionItemKind.Method,
							ImmutableList.of("org.springframework.web.bind.annotation.GetMapping",
									"org.springframework.web.bind.annotation.RequestParam"),
							"@GetMapping(\"${1:path}\")\n"
									+ "public ${2:String} ${3:getMethodName}(@RequestParam ${4:String} ${5:param}) {\n"
									+ "	return new ${2:String}($0);\n" + "}\n",
							"GetMapping"));
			snippetManager.add(
					new JavaSnippet("@PostMapping(..) {..}", webControllerContext, CompletionItemKind.Method,
							ImmutableList.of("org.springframework.web.bind.annotation.PostMapping",
									"org.springframework.web.bind.annotation.RequestBody"),
							"@PostMapping(\"${1:path}\")\n"
									+ "public ${2:String} ${3:postMethodName}(@RequestBody ${4:String} ${5:entity}) {\n"
									+ "	//TODO: process POST request\n" + "	$0\n" + "	return ${5:entity};\n" + "}\n",
							"PostMapping"));
			snippetManager.add(
					new JavaSnippet("@PutMapping(..) {..}", webControllerContext, CompletionItemKind.Method,
							ImmutableList.of("org.springframework.web.bind.annotation.PutMapping",
									"org.springframework.web.bind.annotation.RequestBody",
									"org.springframework.web.bind.annotation.PathVariable"),
							"@PutMapping(\"${1:path}/{${2:id}}\")\n"
									+ "public ${3:String} ${4:putMethodName}(@PathVariable ${5:String} ${2:id}, @RequestBody ${6:String} ${7:entity}) {\n"
									+ "	//TODO: process PUT request\n" + "	$0\n" + "	return ${7:entity};\n" + "}",
							"PutMapping"));
//		}

		return snippetManager;
	}

	@Bean
	BootJavaCompletionEngine javaCompletionEngine(
			BootLanguageServerParams params,
			@Qualifier("adHocProperties") ProjectBasedPropertyIndexProvider adHocProperties,
			JavaSnippetManager snippetManager, 
			CompilationUnitCache cuCache,
			SpringMetamodelIndex springIndex,
			RewriteRefactorings rewriteRefactorings,
			BootJavaConfig config,
			SimpleLanguageServer server) {
		
		SpringPropertyIndexProvider indexProvider = params.indexProvider;
		JavaProjectFinder javaProjectFinder = params.projectFinder;
		
		Map<String, CompletionProvider> providers = new HashMap<>();
		
		providers.put(Annotations.VALUE, new ValueCompletionProcessor(javaProjectFinder, indexProvider, adHocProperties));
		providers.put(Annotations.CONTEXT_CONFIGURATION, new ContextConfigurationProcessor(javaProjectFinder));
		providers.put(Annotations.REPOSITORY, new DataRepositoryCompletionProcessor());
		
		providers.put(Annotations.CONDITIONAL_ON_RESOURCE, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"resources", new ConditionalOnResourceCompletionProcessor())));

		providers.put(Annotations.CONDITIONAL_ON_PROPERTY, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new ConditionalOnPropertyCompletionProcessor(indexProvider, adHocProperties, ConditionalOnPropertyCompletionProcessor.Mode.PROPERTY),
				"name", new ConditionalOnPropertyCompletionProcessor(indexProvider, adHocProperties, ConditionalOnPropertyCompletionProcessor.Mode.PROPERTY),
				"prefix", new ConditionalOnPropertyCompletionProcessor(indexProvider, adHocProperties, ConditionalOnPropertyCompletionProcessor.Mode.PREFIX))));

		providers.put(Annotations.SCOPE, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new ScopeCompletionProcessor())));

		providers.put(Annotations.DEPENDS_ON, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new DependsOnCompletionProcessor(springIndex))));

		providers.put(Annotations.CONDITIONAL_ON_BEAN, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"name", new BeanNamesCompletionProcessor(springIndex),
				"type", new BeanTypesCompletionProcessor(springIndex))));

		providers.put(Annotations.CONDITIONAL_ON_MISSING_BEAN, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"name", new BeanNamesCompletionProcessor(springIndex),
				"type", new BeanTypesCompletionProcessor(springIndex),
				"ignoredType", new BeanTypesCompletionProcessor(springIndex))));

		providers.put(Annotations.QUALIFIER, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new QualifierCompletionProvider(springIndex))));
		
		providers.put(Annotations.PROFILE, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new ProfileCompletionProvider(springIndex))));
		
		providers.put(Annotations.RESOURCE_JAVAX, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"name", new ResourceCompletionProvider(springIndex))));
		
		providers.put(Annotations.RESOURCE_JAKARTA, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"name", new ResourceCompletionProvider(springIndex))));

		providers.put(Annotations.NAMED_JAKARTA, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new NamedCompletionProvider(springIndex))));
		
		providers.put(Annotations.NAMED_JAVAX, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"value", new NamedCompletionProvider(springIndex))));
		
		providers.put(Annotations.SCHEDULED, new AnnotationAttributeCompletionProcessor(javaProjectFinder, Map.of(
				"cron", new CronExpressionCompletionProvider())));

		providers.put(Annotations.BEAN, new BeanCompletionProvider(javaProjectFinder, springIndex, rewriteRefactorings, config, cuCache, server));

		return new BootJavaCompletionEngine(cuCache, providers, snippetManager);
	}
	
	/**
	 * checks if the type declaration that belongs to the node is annotated with the required annotation
	 */
	private class AnnotatedTypeDeclarationContext implements JavaSnippetContext {
		
		private final String requiredAnnotation;

		public AnnotatedTypeDeclarationContext(String requiredAnnotation) {
			this.requiredAnnotation = requiredAnnotation;
		}

		@Override
		public boolean appliesTo(ASTNode node, int offset, CharSequence prefix) {
			TypeDeclaration type = ASTUtils.findDeclaringType(node);
			if (type != null) {
				ITypeBinding binding = type.resolveBinding();
				if (binding != null) {
					return AnnotationHierarchies.get(node).isAnnotatedWith(binding, requiredAnnotation);
				}
			}
			return false;
		}
	}
	
	/**
	 * composite implementation for snippet contexts that allows you to combine multiple context checks
	 * The check only passes if ALL individual context checks are successful
	 */
	private class CompositeJavaSnippetContext implements JavaSnippetContext {
		
		private final JavaSnippetContext[] contexts;

		public CompositeJavaSnippetContext(JavaSnippetContext... contexts) {
			this.contexts = contexts;
		}

		@Override
		public boolean appliesTo(ASTNode node, int offset, CharSequence prefix) {
			for (JavaSnippetContext context : contexts) {
				if (!context.appliesTo(node, offset, prefix)) {
					return false;
				}
			}
			return contexts.length > 0;
		}
	}
	
}
