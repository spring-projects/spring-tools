## Explanations
This warning appears on a `${key}` placeholder without a default value in a Spring Boot YAML file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`) when the key is not defined anywhere the language server can see. Spring Boot resolves placeholders against the `Environment`. When nothing provides the key, the binder behind every `@ConfigurationProperties` class keeps the literal `${key}` and the application starts with a broken setting. Only `@Value` injection and `Environment.getProperty` fail with `Could not resolve placeholder`.

The language server looks the key up in three places and warns only when all three miss:
- the keys defined in the same file, flattened to dotted form (`spring: datasource: url:` is `spring.datasource.url`), across all documents separated by `---`;
- the configuration metadata on the classpath (`spring-configuration-metadata.json`), using the same prefix matching as `YAML_UNKNOWN_PROPERTY`, so any Boot property such as `${server.port}` counts as defined even if the file does not set it;
- the project's own `application*.properties`, `application*.yml` and `application*.yaml` files in the source folders (the ad-hoc index).

Keys the JVM or Boot itself provides are skipped: `random.*`, `user.*`, `java.*`, `os.*`, `file.*`, `line.*`, `path.*`, `PID` and the logging system properties (`LOG_*`, `CONSOLE_LOG_*`, `FILE_LOG_*`, `LOGGED_APPLICATION_*`). A placeholder with a default (`${key:value}`) is never reported, and a nested placeholder in the default (`${a:${B}}`) is checked on its own. An escaped placeholder (`\${key}`) is skipped. Values are checked whether plain, single-quoted or double-quoted.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML file and the project has a non-empty configuration metadata index;
- the placeholder has no default value, is not escaped and its key is not one of the runtime keys above;
- none of the three lookups finds the key; the message is `Placeholder '<key>' has no default value and could not be found in the project's configuration. Make sure it is defined at runtime.` and the highlighted range is the key inside the braces.

Keys that come from `spring.config.import`, `@PropertySource` or a config server are not in the ad-hoc index and will warn. Keys that only exist in another profile's file are found, because the ad-hoc index reads every `application*` file.

This is a reminder rather than a defect report: for a key that is meant to come from outside, the marker stays as long as the placeholder does. The default severity is therefore `IGNORE`, and the check is off until `spring-boot.ls.problem.application-yaml.YAML_UNRESOLVED_PLACEHOLDER` is set to `WARNING`, `INFO`, `HINT` or `ERROR`. There is no automated quick fix. The same check exists for properties files as `PROP_UNRESOLVED_PLACEHOLDER`.

For more details, see:
- [Spring Boot: Externalized Configuration — Property placeholders](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.property-placeholders)
- [Spring Boot: Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [spring-projects/spring-boot#18816 — unresolved placeholders bind the literal string](https://github.com/spring-projects/spring-boot/issues/18816)

## Fixes
**Fix 1: Add a default value**
When the key is optional, give it a default so the application starts without it.

*Before:*
```yaml
server:
  port: ${PORT}
```

*After:*
```yaml
server:
  port: ${PORT:8080}
```

**Fix 2: Define the key in the project**
When the value is the same for every environment, set it in a configuration file instead of expecting it from outside.

*Before:*
```yaml
app:
  greeting: ${GREETING}
```

*After:*
```yaml
greeting:
  text: Hello
app:
  greeting: ${greeting.text}
```

**Fix 3: Keep the placeholder and turn the reminder off**
When the key is meant to come from outside, for example a database URL injected by the deployment, the value is correct as written. Set the severity back to `IGNORE` if the reminder is not wanted.

*Before:*
```json
// VS Code settings.json
{
  "spring-boot.ls.problem.application-yaml.YAML_UNRESOLVED_PLACEHOLDER": "WARNING"
}
```

*After:*
```json
// VS Code settings.json
{
  "spring-boot.ls.problem.application-yaml.YAML_UNRESOLVED_PLACEHOLDER": "IGNORE"
}
```

*Note: a key in a `.env` file or a Docker Compose `environment:` block is still unknown to the language server, which only reads the project's `application*` files.*
