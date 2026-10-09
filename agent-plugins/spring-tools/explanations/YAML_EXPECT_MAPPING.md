## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when a key that is only a prefix of real properties — a group such as `server`, `spring` or `spring.datasource` — is assigned a scalar or a sequence instead of the nested mapping that holds the actual properties. `server: 8080` does not set `server.port`; Spring Boot flattens it to the key `server` with value `8080`, which no property binds to, so the intended setting is silently missing.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- while navigating the key path through the metadata index, the current prefix is not itself a property but has properties below it (`server` → `server.port`, `server.address`, …);
- the node under that key is a scalar or a sequence rather than a mapping; the message is `Expecting a 'Mapping' node but got '<value>'` (or `… but got a 'Sequence' node`).

A scalar assigned to the legacy key `spring.profiles` is deliberately ignored because that key is valid in profile-specific documents of older Spring Boot generations. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_EXPECT_MAPPING`. There is no automated quick fix. The inverse situation — a mapping under a property that needs a single value — is reported as `YAML_EXPECT_TYPE_FOUND_MAPPING`.

For more details, see:
- [Spring Boot: Externalized Configuration — mapping YAML to properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml.mapping-to-properties)
- [Spring Boot: Externalized Configuration — profile-specific files](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.profile-specific)
- [Spring Boot: Common application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)

## Fixes
**Fix 1: Nest the value under the concrete property**
Look up the property you meant to set (hover or content assist lists the children of the prefix) and put the value there.

*Before:*
```yaml
server: 8080
```

*After:*
```yaml
server:
  port: 8080
```

**Fix 2: Expand a dotted prefix that was written as a single key**
When a prefix and its property were collapsed into one line, restore the mapping structure.

*Before:*
```yaml
spring:
  datasource: jdbc:postgresql://localhost/app
```

*After:*
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost/app
```

**Fix 3: Turn a list of settings into a mapping**
Prefixes hold named properties, not positional entries.

*Before:*
```yaml
spring:
  jpa:
    - show-sql
    - open-in-view
```

*After:*
```yaml
spring:
  jpa:
    show-sql: true
    open-in-view: false
```

*Note: the message shows the offending scalar value in quotes; if the value looks right, the mistake is in the key above it — most often a missing property name after a prefix.*
