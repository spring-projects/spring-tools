## Explanations
This reminder appears on a `${key}` placeholder without a default value in a Java annotation attribute when the key is not defined anywhere the language server can see. Spring resolves the placeholder against the `Environment` when the bean is created. When nothing provides the key, the bean fails at startup with `Could not resolve placeholder 'key'`.

The check covers the attributes listed in `AnnotationParamPlaceholderExtractor`. Edit that table to add or remove one. It lists `@Value`, the string attributes of `@Scheduled`, `@PropertySource`, `@ImportResource`, `@ComponentScan`, `@RequestMapping`, `@CrossOrigin`, the `defaultValue` of `@RequestParam`, `@RequestHeader`, `@CookieValue` and `@MatrixVariable`, `@TestPropertySource`, and the string attributes of the JMS, Rabbit and Kafka listener annotations. Meta-annotations count, so `@GetMapping` and the other composed mappings are covered through `@RequestMapping`, and each element of an array attribute is checked on its own. Values may be string literals, text blocks or concatenations of constants.

A placeholder inside a `#{...}` expression belongs to the SpEL reconciler and is skipped here. The key is looked up in the configuration metadata on the classpath, with the same prefix matching as `PROP_UNKNOWN_PROPERTY`, and in the project's own `application*` files. The reminder appears only when both miss.

Keys the JVM or Boot provide are skipped (`random.*`, `user.*`, `java.*`, `os.*`, `file.*`, `line.*`, `path.*`, `PID`, `LOG_*`, `CONSOLE_LOG_*`, `FILE_LOG_*`, `LOGGED_APPLICATION_*`). A placeholder with a default is never reported, a nested placeholder in the default is checked on its own, and an escaped placeholder (`\${key}`) is skipped. A syntax error in the placeholder is reported as `PROPERTY_PLACE_HOLDER_SYNTAX` instead.

The language server raises this diagnostic when the project depends on `spring-context`, the Boot 2 category is on, the attribute is in the table, the placeholder has no default, is not escaped, sits outside `#{...}`, its key is not a runtime key, and neither lookup finds it. The message is `Placeholder '<key>' has no default value and could not be found in the project's configuration. Make sure it is defined at runtime.` The highlighted range is the key inside the braces.

Keys from `spring.config.import`, another `@PropertySource`, `@TestPropertySource` properties, a config server or the deployment environment are not in the ad-hoc index and show the reminder. The indexes are read once per Java file. When an `application*` file changes, the Java files with placeholders are checked again, unless the severity is `IGNORE`.

The marker stays as long as the placeholder does, so the default severity is `IGNORE`. The check is off until `spring-boot.ls.problem.boot2.JAVA_UNRESOLVED_PLACEHOLDER` is set to `WARNING`, `INFO`, `HINT` or `ERROR`. There is no quick fix. The same check exists for configuration files as `PROP_UNRESOLVED_PLACEHOLDER` and `YAML_UNRESOLVED_PLACEHOLDER`.

For more details, see:
- [Spring Framework: Using `@Value`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)
- [Spring Boot: Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [spring-projects/spring-boot#18816 — unresolved placeholders bind the literal string](https://github.com/spring-projects/spring-boot/issues/18816)

## Fixes
**Fix 1: Add a default value**
When the key is optional, give it a default so the bean is created without it.

*Before:*
```java
@Value("${app.greeting}")
String greeting;
```

*After:*
```java
@Value("${app.greeting:Hello}")
String greeting;
```

**Fix 2: Define the key in the project**
When the value is the same for every environment, set it in a configuration file instead of expecting it from outside.

*Before:*
```java
@Scheduled(cron = "${app.cleanup.cron}")
void cleanup() {}
```

*After:*
```properties
# application.properties
app.cleanup.cron=0 0 3 * * *
```
```java
@Scheduled(cron = "${app.cleanup.cron}")
void cleanup() {}
```

**Fix 3: Keep the placeholder and turn the reminder off**
When the key is meant to come from outside, for example a database URL injected by the deployment, the value is correct as written. Set the severity back to `IGNORE` if the reminder is not wanted.

*Before:*
```json
// VS Code settings.json
{
  "spring-boot.ls.problem.boot2.JAVA_UNRESOLVED_PLACEHOLDER": "WARNING"
}
```

*After:*
```json
// VS Code settings.json
{
  "spring-boot.ls.problem.boot2.JAVA_UNRESOLVED_PLACEHOLDER": "IGNORE"
}
```

*Note: a key in a `.env` file or a Docker Compose `environment:` block is still unknown to the language server, which only reads the project's `application*` files.*
