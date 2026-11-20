# Feature flags

Individual features of the Spring Boot language server can be turned off with the `boot-java.feature-flags` setting.
It is an object that maps a feature id to `true` (on) or `false` (off):

```json
"boot-java.feature-flags": {
  "value-property-completion": false,
  "bean-name-navigation": false
}
```

- A feature is off only if its id is explicitly set to `false`. Everything else leaves it on: a missing setting, a missing id, a non-boolean value, or an unknown id.
- Changes apply without restarting the language server. When the flags change, the language server asks the client to refresh inlay hints, code lenses and semantic tokens. The document outline has no such refresh request in the protocol and updates with the next edit of the file.
- Flags only switch features off. Existing dedicated settings (for example the code lens settings) still apply on top of them.
- The Eclipse client does not send this setting yet, so all features stay on there.
- Where the setting goes depends on the client. In VS Code, put it in `settings.json`. VS Code merges object-valued settings across scopes, so a workspace can re-enable a single id.

## Java files

### Completion

| Id | Description |
|---|---|
| `value-property-completion` | Property key proposals in `@Value` |
| `value-classpath-completion` | `classpath:` resource proposals in `@Value` |
| `bean-name-completion` | Bean name proposals in `@DependsOn`, `@Qualifier`, `@Resource`, `@Named`, `@ConditionalOn(Missing)Bean` |
| `bean-type-completion` | Bean type proposals in `@ConditionalOn(Missing)Bean` |
| `profile-completion` | Profile proposals in `@Profile` |
| `conditional-on-property-completion` | Key proposals in `@ConditionalOnProperty` |
| `conditional-on-resource-completion` | Resource proposals in `@ConditionalOnResource` |
| `context-configuration-completion` | Resource proposals in `@ContextConfiguration` |
| `scope-completion` | Scope proposals in `@Scope` |
| `cron-completion` | Cron expression proposals in `@Scheduled` |
| `data-repository-completion` | Query method name proposals in Spring Data repository interfaces |
| `java-snippet-completion` | Java snippet proposals, e.g. request mapping methods in controllers |

### Navigation (go to definition and references)

| Id | Description |
|---|---|
| `property-key-navigation` | Definition and references on property keys in `@Value` and `@ConditionalOnProperty` |
| `bean-name-navigation` | Definition and references on bean names in `@DependsOn`, `@Qualifier`, `@Resource`, `@Named`, `@ConditionalOn(Missing)Bean` |
| `profile-navigation` | References on profile names in `@Profile` |
| `classpath-resource-navigation` | Definition on `classpath:` resources in annotation values |
| `data-query-parameter-navigation` | Definition on parameters in data queries |
| `spel-navigation` | Definition in SpEL expressions |
| `aot-query-method-implementation-navigation` | Go to the generated implementation of Spring Data query methods |
| `event-navigation` | References on application events |

### Code actions and quick fixes

| Id | Description |
|---|---|
| `ignored-problem-fixes` | Quick fixes for problems whose severity is set to `IGNORE`, offered as refactorings |
| `query-text-block-conversion` | The "Convert query to text block" code action |
| `add-query-annotation` | The "Add `@Query`" code action for query methods |
| `sql-dialect-quickfix` | The quick fix to set the SQL dialect for native queries |

### Code lenses

| Id | Description |
|---|---|
| `webflux-handler-code-lens` | Code lens on WebFlux handler methods |
| `ai-assistant-code-lens` | Code lenses that send queries, SpEL and pointcut expressions to an AI assistant |
| `router-function-code-lens` | Code lens on functional router methods to convert them to the builder pattern |
| `data-query-aot-code-lens` | Code lens with the AOT generated query on Spring Data query methods |
| `web-config-code-lens` | Code lens with web config details on controller classes |

### Document highlights

| Id | Description |
|---|---|
| `webflux-route-highlight` | Highlights of the parts of a WebFlux route |
| `data-query-highlight` | Highlights of parameters and other parts in data queries (JPQL, HQL, SQL) |

### Inlay hints

| Id | Description |
|---|---|
| `data-query-inlay-hints` | Parameter name hints for ordinal parameters (`?1`) in data queries |

Cron expression hints have no flag. They are switched with the existing setting `boot-java.cron.inlay-hints`, which applies to Java, properties and YAML files.

### Semantic tokens (syntax highlighting)

| Id | Description |
|---|---|
| `data-query-semantic-tokens` | Highlighting of data queries (JPQL, HQL, SQL) |
| `spel-semantic-tokens` | Highlighting of SpEL expressions |
| `cron-semantic-tokens` | Highlighting of cron expressions |

Other ways to switch things off:

- `boot-java.embedded-syntax-highlighting` turns off the highlighting of all embedded languages in Java files (queries, SpEL, cron) at once.
- `boot-java.jpql` turns off the JPA query support as a whole, including highlighting, hints and highlights for queries.
- The general Java highlighting of the language server is not a Spring feature and has no flag.

### Symbols

| Id | Description |
|---|---|
| `document-symbols` | The document symbols (outline) of Java files, Spring XML config files and `spring.factories` files |
| `workspace-symbols` | The workspace symbol search for Spring elements such as beans and request mappings |

Both flags only guard what the client can retrieve. The underlying index of the Spring elements stays active, so everything else that is based on it (navigation, completion, validation, the structure view) keeps working. The live application URL queries in the workspace symbol search (queries starting with `//`) are live data and are not covered by the flag.

### Live data hovers (no feature flags)

The hovers on `@Value`, `@RequestMapping` and friends, `@Profile`, `@Autowired`, `@Component`, `@Bean` and `@Conditional*`, as well as the code lenses that mark running-app information, are all live data features. The language server only produces them while it is connected to a running Spring Boot application. Without a connected application there is nothing to show.

There are deliberately no feature flags for the individual live data hovers. To not use live data at all, instead of switching off single hovers:

- Do not connect to a running application. Connecting is an explicit action, so nothing appears unless you do it.
- Set `boot-java.live-information.automatic-connection.on` to `false`, so applications are not started with the JVM arguments that enable JMX and the connection.
- Keep `boot-java.remote-apps` empty (the default), so no remote applications are polled.
- Set `boot-java.highlight-codelens.on` to `false` to hide the code lenses for running applications.

## Property files (`application.properties`, `application.yml`)

### Completion

| Id | Description |
|---|---|
| `properties-completion` | Property key and value proposals in `application.properties` files |
| `yaml-completion` | Property key and value proposals in `application.yml` / `application.yaml` files |

### Navigation (go to definition)

| Id | Description |
|---|---|
| `properties-navigation` | Go to the Java source of property keys and values in `application.properties` files |
| `yaml-navigation` | Go to the Java source of property keys and values in `application.yml` / `application.yaml` files |

### Hover

| Id | Description |
|---|---|
| `properties-hover` | Hovers on property keys in `application.properties` files |
| `yaml-hover` | Hovers on property keys in `application.yml` / `application.yaml` files |

### Semantic tokens (syntax highlighting)

| Id | Description |
|---|---|
| `cron-semantic-tokens` | Highlighting of cron expressions (the same flag as for Java files) |

### Inlay hints

Cron expression hints have no flag. They are switched with the existing setting `boot-java.cron.inlay-hints` (the same setting as for Java files).

### Problems and quick fixes, conversion commands (no feature flags)

- The problems reported in property files (unknown properties, type mismatches, deprecated properties, syntax errors, ...) and their quick fixes have no flags. Each problem type has a severity setting (`spring-boot.ls.problem.application-properties.*` and `spring-boot.ls.problem.application-yaml.*`); set it to `IGNORE` to switch a problem off. The quick fixes belong to their problems and go away with them.
- The commands to convert between `.properties` and `.yml` files are explicit actions that the client offers in its menus. They are not passive features and have no flag.

## Named query properties files

| Id | Description |
|---|---|
| `data-query-semantic-tokens` | Highlighting of queries (the same flag as for Java files) |

## `pom.xml` files

| Id | Description |
|---|---|
| `pom-inlay-hints` | Spring Boot version and starter hints |

Ids are part of the settings contract and stay stable once released. The authoritative list is the `FeatureFlag` enum in `org.springframework.ide.vscode.boot.app`.

## Adding a feature flag

- Add an entry with a short Javadoc to the `FeatureFlag` enum, and add the id to the description of `boot-java.feature-flags` in the `package.json` of the VS Code extension and to this file. `FeatureFlagDocumentationTest` fails if one of them is missing.
- Check the flag on every request, never only at startup:
  - Wrap the provider with `FeatureFlagGuards.onlyWhen(config, flag, provider)` where it is registered. This is the default.
  - Check the flag inside the class when one class serves two features (for example `ValueCompletionProcessor`), or when the bean is injected by its concrete type elsewhere, so it can't be wrapped (for example `QueryMethodCodeActionProvider`).
  - Semantic token, inlay hint and highlight providers are discovered as beans and are wrapped where their handlers are built, in `BootJavaLanguageServerComponents`. The data query hints and highlights use the concrete `JdtDataQuerySemanticTokensProvider` bean, which therefore must not be wrapped itself.
- Tests: `harness.changeConfiguration(...)` replaces all settings and re-indexes the project. Tests that inject beans via `springIndex.updateBeans(...)` need to wait for `indexer.waitOperation()` afterwards and apply the beans again.
