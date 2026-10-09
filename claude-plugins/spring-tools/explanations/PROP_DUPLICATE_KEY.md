## Explanations
This error appears on every occurrence of a key that is assigned more than once in the same Spring Boot `.properties` document (`application*.properties`, `bootstrap*.properties`). Keys are compared after unescaping, so `my\:key` and `my:key` count as the same key.

A `.properties` file is loaded into a map, so when a key appears twice only the last assignment survives — Spring Boot's `OriginTrackedPropertiesLoader` behaves like `java.util.Properties` here. The earlier line is dead configuration that still looks authoritative to whoever reads the file, which typically hides a merge-conflict leftover or a copy-pasted block. Highlighting both lines makes the conflict visible before the wrong value reaches production.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index (the duplicate check runs together with the other property checks);
- the same key is defined twice or more within one document; the message `Duplicate property '<key>'` is reported on each occurrence.

Multi-document files are handled: a line starting with `#---` or `!---` (not directly followed by another comment line with the same prefix) starts a new document and resets the set of seen keys, so redefining `server.port` in a `spring.config.activate.on-profile` section is not a duplicate. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_DUPLICATE_KEY`. There is no automated quick fix. The YAML counterpart is `YAML_DUPLICATE_KEY`.

For more details, see:
- [Spring Boot: Externalized Configuration — multi-document files](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.multi-document)
- [Spring Boot: Externalized Configuration — profile-specific files](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.profile-specific)
- [Spring Boot: Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)

## Fixes
**Fix 1: Keep a single assignment**
Decide which value is intended and delete the other line.

*Before:*
```properties
server.port=8080
spring.application.name=demo
server.port=9090
```

*After:*
```properties
server.port=9090
spring.application.name=demo
```

**Fix 2: Split profile-specific values into separate documents**
If both values are needed under different conditions, move the second into its own document activated by a profile (or into `application-<profile>.properties`).

*Before:*
```properties
server.port=8080
server.port=9090
```

*After:*
```properties
server.port=8080
#---
spring.config.activate.on-profile=local
server.port=9090
```

*Note: the `#---` separator must start at the beginning of the line and the line directly after it must not be another `#` comment, otherwise Spring Boot (and the language server) treats it as an ordinary comment and the keys are still in one document.*
