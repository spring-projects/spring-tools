# Spring Tools Validation Problem Types & Quick Fixes

This document tracks all validation problem types defined in the Spring Tools language server, their corresponding quick fix implementations (JDT refactoring or OpenRewrite recipe), and whether they have an explanation playbook in the Claude plugin (`claude-plugins/spring-tools/explanations/<CODE>.md`).

The table is kept in sync with the source and the playbook files by `node claude-plugins/tools/check-explanations.mjs --require-all --todo` (also run in CI): every code found in the `*ProblemType` enums and the `BOOT_VERSION_VALIDATION_CODE` constant must have a row here, every row must name an existing code, and the Playbook column must match the files on disk.

When a new problem type is added to the language server, add a row to the matching section below, write the playbook (see the "Explanation playbooks" section of the plugin README for the structure and the authoring checklist), and run the checker.

## Boot 2 Java Problem Types (`Boot2JavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `PATH_IN_CONTROLLER_ANNOTATION` | Controller annotation default attribute might contain a path | OpenRewrite: `org.openrewrite.java.spring.boot2.NoPathInControllerAnnotation` | ✅ |
| `JAVA_AUTOWIRED_CONSTRUCTOR` | Unnecessary `@Autowired` over the only constructor | JDT Refactoring (`NoAutowiredOnConstructorReconciler`) | ✅ |
| `JAVA_PUBLIC_BEAN_METHOD` | Public modifier on `@Bean` method | JDT Refactoring (`BeanMethodNotPublicReconciler`) | ✅ |
| `JAVA_TEST_SPRING_EXTENSION` | Unnecessary `@SpringExtension` | OpenRewrite: `org.openrewrite.java.spring.boot2.UnnecessarySpringExtension` | ✅ |
| `JAVA_CONSTRUCTOR_PARAMETER_INJECTION` | Use constructor parameter injection | OpenRewrite: `org.springframework.ide.vscode.commons.rewrite.java.ConvertAutowiredFieldIntoConstructorParameter` | ✅ |
| `JAVA_PRECISE_REQUEST_MAPPING` | Use precise mapping annotation | OpenRewrite: `org.openrewrite.java.spring.NoRequestMappingAnnotation` | ✅ |
| `JAVA_REPOSITORY` | Unnecessary `@Repository` | JDT Refactoring (`NoRepoAnnotationReconciler`) | ✅ |
| `JAVA_PRECISE_SCOPE` | Use precise scope annotation (`@RequestScope`, `@SessionScope`, `@ApplicationScope`) | JDT Refactoring (`ScopeAnnotationReconciler` / `ReplaceScopeAnnotationRefactoring`) | ✅ |
| `JAVA_LAMBDA_DSL` | Consider switching to Lambda DSL syntax | OpenRewrite: `org.openrewrite.java.spring.security5.HttpSecurityLambdaDsl` | ✅ |
| `MISSING_CONFIGURATION_ANNOTATION` | Missing `@Configuration` | OpenRewrite: `org.openrewrite.java.spring.boot2.AddConfigurationAnnotationIfBeansPresent` | ✅ |
| `HTTP_SECURITY_AUTHORIZE_HTTP_REQUESTS` | Usage of old `HttpSecurity.authorizeRequests(...)` | OpenRewrite: `org.openrewrite.java.spring.security5.AuthorizeHttpRequests` | ✅ |
| `WEB_SECURITY_CONFIGURER_ADAPTER` | `WebSecurityConfigurerAdapter` is removed | OpenRewrite: `org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter` | ✅ |
| `DOMAIN_ID_FOR_REPOSITORY` | Invalid Domain ID type for Spring Data Repository | *None* | ✅ |
| `WEB_ANNOTATION_NAMES` | Implicit web annotations names | OpenRewrite: `org.openrewrite.java.spring.ImplicitWebAnnotationNames` | ✅ |
| `VALUE_CLASSPATH_RESOURCE_TYPE` | Invalid type for classpath resource in `@Value` | *None* | ✅ |
| `WEB_CONFIGURER_CONFIGURATION` | Missing `@Configuration` on web configurer | OpenRewrite: `org.openrewrite.java.spring.boot2.AddConfigurationAnnotation` | ✅ |
| `MISSING_VALIDATED_ANNOTATION` | Missing `@Validated` on component | OpenRewrite: `org.openrewrite.java.spring.boot2.AddValidatedAnnotation` | ✅ |
| `JAVA_FINAL_AUTOWIRED_FIELD` | `@Autowired` field should not be `final` | *None* | ✅ |
| `EXTRACT_REQUEST_MAPPING_PARENT_PATH` | Request mappings share a common parent path that could be extracted into a class-level `@RequestMapping` | JDT Refactoring (`ExtractRequestMappingParentPathReconciler` / `ExtractRequestMappingParentPathRefactoring`) | ✅ |
| `REST_CONTROLLER_COMBINATION` | `@Controller` + `@ResponseBody` could use `@RestController` | JDT Refactoring (`RestControllerReconciler` / `RestControllerRefactoring`) | ✅ |
| `SPRING_JUNIT_CONFIG_COMBINATION` | `@ExtendWith(SpringExtension.class)` + `@ContextConfiguration` could use `@SpringJUnitConfig` | JDT Refactoring (`SpringJUnitConfigReconciler` / `SpringJUnitConfigRefactoring`) | ✅ |

## Boot 3 Java Problem Types (`Boot3JavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `JAVA_TYPE_NOT_SUPPORTED` | Type not supported as of Spring Boot 3 | *None* | ✅ |
| `FACTORIES_KEY_NOT_SUPPORTED` | Spring factories key not supported (`META-INF/spring.factories`, reconciled for open documents and by `SpringIndexerConfigFiles` during indexing) | *None* | ✅ |
| `MODULITH_TYPE_REF_VIOLATION` | Modulith restricted type reference | *None* | ✅ |
| `MODULITH_APPLICATION_MODULE_LISTENER` | `@Async` + `@Transactional` + `@TransactionalEventListener` could use `@ApplicationModuleListener` | JDT Refactoring (`ApplicationModuleListenerReconciler` / `ApplicationModuleListenerRefactoring`) | ✅ |

## Boot 4 Java Problem Types (`Boot4JavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `REGISTRAR_BEAN_INVALID_ANNOTATION` | Invalid annotation over bean registrar | OpenRewrite: `org.openrewrite.java.RemoveAnnotation` | ✅ |
| `REGISTRAR_BEAN_DECLARATION` | Not added to configuration via `@Import` | OpenRewrite: `org.springframework.ide.vscode.commons.rewrite.java.ImportBeanRegistrarInConfigRecipe` | ✅ |
| `API_VERSIONING_NOT_CONFIGURED` | API Versioning not configured anywhere | OpenRewrite: `org.openrewrite.java.spring.AddSpringProperty` / `AddBeanRecipe` | ✅ |
| `API_VERSION_SYNTAX_ERROR` | API version cannot be parsed | *None* | ✅ |
| `API_VERSIONING_VIA_PATH_SEGMENT_CONFIGURED_IN_COMBINATION` | Strategy should not be mixed | *None* | ✅ |
| `API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED` | Strategy configured multiple times | *None* | ✅ |
| `SPRING_DATA_STRING_PROPERTY_REFERENCE` | Non type-safe property reference | JDT Refactoring (`SpringDataPropertyReferenceReconciler`) | ✅ |

## Spring AOT Java Problem Types (`SpringAotJavaProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `JAVA_CONCRETE_BEAN_TYPE` | Not precise bean definition type | OpenRewrite: `org.openrewrite.java.spring.boot3.PreciseBeanType` | ✅ |
| `JAVA_BEAN_POST_PROCESSOR_IGNORED_IN_AOT` | `BeanPostProcessor` behaviour is ignored in AOT | OpenRewrite: `org.openrewrite.java.spring.boot3.BeanPostProcessingIgnoreInAot` | ✅ |
| `JAVA_BEAN_NOT_REGISTERED_IN_AOT` | Not registered as a Bean | OpenRewrite: `org.springframework.ide.vscode.commons.rewrite.java.DefineMethod` | ✅ |

## Spring AI Problem Types (`SpringAiProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `SPRING_AI_TOOL_MISSING_DESCRIPTION` | Missing `@Tool` description | *None* | ✅ |
| `SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT` | `@Tool` description too short | *None* | ✅ |

## SpEL Problem Types (`SpelProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `JAVA_SPEL_EXPRESSION_SYNTAX` | SpEL expression syntax error in `#{...}` (e.g. `@Value`, caching, `@EventListener`, method security annotations) | *None* | ✅ |
| `PROPERTY_PLACE_HOLDER_SYNTAX` | Property placeholder `${...}` syntax error inside a SpEL expression | *None* | ✅ |

## Cron Problem Types (`cron/CronProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `CRON_SYNTAX` | `@Scheduled(cron = ...)` expression does not match the six-field / macro grammar | *None* | ✅ |
| `CRON_FIELD` | `@Scheduled(cron = ...)` field value rejected by Spring's `CronExpression` field parsing (message prefixed `CRON:`) | *None* | ✅ |

## Data Query Problem Types (`data/jpa/queries/QueryProblemType`)

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `JPQL_SYNTAX` | JPQL syntax error in `@Query` / `@NamedQuery` / `EntityManager.createQuery(...)` (no `hibernate-core` on the classpath) | *None* | ✅ |
| `HQL_SYNTAX` | HQL syntax error in the same locations when `hibernate-core` is present | *None* | ✅ |
| `SQL_SYNTAX` | Native SQL syntax error (`nativeQuery = true`, `@NativeQuery`, Spring Data JDBC `@Query`); MySQL or PostgreSQL grammar chosen from the JDBC driver dependency | *None* | ✅ |

## Spring Boot Version Validation (`validation/generations/AbstractDiagnosticValidator`)

Not an enum: all `VersionValidationProblemType` checks (OSS/commercial support ending or ended, newer patch/minor/major available, Spring Cloud compatibility) share the single diagnostic code below and are told apart by their message text.

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `BOOT_VERSION_VALIDATION_CODE` | Spring Boot version support / upgrade diagnostics on the build file | Version upgrade quick fixes on `pom.xml` / `build.gradle` | ✅ |

## Properties Config File Problem Types (`properties/reconcile/ApplicationPropertiesProblemType`)

Raised on `application*.properties` / `bootstrap*.properties` files by `SpringPropertiesReconcileEngine` (plus `PropertyNavigator` for the bracket/dot navigation codes). Severity is configured per code via `spring-boot.ls.problem.application-properties.<CODE>`. Besides the document reconciler for open files, `SpringIndexerConfigFiles` runs the same engine over the project's config files during indexing so that `getProjectDiagnostics` reports these codes without an editor.

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `PROP_SYNTAX_ERROR` | Line cannot be parsed as a `key=value` / `key: value` assignment | *None* | ✅ |
| `PROP_UNKNOWN_PROPERTY` | Key matches no property in the configuration metadata | Create metadata for `<key>` (`CommonQuickfixes.MISSING_PROPERTY`, id `MISSING_PROPERTY_APP`) | ✅ |
| `PROP_DEPRECATED` | Property is deprecated in the configuration metadata (any level) | Replace with `<replacement>` (`AppPropertiesQuickFixes.DEPRECATED_PROPERTY`) | ✅ |
| `PROP_DUPLICATE_KEY` | Same key assigned more than once in one document | *None* | ✅ |
| `PROP_VALUE_TYPE_MISMATCH` | Value cannot be converted to the property's declared type | *None* | ✅ |
| `PROP_INVALID_BEAN_NAVIGATION` | `.` navigation into a type that has no bean properties | *None* | ✅ |
| `PROP_INVALID_INDEXED_NAVIGATION` | `[index]` navigation into a type that is not indexable | *None* | ✅ |
| `PROP_EXPECTED_DOT_OR_LBRACK` | Unexpected characters after a property name (expected `.` or `[`) | *None* | ✅ |
| `PROP_NO_MATCHING_RBRACK` | `[` without a closing `]` | *None* | ✅ |
| `PROP_NON_INTEGER_IN_BRACKETS` | Non-integer index in `[...]` on a list/array property | *None* | ✅ |
| `PROP_INVALID_BEAN_PROPERTY` | Unknown nested property for the navigated bean type | *None* | ✅ |

## YAML Config File Problem Types (`yaml/reconcile/ApplicationYamlProblemType`)

Raised on `application*.yml`/`.yaml` and `bootstrap*.yml`/`.yaml` files by `ApplicationYamlReconcileEngine` / `ApplicationYamlASTReconciler`, both for open documents and - via `SpringIndexerConfigFiles` - for every config file of an indexed project, so `getProjectDiagnostics` reports these codes without an editor. Severity is configured per code via `spring-boot.ls.problem.application-yaml.<CODE>`. The last row is not an enum constant: the reconciler flattens `<<` merge keys with commons-yaml's `NodeMergeSupport`, which reports malformed merge values under the generic `YamlSchemaProblem` code (severity key `spring-boot.ls.problem.yaml-schema-problems.YamlSchemaProblem`).

| Problem Type Code | Description | Quick Fix Implementation | Playbook |
| :--- | :--- | :--- | :---: |
| `YAML_SYNTAX_ERROR` | SnakeYAML parse error | *None* | ✅ |
| `YAML_UNKNOWN_PROPERTY` | Key path matches no property in the configuration metadata | Create metadata for `<key>` (`CommonQuickfixes.MISSING_PROPERTY`, id `MISSING_PROPERTY_APP`) | ✅ |
| `YAML_DEPRECATED_WARNING` | Property deprecated at `warning` level | Replace with `<replacement>` (`AppYamlQuickfixes.DEPRECATED_PROPERTY`, id `DEPRECATED_YAML_PROPERTY`) | ✅ |
| `YAML_DEPRECATED_ERROR` | Property deprecated at `error` level (no longer bound) | Replace with `<replacement>` (`AppYamlQuickfixes.DEPRECATED_PROPERTY`, id `DEPRECATED_YAML_PROPERTY`) | ✅ |
| `YAML_DUPLICATE_KEY` | Same key twice within one mapping node | *None* | ✅ |
| `YAML_VALUE_TYPE_MISMATCH` | Scalar cannot be converted to the declared type | *None* | ✅ |
| `YAML_EXPECT_TYPE_FOUND_MAPPING` | Mapping node where an atomic value is expected | *None* | ✅ |
| `YAML_EXPECT_TYPE_FOUND_SEQUENCE` | Sequence node where a non-sequencable type is expected | *None* | ✅ |
| `YAML_EXPECT_MAPPING` | Scalar/sequence under a property-group prefix that needs nested keys | *None* | ✅ |
| `YAML_EXPECT_SCALAR` | Non-scalar key node during metadata navigation | *None* | ✅ |
| `YAML_EXPECT_BEAN_PROPERTY_NAME` | Non-scalar key inside a bean-typed mapping | *None* | ✅ |
| `YAML_INVALID_BEAN_PROPERTY` | Unknown property for the bean type | *None* | ✅ |
| `YAML_SHOULD_ESCAPE` | Map key with special characters should be wrapped in `[]` | *None* | ✅ |
| `YamlSchemaProblem` | Malformed `<<` merge key (value is not a mapping or a list of mappings); inherited from commons-yaml `YamlSchemaProblems.SCHEMA_PROBLEM` via `NodeMergeSupport`, category `yaml-schema-problems` | *None* | ✅ |
