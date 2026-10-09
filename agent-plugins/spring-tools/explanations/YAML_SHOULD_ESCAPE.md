## Explanations
This warning appears on a key inside a map-typed property in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when the key contains characters other than letters, digits and `-`. Spring Boot's relaxed binding for maps keeps such keys intact only when they are wrapped in `[]`; otherwise "any characters that are not alpha-numeric, `-` or `.` are removed" — `/`, `:`, `_`, spaces and other characters disappear from the bound key. A key such as `logging.level` → `org.springframework` happens to survive because `.` is kept, but keys like `/api/**`, `Content_Type` or `spring:boot` are silently rewritten, and the surrounding map entry then never matches at runtime.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path has reached a property whose type is a `java.util.Map` (for example `logging.level`, `spring.jpa.properties`, `management.endpoints.web.path-mapping`), and a mapping is written under it;
- one of the keys of that mapping does not start with `[` and contains at least one character that is not a letter, a digit or `-`; the message is `This key is used in a map and contains special characters. It is recommended to escape it by surrounding it with '[]'`.

The default severity is `WARNING` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_SHOULD_ESCAPE`. There is no automated quick fix. The check is deliberately stricter than the binder (it also flags `.` so that all map keys are written consistently); a key that is already wrapped in `[]` is never flagged. There is no `.properties` counterpart, because dotted map keys in `.properties` files are unambiguous once written as `logging.level.org.springframework=DEBUG`.

For more details, see:
- [Spring Boot: Externalized Configuration — relaxed binding for maps](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.maps)
- [Spring Boot: Logging — log levels](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.log-levels)
- [Spring Boot: Externalized Configuration — working with YAML](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)

## Fixes
**Fix 1: Wrap the key in `[]` and quote it**
The brackets tell the binder to keep the key verbatim; the quotes are needed because `[` starts a YAML flow sequence.

*Before:*
```yaml
logging:
  level:
    org.springframework.web: DEBUG
    org.hibernate.SQL: TRACE
```

*After:*
```yaml
logging:
  level:
    "[org.springframework.web]": DEBUG
    "[org.hibernate.SQL]": TRACE
```

**Fix 2: Escape keys whose characters would otherwise be stripped**
For keys with `/`, `:` or `_` the escaping is not optional — without it the bound key is different from what is written.

*Before:*
```yaml
management:
  endpoints:
    web:
      path-mapping:
        health: healthcheck
        "prometheus/metrics": metrics
spring:
  jpa:
    properties:
      hibernate.format_sql: true
```

*After:*
```yaml
management:
  endpoints:
    web:
      path-mapping:
        health: healthcheck
        "[prometheus/metrics]": metrics
spring:
  jpa:
    properties:
      "[hibernate.format_sql]": true
```

*Note: the equivalent `.properties` form is `logging.level.[org.springframework.web]=DEBUG` or simply `logging.level.org.springframework.web=DEBUG`; the reconciler for `.properties` files does not report this warning.*
