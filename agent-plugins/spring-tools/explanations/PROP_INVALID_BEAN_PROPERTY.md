## Explanations
This error appears on a segment of a key in a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) that navigates with `.` into a bean-typed property but names a nested property the bean does not have. Configuration metadata describes the top-level keys only; for the nested structure the language server inspects the target type (`server.servlet.session` is a `Session` class, `spring.datasource.hikari` is a `HikariDataSource`, …) and lists its bindable properties. A segment that matches none of them — `spring.datasource.hikari.max-pool-size` instead of `maximum-pool-size` — is never bound by Spring Boot and silently keeps the default.

Names are compared in relaxed form: the segment is converted from camelCase to hyphenated form and matched against the bean's property names, which are offered both in their hyphenated and original spelling, so `maximumPoolSize` and `maximum-pool-size` are both accepted.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the longest valid prefix of the key resolves to a property (or nested bean property) whose type is a bean-like class that is on the project classpath, the next step is a `.segment`, and no property of that class matches the segment;
- the message is `Type '<type>' has no property '<segment>'`, highlighted on the segment.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_INVALID_BEAN_PROPERTY`. There is no automated quick fix. Related codes: `PROP_UNKNOWN_PROPERTY` (no prefix of the key is known at all), `PROP_INVALID_BEAN_NAVIGATION` (the type on the left of the `.` has no properties); the YAML counterpart is `YAML_INVALID_BEAN_PROPERTY`.

For more details, see:
- [Spring Boot: Type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)

## Fixes
**Fix 1: Use the property name declared by the bean**
Completion proposals after the `.` list the properties of the type; pick the right one.

*Before:*
```properties
spring.datasource.hikari.max-pool-size=20
```

*After:*
```properties
spring.datasource.hikari.maximum-pool-size=20
```

**Fix 2: Add the missing property to your own configuration class**
When the bean is one of your `@ConfigurationProperties` classes, the key may be right and the class incomplete — add a field with getter and setter (or a record component).

*Before:*
```properties
app.mail.retry-count=3
```

```java
public class MailProperties {
    private String host;
    // getter and setter for host only
}
```

*After:*
```java
public class MailProperties {
    private String host;
    private int retryCount = 3;
    // getters and setters
}
```

*Note: properties of third-party types are discovered through their getters/setters; a type that only exposes a builder or a constructor without matching accessors cannot be navigated and is better configured through a `@Bean` method.*
