## Explanations
This warning appears on a key in a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) that does not correspond to any property known from the configuration metadata of the project: neither the whole key nor any dotted prefix of it (`spring.datasource.url` → `spring.datasource` → `spring`) is found in the `spring-configuration-metadata.json` / `additional-spring-configuration-metadata.json` files on the classpath, including the metadata generated for the project's own `@ConfigurationProperties` classes.

Spring Boot does not validate property names at startup. A key that is misspelled (`server.prot`), that belongs to a starter that is not on the classpath, or that has been renamed in a newer Boot generation is simply ignored, and the application keeps running with default values. This diagnostic surfaces that silent failure while editing. The matching is relaxed: camelCase segments are compared against their hyphenated form (`server.servletPath` matches `server.servlet-path`), and only the part of the key before the first `[` is looked up, so indexed and map-style keys are checked on their base name.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index (in a project with no metadata at all the check is skipped entirely);
- no prefix of the key (compared after camelCase → hyphen normalisation) matches a metadata entry;
- the message is `'<key>' is an unknown property.`; when a similarly spelled property exists it is extended with ` Did you mean '<candidate>'?`. The highlighted range starts after the longest prefix that is still valid, so for `server.prot` only `prot` is underlined.

The default severity is `WARNING` and can be changed with `spring-boot.ls.problem.application-properties.PROP_UNKNOWN_PROPERTY`. A quick fix "Create metadata for `<key>`" is offered: it adds an entry `{ "name": "<key>", "type": "java.lang.String", "description": "A description for '<key>'" }` to `META-INF/additional-spring-configuration-metadata.json` in the project's resources folder (creating the file if necessary). The same check exists for YAML files as `YAML_UNKNOWN_PROPERTY`.

For more details, see:
- [Spring Boot: Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Configuration metadata annotation processor — adding additional metadata](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html#configuration-metadata.annotation-processor.adding-additional-metadata)
- [Spring Boot: Properties and configuration how-to (`spring-boot-properties-migrator`)](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)

## Fixes
**Fix 1: Correct the spelling**
Use the suggestion from the message or the completion proposals to pick the real property name.

*Before:*
```properties
server.prot=8080
spring.datasource.usrname=demo
```

*After:*
```properties
server.port=8080
spring.datasource.username=demo
```

**Fix 2: Add the starter that owns the property**
Properties are only known when the library that defines them is on the classpath. Add the missing dependency instead of keeping an inert key.

*Before:*
```properties
# spring-boot-starter-data-redis is not a dependency of this project
spring.data.redis.host=cache.internal
```

*After:*
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Fix 3: Describe your own property in additional metadata**
For custom keys read via `@Value("${...}")` or `Environment.getProperty(...)`, use the quick fix (or edit the file by hand) so tools can recognise them; the annotation processor merges this file into the generated metadata.

*Before:*
```properties
app.greeting=Hello
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
