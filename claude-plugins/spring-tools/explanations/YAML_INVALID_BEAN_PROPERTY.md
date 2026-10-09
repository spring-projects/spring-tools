## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when a key inside a bean-typed mapping does not correspond to any property of that bean. It is the nested-object variant of `YAML_UNKNOWN_PROPERTY`: the outer key (for example `spring.security.oauth2.client.registration.google`) is known from the configuration metadata, but the language server resolves the bean class from the project classpath and finds no setter, record component or constructor-bound field matching the inner key — typically a typo (`client-secrt`) or a property that belongs to a different type. Spring Boot's binder ignores unknown keys by default unless `ignoreUnknownFields = false` is set on the `@ConfigurationProperties` class, so the mistake is otherwise silent.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path has reached a property whose type is bean-like (not atomic, not a map, not a collection) and the bean's properties can be resolved from the classpath;
- a scalar key in the mapping under that property matches none of the bean's properties in any of the relaxed spellings (`client-id`, `clientId`, `client_id`); the message is `Unknown property '<key>' for type '<Type>'`.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_INVALID_BEAN_PROPERTY`. There is no automated quick fix. The `.properties` counterpart is `PROP_INVALID_BEAN_PROPERTY`; a non-scalar key at the same position is reported as `YAML_EXPECT_BEAN_PROPERTY_NAME`.

For more details, see:
- [Spring Boot: Externalized Configuration — type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Boot: Externalized Configuration — relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Configuration metadata annotation processor](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)

## Fixes
**Fix 1: Correct the property name**
Use content assist inside the bean mapping to see the available properties and fix the spelling.

*Before:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: my-google-client
            client-secrt: ${GOOGLE_SECRET}
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

**Fix 2: Move the key to the type that owns it**
Some settings live on a sibling bean; for OAuth2 clients the `issuer-uri` belongs to the `provider`, not to the `registration`.

*Before:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: demo
            issuer-uri: ${KEYCLOAK_ISSUER_URI}
```

*After:*
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: demo
            provider: keycloak
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_ISSUER_URI}
```

**Fix 3: Add the property to your own nested type**
For project-owned types (typically the value type of a `Map` or `List` property, which the annotation processor does not expand into individual metadata entries), declare the missing member in Java and rebuild.

*Before:*
```java
@ConfigurationProperties("app.mail")
public record MailProperties(Map<String, Sender> senders) {
    public record Sender(String address) {}
}
```
```yaml
app:
  mail:
    senders:
      support:
        address: support@example.com
        display-name: Support Team
```

*After:*
```java
@ConfigurationProperties("app.mail")
public record MailProperties(Map<String, Sender> senders) {
    public record Sender(String address, String displayName) {}
}
```

*Note: the type in the message is resolved from the compiled classes on the project classpath; after adding a property, rebuild the project so the language server sees the new member.*
