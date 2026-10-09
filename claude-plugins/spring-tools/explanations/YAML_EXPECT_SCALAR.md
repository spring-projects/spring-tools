## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when a key node is not a plain scalar. YAML allows complex keys — a sequence or mapping introduced with `? ` — but Spring Boot property names are strings, so a non-scalar key cannot be flattened into a property path and the whole entry is meaningless to the binder.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- a mapping entry that is navigated through the metadata index — a top-level key or a key below a property group such as `server:` or `spring.datasource:` — has a key node that is a sequence or a mapping; the message is `Expecting a 'Scalar' node but got a 'Sequence' node` (or `… a 'Mapping' node`), highlighting the key.

Once the reconciler has reached a typed property, non-scalar keys are reported differently: as `YAML_EXPECT_BEAN_PROPERTY_NAME` inside a bean-typed mapping, or as `YAML_EXPECT_TYPE_FOUND_SEQUENCE` / `YAML_EXPECT_TYPE_FOUND_MAPPING` for the key of a map-typed property (the key is checked against the map's key type). The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_EXPECT_SCALAR`. There is no automated quick fix.

For more details, see:
- [Spring Boot: Externalized Configuration — mapping YAML to properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml.mapping-to-properties)
- [Spring Boot: Externalized Configuration — working with YAML](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)
- [YAML 1.2.2 specification — explicit block mapping entries](https://yaml.org/spec/1.2.2/)

## Fixes
**Fix 1: Replace a sequence key with one scalar key per entry**
A sequence key usually means "the same value for several keys"; write the keys out individually.

*Before:*
```yaml
server:
  ? [port, http2.port]
  : 8080
```

*After:*
```yaml
server:
  port: 8080
```

**Fix 2: Flatten a mapping key into the normal key/value structure**
A mapping used as a key is almost always a mis-placed `? ` indicator; remove it so the mapping becomes the value of the surrounding key.

*Before:*
```yaml
spring:
  datasource:
    ? url: jdbc:postgresql://localhost/app
      username: app
```

*After:*
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost/app
    username: app
```

*Note: the `? key : value` syntax with a scalar after `?` is legal and equivalent to `key: value`; only sequence or mapping keys trigger this diagnostic.*
