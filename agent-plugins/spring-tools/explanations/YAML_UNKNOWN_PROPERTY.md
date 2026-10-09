## Explanations
This warning appears on a key in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) whose dotted path — built from the nesting of the mapping keys — is neither a property in the project's configuration metadata nor a prefix of one. The metadata comes from the `spring-configuration-metadata.json` / `additional-spring-configuration-metadata.json` files on the classpath, including the metadata generated for the project's own `@ConfigurationProperties` classes.

Spring Boot does not validate property names at startup; a misspelled key (`server.prot`), a key of a starter that is not on the classpath, or a key renamed in a newer Boot generation is silently ignored and the application keeps its defaults. This diagnostic surfaces that while editing. Matching is relaxed: each key is tried as written, in camelCase → hyphen form and in snake_case → hyphen form, so `contextPath`, `context_path` and `context-path` are all recognised.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index (with no metadata at all the reconciler is not created and nothing is checked);
- for the current key path there is neither an exact metadata match nor any metadata property that extends the path; the message is `Unknown property '<path>'` and it is reported on the first key at which the path becomes unknown (children are not reported separately);
- the entry is not defined through a YAML anchor/alias (`&anchor` / `*alias` entries are skipped).

The default severity is `WARNING` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_UNKNOWN_PROPERTY`. A quick fix "Create metadata for `<path>`" is offered for each leaf below the unknown key: it adds `{ "name": "<path>", "type": "java.lang.String", "description": "A description for '<path>'" }` to `META-INF/additional-spring-configuration-metadata.json` (creating the file if necessary). The counterpart for `.properties` files is `PROP_UNKNOWN_PROPERTY`; an unknown key *inside* a bean-typed value is reported as `YAML_INVALID_BEAN_PROPERTY` instead.

For more details, see:
- [Spring Boot: Externalized Configuration — working with YAML](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Configuration metadata annotation processor — adding additional metadata](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html#configuration-metadata.annotation-processor.adding-additional-metadata)
- [Spring Boot: Properties and configuration how-to (`spring-boot-properties-migrator`)](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)

## Fixes
**Fix 1: Correct the spelling**
Use completion proposals to pick the real property name.

*Before:*
```yaml
server:
  prot: 8080
spring:
  datasource:
    usrname: demo
```

*After:*
```yaml
server:
  port: 8080
spring:
  datasource:
    username: demo
```

**Fix 2: Add the starter that owns the property**
Properties are only known when the library defining them is on the classpath.

*Before:*
```yaml
# spring-boot-starter-data-redis is not a dependency of this project
spring:
  data:
    redis:
      host: cache.internal
```

*After:*
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Fix 3: Describe your own property in additional metadata**
For custom keys read via `@Value("${...}")` or `Environment`, apply the quick fix (or edit the file) so tools recognise them; the annotation processor merges this file into the generated metadata.

*Before:*
```yaml
app:
  greeting: Hello
```

*After:*
```json
// src/main/resources/META-INF/additional-spring-configuration-metadata.json
{
  "properties": [
    {
      "name": "app.greeting",
      "type": "java.lang.String",
      "description": "Greeting shown on the landing page."
    }
  ]
}
```

*Note: binding the key to a `@ConfigurationProperties` class together with the `spring-boot-configuration-processor` dependency generates the metadata automatically and is the preferred long-term solution.*
