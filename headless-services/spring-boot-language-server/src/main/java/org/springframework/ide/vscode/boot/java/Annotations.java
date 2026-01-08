/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import java.util.Map;
import java.util.Set;

/**
 * Constants containing various fully-qualified annotation names.
 *
 * @author Kris De Volder
 */
public class Annotations {
	
	// Context
	
	public static final String BEAN = "org.springframework.context.annotation.Bean";
	public static final String PROFILE = "org.springframework.context.annotation.Profile";
	public static final String CONDITIONAL = "org.springframework.context.annotation.Conditional";
	public static final String IMPORT = "org.springframework.context.annotation.Import";
	public static final String CONFIGURATION = "org.springframework.context.annotation.Configuration";

	public static final String COMPONENT = "org.springframework.stereotype.Component";
	public static final String CONTROLLER = "org.springframework.stereotype.Controller";
	
	public static final String SCOPE = "org.springframework.context.annotation.Scope";
	public static final String DEPENDS_ON = "org.springframework.context.annotation.DependsOn";

	public static final String EVENT_LISTENER = "org.springframework.context.event.EventListener";

	public static final String APPLICATION_LISTENER = "org.springframework.context.ApplicationListener";
	public static final String EVENT_PUBLISHER = "org.springframework.context.ApplicationEventPublisher";

	public static final String CONTEXT_CONFIGURATION = "org.springframework.test.context.ContextConfiguration";
	public static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";
	
	// Beans
	
	public static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
	public static final String QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";
	public static final String VALUE = "org.springframework.beans.factory.annotation.Value";
	
	public static final String BEAN_REGISTRAR_INTERFACE = "org.springframework.beans.factory.BeanRegistrar";
	public static final String BEAN_REGISTRY_INTERFACE = "org.springframework.beans.factory.BeanRegistry";

	// Javax - Jakarta
	
	public static final String RESOURCE_JAVAX = "javax.annotation.Resource";
	public static final String RESOURCE_JAKARTA = "jakarta.annotation.Resource";
	
	public static final String INJECT_JAVAX = "javax.inject.Inject";
	public static final String INJECT_JAKARTA = "jakarta.inject.Inject";

	public static final String NAMED_JAVAX = "javax.inject.Named";
	public static final String NAMED_JAKARTA = "jakarta.inject.Named";
	
	public static final Set<String> NAMED_ANNOTATIONS = Set.of(Annotations.NAMED_JAKARTA, Annotations.NAMED_JAVAX);

	public static final Set<String> JAKARTA_ANNOTATIONS = Set.of(
			Annotations.RESOURCE_JAKARTA, Annotations.INJECT_JAKARTA, Annotations.NAMED_JAKARTA,
			Annotations.RESOURCE_JAVAX, Annotations.INJECT_JAVAX, Annotations.NAMED_JAVAX
	);

	// Web
	
	public static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";

	public static final String SPRING_REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
	public static final String SPRING_GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
	public static final String SPRING_POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
	public static final String SPRING_PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
	public static final String SPRING_DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
	public static final String SPRING_PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";
	
	public static final String HTTP_EXCHANGE = "org.springframework.web.service.annotation.HttpExchange";
	public static final String GET_EXCHANGE = "org.springframework.web.service.annotation.GetExchange";
	public static final String POST_EXCHANGE = "org.springframework.web.service.annotation.PostExchange";
	public static final String PUT_EXCHANGE = "org.springframework.web.service.annotation.PutExchange";
	public static final String DELETE_EXCHANGE = "org.springframework.web.service.annotation.DeleteExchange";
	public static final String PATCH_EXCHANGE = "org.springframework.web.service.annotation.PatchExchange";
	
	public static final String WEB_MVC_CONFIGURER_INTERFACE = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
	public static final String WEB_MVC_API_VERSION_CONFIGURER_INTERFACE = "org.springframework.web.servlet.config.annotation.ApiVersionConfigurer";
	public static final String WEB_MVC_PATH_MATCH_CONFIGURER_INTERFACE = "org.springframework.web.servlet.config.annotation.PathMatchConfigurer";
	
	public static final String WEB_FLUX_CONFIGURER_INTERFACE = "org.springframework.web.reactive.config.WebFluxConfigurer";
	public static final String WEB_FLUX_API_VERSION_CONFIGURER_INTERFACE = "org.springframework.web.reactive.config.ApiVersionConfigurer";
	public static final String WEB_FLUX_PATH_MATCH_CONFIGURER_INTERFACE = "org.springframework.web.reactive.config.PathMatchConfigurer";

	// Boot

	public static final String BOOT_APP = "org.springframework.boot.autoconfigure.SpringBootApplication";
	public static final String CONDITIONAL_ON_BEAN = "org.springframework.boot.autoconfigure.condition.ConditionalOnBean";
	public static final String CONDITIONAL_ON_MISSING_BEAN = "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean";
	public static final String CONDITIONAL_ON_PROPERTY = "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty";
	public static final String CONDITIONAL_ON_RESOURCE = "org.springframework.boot.autoconfigure.condition.ConditionalOnResource";
	public static final String CONDITIONAL_ON_CLASS = "org.springframework.boot.autoconfigure.condition.ConditionalOnClass";
	public static final String CONDITIONAL_ON_MISSING_CLASS = "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass";
	public static final String CONDITIONAL_ON_CLOUD_PLATFORM = "org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform";
	public static final String CONDITIONAL_ON_WEB_APPLICATION = "org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication";
	public static final String CONDITIONAL_ON_NOT_WEB_APPLICATION = "org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication";
	public static final String CONDITIONAL_ON_ENABLED_INFO_CONTRIBUTOR = "org.springframework.boot.actuate.autoconfigure.ConditionalOnEnabledInfoContributor";
	public static final String CONDITIONAL_ON_ENABLED_RESOURCE_CHAIN = "org.springframework.boot.autoconfigure.web.ConditionalOnEnabledResourceChain";
	public static final String CONDITIONAL_ON_ENABLED_ENDPOINT = "org.springframework.boot.actuate.condition.ConditionalOnEnabledEndpoint";             // <========= double checking necessary
	public static final String CONDITIONAL_ON_ENABLED_HEALTH_INDICATOR = "org.springframework.boot.actuate.autoconfigure.ConditionalOnEnabledHealthIndicator";
	public static final String CONDITIONAL_ON_EXPRESSION = "org.springframework.boot.autoconfigure.condition.ConditionalOnExpression";
	public static final String CONDITIONAL_ON_JAVA = "org.springframework.boot.autoconfigure.condition.ConditionalOnJava";
	public static final String CONDITIONAL_ON_JNDI = "org.springframework.boot.autoconfigure.condition.ConditionalOnJndi";
	public static final String CONDITIONAL_ON_SINGLE_CANDIDATE = "org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate";

	public static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";
	
	public static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";

	// Data

	public static final String REPOSITORY = "org.springframework.stereotype.Repository";
	public static final String REPOSITORY_DEFINITION = "org.springframework.data.repository.RepositoryDefinition";
	public static final String NO_REPO_BEAN = "org.springframework.data.repository.NoRepositoryBean";
	public static final String SPRING_ENTITY_ID = "org.springframework.data.annotation.Id";
	
	public static final String JPA_JAKARTA_ENTITY_ID = "jakarta.persistence.Id"; 
	public static final String JPA_JAVAX_ENTITY_ID = "javax.persistence.Id";
	public static final String JPA_JAKARTA_EMBEDDED_ID = "jakarta.persistence.EmbeddedId"; 
	public static final String JPA_JAVAX_EMBEDDED_ID = "javax.persistence.EmbeddedId";
	public static final String JPA_JAKARTA_ID_CLASS = "jakarta.persistence.IdClass"; 
	public static final String JPA_JAVAX_ID_CLASS = "javax.persistence.IdClass";
	public static final String JPA_JAKARTA_NAMED_QUERY = "jakarta.persistence.NamedQuery"; 
	public static final String JPA_JAVAX_NAMED_QUERY = "javax.persistence.NamedQuery";

	public static final String DATA_QUERY_META_ANNOTATION = "org.springframework.data.annotation.QueryAnnotation";
	public static final String DATA_JPA_QUERY = "org.springframework.data.jpa.repository.Query";
	public static final String DATA_JPA_NATIVE_QUERY = "org.springframework.data.jpa.repository.NativeQuery";
	public static final String DATA_MONGODB_QUERY = "org.springframework.data.mongodb.repository.Query";
	public static final String DATA_REST_BASE_PATH_AWARE_CONTROLLER = "org.springframework.data.rest.webmvc.BasePathAwareController";
	public static final String DATA_JDBC_QUERY = "org.springframework.data.jdbc.repository.query.Query";
	
	public static final String PROPERTY_NAME = "org.springframework.boot.context.properties.bind.Name";

	// Cloud
	
	public static final String FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient";
	
	// AOP
	
	public static final Map<String, String> AOP_ANNOTATIONS = Map.of(
	        "org.aspectj.lang.annotation.Pointcut", "Pointcut",
	        "org.aspectj.lang.annotation.Before", "Before",
	        "org.aspectj.lang.annotation.Around", "Around",
	        "org.aspectj.lang.annotation.After", "After",
	        "org.aspectj.lang.annotation.AfterReturning", "AfterReturning",
	        "org.aspectj.lang.annotation.AfterThrowing", "AfterThrowing",
	        "org.aspectj.lang.annotation.DeclareParents", "DeclareParents"
	);
	
	// JMolecules
	
	public static final String JMOLECULES_STEREOTYPE = "org.jmolecules.stereotype.Stereotype";
	
	// Lombok
	
	public static final Set<String> LOMBOK_CONSTRUCTOR_ANNOTATIONS = Set.of(
			"lombok.RequiredArgsConstructor",
			"lombok.NoArgsConstructor",
			"lombok.AllArgsConstructor"
	);
	
}
