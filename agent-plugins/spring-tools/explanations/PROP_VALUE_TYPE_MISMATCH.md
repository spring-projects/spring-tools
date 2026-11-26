## Explanations
This error appears on the value (or on a typed map key) of a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) when the text cannot be converted to the type declared for that property in the configuration metadata. Spring Boot's `Binder` performs this conversion at startup; a value that does not convert makes the application fail with a `BindException`, so the language server checks it while editing.

The reconciler knows the following types and applies a check close to Boot's converters: `Byte`, `Short`, `Integer`, `Long` (including `0x` hexadecimal, `0b` binary and a leading `0` for octal), `Double`, `Float`, `Boolean` (only `true` or `false`, case-insensitive), `java.time.Duration` (ISO-8601 such as `PT30S` or Boot's simple form `30s`, `500ms`, `2h`, `1d`), enum constants (relaxed: case-insensitive, non-alphanumeric characters ignored), and `List`, `Set` and arrays of those element types written as comma-separated values. For a `Map` property whose key type is declared, the text inside `[...]` and the segment after the map prefix are checked against the key type (`Expecting 'int' for '[...]' notation '<prefix>'`, `Expecting Integer`).

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the key resolves to a property (or nested bean property) with a known type that has a value parser;
- the value does not contain a placeholder `${...}` (values with placeholders are never checked) and the parser rejects it — the message is `Expecting a '<type>' but got '<value>'`, or the parser's own explanation for durations and enums;
- alternatively a scalar value is assigned directly to a `Map` typed property (`spring.datasource.hikari.data-source-properties=foo`), which cannot bind.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_VALUE_TYPE_MISMATCH`. There is no automated quick fix. The YAML counterpart is `YAML_VALUE_TYPE_MISMATCH`.

For more details, see:
- [Spring Boot: Type-safe configuration properties — conversion](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.conversion)
- [Spring Boot: Converting durations](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.conversion.durations)
- [Spring Boot: Relaxed binding — maps](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.maps)
- [Spring Boot: Externalized Configuration — placeholders in properties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.property-placeholders)

## Fixes
**Fix 1: Use a literal of the expected type**
Numbers must not contain units or thousands separators, booleans must be `true`/`false`.

*Before:*
```properties
server.port=eighty-eighty
spring.jpa.show-sql=yes
```

*After:*
```properties
server.port=8080
spring.jpa.show-sql=true
```

**Fix 2: Write durations in a supported format**
Use ISO-8601 or the simple `<number><unit>` format with `ns`, `us`, `ms`, `s`, `m`, `h` or `d`.

*Before:*
```properties
spring.lifecycle.timeout-per-shutdown-phase=30 seconds
```

*After:*
```properties
spring.lifecycle.timeout-per-shutdown-phase=30s
```

**Fix 3: Use a valid enum constant**
Check the completion proposals for the constants the property accepts.

*Before:*
```properties
spring.jpa.hibernate.ddl-auto=recreate
logging.level.root=VERBOSE
```

*After:*
```properties
spring.jpa.hibernate.ddl-auto=create-drop
logging.level.root=DEBUG
```

**Fix 4: Bind map entries with keys, not a scalar**
A `Map` property needs one entry per key.

*Before:*
```properties
spring.datasource.hikari.data-source-properties=cachePrepStmts
```

*After:*
```properties
spring.datasource.hikari.data-source-properties.cachePrepStmts=true
```

*Note: values that are resolved from a placeholder such as `server.port=${PORT}` are not checked; the language server cannot know the resolved text.*
