## Explanations
This error appears on a scalar value in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) that cannot be converted to the type declared for the property in the configuration metadata. `server.port: eighty` or `spring.jpa.show-sql: yes` binds fine as YAML text, but Spring Boot's binder later fails with a `BindException` caused by a `ConversionFailedException`, and the application does not start.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path resolves to a property (or a nested bean property) with a known atomic type — `Integer`/`int`, `Long`, `Short`, `Byte` (decimal or `0x` hexadecimal), `Double`, `Float`, `Boolean` (exactly `true` or `false`), `java.time.Duration`, an enum (matched with relaxed binding, so `read_committed`, `READ-COMMITTED` and `readCommitted` are all accepted), a `Class`, or a collection/array/map whose elements are such a type;
- the scalar value is not a placeholder (`${…}` and `@…@` values are never checked) and the parser for that type rejects it. The message is `Expecting a '<type>' but got '<value>'`; for `Duration` and enum values the parser's own message is used and only the offending part of the value is highlighted.

When the value is a mapping or a sequence instead of a scalar the reconciler reports `YAML_EXPECT_TYPE_FOUND_MAPPING` or `YAML_EXPECT_TYPE_FOUND_SEQUENCE`. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_VALUE_TYPE_MISMATCH`. There is no automated quick fix. The `.properties` counterpart is `PROP_VALUE_TYPE_MISMATCH`.

For more details, see:
- [Spring Boot: Externalized Configuration — properties conversion](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.conversion)
- [Spring Boot: Externalized Configuration — converting durations](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.conversion.durations)
- [Spring Boot: Externalized Configuration — relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Configuration metadata format](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)

## Fixes
**Fix 1: Provide a value of the declared type**
Numbers must be plain integers (or `0x…`); for booleans the reconciler accepts only `true`/`false` (case-insensitive). Spring's `StringToBooleanConverter` also tolerates `yes`/`no`/`on`/`off`/`1`/`0`, but those are flagged here and are best avoided for clarity.

*Before:*
```yaml
server:
  port: eighty
spring:
  jpa:
    show-sql: yes
```

*After:*
```yaml
server:
  port: 80
spring:
  jpa:
    show-sql: true
```

**Fix 2: Use a valid duration format**
Durations accept ISO-8601 (`PT30S`) or a number with a unit suffix (`ns`, `us`, `ms`, `s`, `m`, `h`, `d`); a bare number means milliseconds unless the property declares `@DurationUnit`.

*Before:*
```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30 seconds
```

*After:*
```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**Fix 3: Pick a valid enum constant**
Check the metadata hover or the enum's source for the allowed constants; case and separators are relaxed, but the name must exist.

*Before:*
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: recreate
```

*After:*
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
```

*Note: if the value is a placeholder, quote it or leave it as is — placeholders are skipped by this check and resolved at startup. Values quoted in YAML (`port: "8080"`) are still validated as the declared type.*
