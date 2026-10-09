## Explanations
This error appears on the remainder of a key in a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) when a `.` is used to navigate "into" a property whose type has no nested properties. Dotted keys are how Boot addresses nested `@ConfigurationProperties` beans (`server.servlet.session.timeout` walks `server` → `servlet` → `session` → `timeout`) and map entries (`logging.level.<logger>`). That only makes sense when the type on the left of the `.` is a bean or a `Map`; for an atomic type — primitives and their wrappers, `String`, `Class`, `InetAddress`, `Duration`, enums — or for `java.lang.Object` there is nothing to navigate into.

A key like `server.port.number=8080` therefore binds nothing: Spring Boot looks for a property `server.port` of a nested type, finds a `Integer`, and ignores the whole key. The language server reports this instead of letting the setting disappear silently.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the longest valid prefix of the key resolves to a property (or nested bean property) whose type is not "dotable" (neither a bean-like type nor a `Map`), and the next character after that prefix is a `.`;
- the message is `Can't use '.' navigation for property '<prefix>' of type <type>`, highlighted from the offending `.` to the end of the key.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_INVALID_BEAN_NAVIGATION`. There is no automated quick fix. Related codes: `PROP_INVALID_INDEXED_NAVIGATION` (the same mistake with `[..]`), `PROP_INVALID_BEAN_PROPERTY` (navigation is allowed but the nested property does not exist); in YAML the equivalent situation is reported as `YAML_EXPECT_TYPE_FOUND_MAPPING`.

For more details, see:
- [Spring Boot: Type-safe configuration properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)

## Fixes
**Fix 1: Assign the value to the property itself**
Drop the extra segment; the prefix named in the message is the property you want.

*Before:*
```properties
server.port.number=8080
```

*After:*
```properties
server.port=8080
```

**Fix 2: Use the property that actually has the nested structure**
Sometimes the intended property is a sibling with a bean or map type; check completion proposals for the correct path.

*Before:*
```properties
spring.jpa.show-sql.format=true
```

*After:*
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

*Note: for your own `@ConfigurationProperties` class, the nested segment is only valid when the field type is a class with getters/setters (or a `Map`); a `String` or enum field cannot be extended with a suffix.*
