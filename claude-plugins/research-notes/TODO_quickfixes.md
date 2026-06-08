# Spring Tools Validation Problem Types & Quick Fixes

This document tracks all validation problem types defined in the Spring Tools codebase, their corresponding quick fix implementations (JDT Refactoring or OpenRewrite recipe), and whether they have an explanation playbook in the Claude plugin.

Use this as a TODO list to implement missing explanation playbooks (`.md` files) in `claude-plugins/spring-tools/explanations/`.

## Boot 2 Java Problem Types (`Boot2JavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `PATH_IN_CONTROLLER_ANNOTATION` | Controller annotation default attribute might contain a path | OpenRewrite: `org.openrewrite.java.spring.boot2.NoPathInControllerAnnotation` | ✅ |
| `JAVA_AUTOWIRED_CONSTRUCTOR` | Unnecessary `@Autowired` over the only constructor | JDT Refactoring (`NoAutowiredOnConstructorReconciler`) | ❌ |
| `JAVA_PUBLIC_BEAN_METHOD` | Public modifier on `@Bean` method | JDT Refactoring (`BeanMethodNotPublicReconciler`) | ✅ |
| `JAVA_TEST_SPRING_EXTENSION` | Unnecessary `@SpringExtension` | OpenRewrite: `org.openrewrite.java.spring.boot2.UnnecessarySpringExtension` | ❌ |
| `JAVA_CONSTRUCTOR_PARAMETER_INJECTION` | Use constructor parameter injection | OpenRewrite: `org.springframework.ide.vscode.commons.rewrite.java.ConvertAutowiredFieldIntoConstructorParameter` | ✅ |
| `JAVA_PRECISE_REQUEST_MAPPING` | Use precise mapping annotation | OpenRewrite: `org.openrewrite.java.spring.NoRequestMappingAnnotation` | ✅ |
| `JAVA_REPOSITORY` | Unnecessary `@Repository` | JDT Refactoring (`NoRepoAnnotationReconciler`) | ❌ |
| `JAVA_LAMBDA_DSL` | Consider switching to Lambda DSL syntax | OpenRewrite: `org.openrewrite.java.spring.security5.HttpSecurityLambdaDsl` | ✅ |
| `MISSING_CONFIGURATION_ANNOTATION` | Missing `@Configuration` | OpenRewrite: `org.openrewrite.java.spring.boot2.AddConfigurationAnnotationIfBeansPresent` | ✅ |
| `HTTP_SECURITY_AUTHORIZE_HTTP_REQUESTS` | Usage of old `HttpSecurity.authorizeRequests(...)` | OpenRewrite: `org.openrewrite.java.spring.security5.AuthorizeHttpRequests` | ✅ |
| `WEB_SECURITY_CONFIGURER_ADAPTER` | `WebSecurityConfigurerAdapter` is removed | OpenRewrite: `org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter` | ❌ |
| `DOMAIN_ID_FOR_REPOSITORY` | Invalid Domain ID type for Spring Data Repository | *None* | ❌ |
| `WEB_ANNOTATION_NAMES` | Implicit web annotations names | OpenRewrite: `org.openrewrite.java.spring.ImplicitWebAnnotationNames` | ✅ |
| `VALUE_CLASSPATH_RESOURCE_TYPE` | Invalid type for classpath resource in `@Value` | *None* | ❌ |
| `WEB_CONFIGURER_CONFIGURATION` | Missing `@Configuration` on web configurer | OpenRewrite: `org.openrewrite.java.spring.boot2.AddConfigurationAnnotation` | ❌ |
| `MISSING_VALIDATED_ANNOTATION` | Missing `@Validated` on component | OpenRewrite: `org.openrewrite.java.spring.boot2.AddValidatedAnnotation` | ❌ |
| `JAVA_FINAL_AUTOWIRED_FIELD` | `@Autowired` field should not be `final` | *None* | ❌ |

## Boot 3 Java Problem Types (`Boot3JavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `JAVA_TYPE_NOT_SUPPORTED` | Type not supported as of Spring Boot 3 | *None* | ❌ |
| `FACTORIES_KEY_NOT_SUPPORTED` | Spring factories key not supported | *None* | ❌ |
| `MODULITH_TYPE_REF_VIOLATION` | Modulith restricted type reference | *None* | ❌ |

## Boot 4 Java Problem Types (`Boot4JavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `REGISTRAR_BEAN_INVALID_ANNOTATION` | Invalid annotation over bean registrar | OpenRewrite: `org.openrewrite.java.RemoveAnnotation` | ❌ |
| `REGISTRAR_BEAN_DECLARATION` | Not added to configuration via `@Import` | OpenRewrite: `org.springframework.ide.vscode.commons.rewrite.java.ImportBeanRegistrarInConfigRecipe` | ❌ |
| `API_VERSIONING_NOT_CONFIGURED` | API Versioning not configured anywhere | OpenRewrite: `org.openrewrite.java.spring.AddSpringProperty` / `AddBeanRecipe` | ✅ |
| `API_VERSION_SYNTAX_ERROR` | API version cannot be parsed | *None* | ❌ |
| `API_VERSIONING_VIA_PATH_SEGMENT_CONFIGURED_IN_COMBINATION` | Strategy should not be mixed | *None* | ❌ |
| `API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED` | Strategy configured multiple times | *None* | ❌ |
| `SPRING_DATA_STRING_PROPERTY_REFERENCE` | Non type-safe property reference | JDT Refactoring (`SpringDataPropertyReferenceReconciler`) | ❌ |

## Spring AOT Java Problem Types (`SpringAotJavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `JAVA_CONCRETE_BEAN_TYPE` | Not precise bean definition type | OpenRewrite: `org.openrewrite.java.spring.boot3.PreciseBeanType` | ❌ |
| `JAVA_BEAN_POST_PROCESSOR_IGNORED_IN_AOT` | `BeanPostProcessor` behaviour is ignored in AOT | OpenRewrite: `org.openrewrite.java.spring.boot3.BeanPostProcessingIgnoreInAot` | ❌ |
| `JAVA_BEAN_NOT_REGISTERED_IN_AOT` | Not registered as a Bean | OpenRewrite: `org.springframework.ide.vscode.commons.rewrite.java.DefineMethod` | ❌ |

## Spring AI Problem Types (`SpringAiProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `SPRING_AI_TOOL_MISSING_DESCRIPTION` | Missing `@Tool` description | *None* | ❌ |
| `SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT` | `@Tool` description too short | *None* | ❌ |
