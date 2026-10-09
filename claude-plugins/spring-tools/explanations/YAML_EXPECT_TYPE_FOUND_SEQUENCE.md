## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when a YAML sequence (`- item` list or `[a, b]` flow list) is given to a property whose configured type is not list-like. Spring Boot maps list entries to indexed keys such as `server.port[0]`; for a single-valued property like `server.port` those keys do not bind, so the property stays at its default and Spring Boot fails with a `BindException` when the type cannot accept the list.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path resolves to a metadata property or a nested bean property whose type is either atomic (number, boolean, string, duration, enum, …) or another non-sequencable type such as a bean or a map — only `java.util.Collection` subtypes, arrays and `java.lang.Object` accept sequences;
- the value node under that key is a YAML sequence; the message is `Expecting a '<type>' but got a 'Sequence' node`, highlighting the whole sequence.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_EXPECT_TYPE_FOUND_SEQUENCE`. There is no automated quick fix. Related codes: `YAML_EXPECT_TYPE_FOUND_MAPPING` when a nested mapping appears where a single value is expected and `YAML_VALUE_TYPE_MISMATCH` when a scalar of the wrong type is given.

For more details, see:
- [Spring Boot: Externalized Configuration — mapping YAML to properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml.mapping-to-properties)
- [Spring Boot: Externalized Configuration — type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Boot: Configuration metadata format](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)

## Fixes
**Fix 1: Give the single-valued property one scalar**
If the property cannot hold multiple values, keep the one you need.

*Before:*
```yaml
server:
  port:
    - 8080
    - 8443
```

*After:*
```yaml
server:
  port: 8080
```

**Fix 2: Describe a bean-typed entry as a mapping**
Entries of a map whose values are beans (for example OAuth2 client registrations) are mappings of bean properties, not a list of single-entry mappings.

*Before:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            - client-id: my-client
            - client-secret: ${GOOGLE_SECRET}
```

*After:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: my-client
            client-secret: ${GOOGLE_SECRET}
```

**Fix 3: Use a mapping for a map-typed property**
Map-typed properties need key/value pairs, not a list of pairs.

*Before:*
```yaml
logging:
  level:
    - root: INFO
    - org.springframework.web: DEBUG
```

*After:*
```yaml
logging:
  level:
    root: INFO
    org.springframework.web: DEBUG
```

*Note: properties typed as `String[]`, `List<…>` or `Set<…>` (such as `spring.profiles.active` or `management.endpoints.web.exposure.include`) legitimately accept sequences and are never flagged; check the type shown on hover before restructuring.*
