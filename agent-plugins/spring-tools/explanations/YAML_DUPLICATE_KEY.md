## Explanations
This error appears on every occurrence of a key that is defined more than once within the same mapping of a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`). In YAML a mapping is a set of unique keys; a second `port:` under the same `server:` node is not a legal way to "override" the first one.

Spring Boot rejects such files: `YamlProcessor.createYaml()` configures SnakeYAML with `loaderOptions.setAllowDuplicateKeys(false)`, so the application fails at startup with a `DuplicateKeyException` ("found duplicate key …"). Duplicates usually come from merge conflicts or from pasting a block into a mapping that already has that key; reporting both occurrences shows exactly which lines collide.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index (the duplicate check runs together with the property checks);
- inside one mapping node two or more entries have the same scalar key (keys are compared textually after parsing, so `port` and `"port"` collide); the message `Duplicate key '<key>'` is reported on each of the colliding keys.

Keys in different documents of a multi-document file (separated by `---`) or at different nesting levels are not duplicates. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_DUPLICATE_KEY`. There is no automated quick fix. The `.properties` counterpart is `PROP_DUPLICATE_KEY`.

For more details, see:
- [Spring Boot: Externalized Configuration — working with YAML](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)
- [Spring Boot: Externalized Configuration — multi-document files](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.multi-document)
- [Spring Framework `YamlProcessor` (disables duplicate keys)](https://github.com/spring-projects/spring-framework/blob/main/spring-beans/src/main/java/org/springframework/beans/factory/config/YamlProcessor.java)

## Fixes
**Fix 1: Keep a single definition**
Decide which value is intended and delete the other entry.

*Before:*
```yaml
server:
  port: 8080
  servlet:
    context-path: /app
  port: 9090
```

*After:*
```yaml
server:
  port: 9090
  servlet:
    context-path: /app
```

**Fix 2: Merge two blocks for the same parent key**
Often the duplicate is a parent key (`spring:`) repeated with different children; merge the children under one parent.

*Before:*
```yaml
spring:
  application:
    name: demo
spring:
  datasource:
    url: jdbc:postgresql://localhost/app
```

*After:*
```yaml
spring:
  application:
    name: demo
  datasource:
    url: jdbc:postgresql://localhost/app
```

**Fix 3: Split profile-specific values into separate documents**
If both values are needed under different conditions, put the second into its own document activated by a profile.

*Before:*
```yaml
server:
  port: 8080
  port: 9090
```

*After:*
```yaml
server:
  port: 8080
---
spring:
  config:
    activate:
      on-profile: local
server:
  port: 9090
```

*Note: YAML merge keys (`<<: *defaults`) are flattened before the duplicate check, so an explicit key that overrides a merged one is not reported; a `<<` whose value is not a mapping is reported separately as `YamlSchemaProblem`.*
