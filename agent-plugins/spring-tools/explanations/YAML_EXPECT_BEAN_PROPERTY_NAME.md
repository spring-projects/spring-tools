## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when, inside a mapping that describes a Java bean — a `@ConfigurationProperties` class or a nested type such as an OAuth2 client registration — one of the keys is not a plain scalar. Bean properties are addressed by name (`client-id`, `clientId`), so a sequence or mapping used as a key with YAML's `? ` syntax cannot be matched to any property of the bean and the entry is ignored by Spring Boot's binder.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path has reached a property whose type is bean-like (not atomic, not a map, not a collection) and the language server can resolve the bean's properties from the project classpath;
- an entry of the mapping under that property has a key node that is a sequence or a mapping; the message is `Expecting a bean-property name for object of type '<Type>' but got a 'Sequence' node` (or `… a 'Mapping' node`), highlighting the key.

A scalar key that does not name any property of the bean is reported as `YAML_INVALID_BEAN_PROPERTY` instead; a non-scalar key outside a bean-typed mapping is `YAML_EXPECT_SCALAR`. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_EXPECT_BEAN_PROPERTY_NAME`. There is no automated quick fix.

For more details, see:
- [Spring Boot: Externalized Configuration — type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Boot: Externalized Configuration — relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Externalized Configuration — mapping YAML to properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml.mapping-to-properties)

## Fixes
**Fix 1: Name each bean property individually**
Split a sequence key into one `property: value` line per property.

*Before:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            ? [client-id, client-name]
            : my-google-client
```

*After:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: my-google-client
            client-name: my-google-client
```

**Fix 2: Remove a stray explicit-key indicator**
A `? ` in front of an indented block turns that whole block into a mapping key; deleting it restores ordinary bean properties.

*Before:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            ? client-id: my-google-client
              client-secret: ${GOOGLE_SECRET}
```

*After:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: my-google-client
            client-secret: ${GOOGLE_SECRET}
```

*Note: the type in the message is the bean class resolved from the classpath; if it is not the type you expect, the surrounding keys may be wrong rather than the key that is highlighted.*
