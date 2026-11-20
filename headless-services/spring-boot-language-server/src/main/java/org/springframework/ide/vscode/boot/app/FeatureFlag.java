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
package org.springframework.ide.vscode.boot.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * Individual language server features that can be turned on or off via the {@code boot-java.feature-flags}
 * setting. This is the complete list of valid ids. The ids are part of the settings contract and must stay
 * stable once released. When adding an id, also add it to the description of the setting in the
 * {@code package.json} of the VS Code extension and to {@code FEATURE_FLAGS.md}.
 *
 * @author Martin Lippert
 */
public enum FeatureFlag {

	/*
	 * ==========================================================================
	 * Java files - Completion
	 * ==========================================================================
	 */

	/** Property key proposals in {@code @Value} */
	VALUE_PROPERTY_COMPLETION("value-property-completion"),

	/** {@code classpath:} resource proposals in {@code @Value} */
	VALUE_CLASSPATH_COMPLETION("value-classpath-completion"),

	/** Bean name proposals in {@code @DependsOn}, {@code @Qualifier}, {@code @Resource}, {@code @Named}, {@code @ConditionalOn(Missing)Bean} */
	BEAN_NAME_COMPLETION("bean-name-completion"),

	/** Bean type proposals in {@code @ConditionalOn(Missing)Bean} */
	BEAN_TYPE_COMPLETION("bean-type-completion"),

	/** Profile proposals in {@code @Profile} */
	PROFILE_COMPLETION("profile-completion"),

	/** Key proposals in {@code @ConditionalOnProperty} */
	CONDITIONAL_ON_PROPERTY_COMPLETION("conditional-on-property-completion"),

	/** Resource proposals in {@code @ConditionalOnResource} */
	CONDITIONAL_ON_RESOURCE_COMPLETION("conditional-on-resource-completion"),

	/** Resource proposals in {@code @ContextConfiguration} */
	CONTEXT_CONFIGURATION_COMPLETION("context-configuration-completion"),

	/** Scope proposals in {@code @Scope} */
	SCOPE_COMPLETION("scope-completion"),

	/** Cron expression proposals in {@code @Scheduled} */
	CRON_COMPLETION("cron-completion"),

	/** Query method name proposals in Spring Data repository interfaces */
	DATA_REPOSITORY_COMPLETION("data-repository-completion"),

	/** Java snippet proposals, e.g. request mapping methods in controllers */
	JAVA_SNIPPET_COMPLETION("java-snippet-completion"),

	/*
	 * ==========================================================================
	 * Java files - Navigation (go to definition and references)
	 * ==========================================================================
	 */

	/** Go to definition / references on property keys in {@code @Value} and {@code @ConditionalOnProperty} */
	PROPERTY_KEY_NAVIGATION("property-key-navigation"),

	/** Go to definition / references on bean names in {@code @DependsOn}, {@code @Qualifier}, {@code @Resource}, {@code @Named}, {@code @ConditionalOn(Missing)Bean} */
	BEAN_NAME_NAVIGATION("bean-name-navigation"),

	/** References on profile names in {@code @Profile} */
	PROFILE_NAVIGATION("profile-navigation"),

	/** Go to definition on {@code classpath:} resources in annotation values */
	CLASSPATH_RESOURCE_NAVIGATION("classpath-resource-navigation"),

	/** Go to definition on parameters in data queries */
	DATA_QUERY_PARAMETER_NAVIGATION("data-query-parameter-navigation"),

	/** Go to definition in SpEL expressions */
	SPEL_NAVIGATION("spel-navigation"),

	/** Go to the generated implementation of Spring Data query methods */
	AOT_QUERY_METHOD_IMPLEMENTATION_NAVIGATION("aot-query-method-implementation-navigation"),

	/** References on application events */
	EVENT_NAVIGATION("event-navigation"),

	/*
	 * ==========================================================================
	 * Java files - Code actions and quick fixes
	 * ==========================================================================
	 */

	/** Quick fixes for problems whose severity is set to {@code IGNORE}, offered as refactorings */
	IGNORED_PROBLEM_FIXES("ignored-problem-fixes"),

	/** The "Convert query to text block" code action */
	QUERY_TEXT_BLOCK_CONVERSION("query-text-block-conversion"),

	/** The "Add @Query" code action for query methods */
	ADD_QUERY_ANNOTATION("add-query-annotation"),

	/** The quick fix to set the SQL dialect for native queries */
	SQL_DIALECT_QUICKFIX("sql-dialect-quickfix"),

	/*
	 * ==========================================================================
	 * Java files - Code lenses
	 * ==========================================================================
	 */

	/** Code lens on WebFlux handler methods */
	WEBFLUX_HANDLER_CODE_LENS("webflux-handler-code-lens"),

	/** Code lenses that send queries, SpEL and pointcut expressions to an AI assistant */
	AI_ASSISTANT_CODE_LENS("ai-assistant-code-lens"),

	/** Code lens on functional router methods to convert them to the builder pattern */
	ROUTER_FUNCTION_CODE_LENS("router-function-code-lens"),

	/** Code lens with the AOT generated query on Spring Data query methods */
	DATA_QUERY_AOT_CODE_LENS("data-query-aot-code-lens"),

	/** Code lens with web config details on controller classes */
	WEB_CONFIG_CODE_LENS("web-config-code-lens"),

	/*
	 * ==========================================================================
	 * Java files - Document highlights
	 * ==========================================================================
	 */

	/** Document highlights for WebFlux routes in Java files */
	WEBFLUX_ROUTE_HIGHLIGHT("webflux-route-highlight"),

	/** Document highlights for parts of data queries (JPQL, HQL, SQL) in Java files */
	DATA_QUERY_HIGHLIGHT("data-query-highlight"),

	/*
	 * ==========================================================================
	 * Java files - Inlay hints
	 * ==========================================================================
	 */

	/** Inlay hints in data queries (JPQL, HQL, SQL) in Java files */
	DATA_QUERY_INLAY_HINTS("data-query-inlay-hints"),

	/*
	 * ==========================================================================
	 * Java files - Semantic tokens (syntax highlighting)
	 * ==========================================================================
	 */

	/** Syntax highlighting of data queries (JPQL, HQL, SQL) in Java files and in named query properties files */
	DATA_QUERY_SEMANTIC_TOKENS("data-query-semantic-tokens"),

	/** Syntax highlighting of SpEL expressions in Java files */
	SPEL_SEMANTIC_TOKENS("spel-semantic-tokens"),

	/** Syntax highlighting of cron expressions in Java files and in properties and YAML files */
	CRON_SEMANTIC_TOKENS("cron-semantic-tokens"),

	/*
	 * ==========================================================================
	 * Java files - Symbols
	 * ==========================================================================
	 */

	/** The document symbols (outline) of Java, Spring XML and {@code spring.factories} files. The underlying index stays active */
	DOCUMENT_SYMBOLS("document-symbols"),

	/** The workspace symbol search (not the live application URL queries starting with //). The underlying index stays active */
	WORKSPACE_SYMBOLS("workspace-symbols"),

	/*
	 * ==========================================================================
	 * Property files (application.properties, application.yml) - Completion
	 * ==========================================================================
	 */

	/** Property key and value proposals in {@code application.properties} files */
	PROPERTIES_COMPLETION("properties-completion"),

	/** Property key and value proposals in {@code application.yml} / {@code application.yaml} files */
	YAML_COMPLETION("yaml-completion"),

	/*
	 * ==========================================================================
	 * Property files (application.properties, application.yml) - Navigation (go to definition)
	 * ==========================================================================
	 */

	/** Go to definition on property keys and values in {@code application.properties} files */
	PROPERTIES_NAVIGATION("properties-navigation"),

	/** Go to definition on property keys and values in {@code application.yml} / {@code application.yaml} files */
	YAML_NAVIGATION("yaml-navigation"),

	/*
	 * ==========================================================================
	 * Property files (application.properties, application.yml) - Hover
	 * ==========================================================================
	 */

	/** Hovers on property keys in {@code application.properties} files */
	PROPERTIES_HOVER("properties-hover"),

	/** Hovers on property keys in {@code application.yml} / {@code application.yaml} files */
	YAML_HOVER("yaml-hover"),

	/*
	 * ==========================================================================
	 * Property files (application.properties, application.yml) - Semantic tokens (syntax highlighting)
	 *
	 * No entries of their own: CRON_SEMANTIC_TOKENS (Java files - Semantic tokens) covers these files too
	 * ==========================================================================
	 */

	/*
	 * ==========================================================================
	 * Named query properties files
	 *
	 * No entries of their own: DATA_QUERY_SEMANTIC_TOKENS (Java files - Semantic tokens) covers these files too
	 * ==========================================================================
	 */

	/*
	 * ==========================================================================
	 * pom.xml files
	 * ==========================================================================
	 */

	/** Spring Boot version and starter inlay hints in {@code pom.xml} files */
	POM_INLAY_HINTS("pom-inlay-hints");

	private final String id;

	FeatureFlag(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public static Optional<FeatureFlag> fromId(String id) {
		return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
	}

}
