## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when a key whose configured type is atomic — a number, boolean, string, duration, enum or similar single value — is followed by a nested mapping instead of a scalar. A typical cause is one indentation level too many: `server.port` receives the child mapping `{extracrap: 8080}` rather than the value `8080`. Spring Boot flattens YAML to properties, so this produces keys such as `server.port.extracrap` that either bind to nothing or fail conversion, and `server.port` itself stays unset.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path resolves to a metadata property or a nested bean property whose type is atomic (not a bean, map, collection or `Object`);
- the value node under that key is a YAML mapping; the message is `Expecting a '<type>' but got a 'Mapping' node`, highlighting the whole mapping.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_EXPECT_TYPE_FOUND_MAPPING`. There is no automated quick fix. Related codes: `YAML_EXPECT_TYPE_FOUND_SEQUENCE` when a list appears instead of a single value, `YAML_EXPECT_MAPPING` for the opposite situation (a scalar where nested keys are required), and `YAML_VALUE_TYPE_MISMATCH` when a scalar of the wrong type is given.

For more details, see:
- [Spring Boot: Externalized Configuration — mapping YAML to properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml.mapping-to-properties)
- [Spring Boot: Externalized Configuration — type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Boot: Configuration metadata format](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)

## Fixes
**Fix 1: Assign the scalar directly to the key**
Remove the accidental nesting so the atomic property gets its value.

*Before:*
```yaml
server:
  port:
    extracrap: 8080
```

*After:*
```yaml
server:
  port: 8080
```

**Fix 2: Move mis-indented siblings up one level**
When keys were indented under a scalar property by mistake, dedent them so they become siblings of that property and give the property its own value.

*Before:*
```yaml
management:
  endpoints:
    web:
      base-path:
        exposure:
          include: health
```

*After:*
```yaml
management:
  endpoints:
    web:
      base-path: /manage
      exposure:
        include: health
```

*Note: if the property really is a bean or a map of settings, its metadata type is not atomic and this diagnostic does not apply; check the type shown on hover — a mapping under a `java.util.Map` or `@ConfigurationProperties` bean is valid.*
