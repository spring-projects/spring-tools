## Explanations
This error appears on the text between `[` and `]` in the key of a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) when the property on the left of the bracket is a `List`, `Set` or array and the bracket content is not an integer. Collection elements are addressed by position (`my.servers[0]`, `my.servers[1]`); a key like `security.user.role[bork]=foo` names no position, so Spring Boot cannot bind it — the binder expects numeric indexes for indexed properties, and non-numeric ones are treated as map keys, which do not exist for a list.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the longest valid prefix of the key resolves to a `List`, `Set` or array property (or nested bean property), and a `[...]` step follows whose content does not parse as an `int` (the content may not contain a placeholder `${...}`; such indexes are skipped);
- the message is `Expecting 'int' for '[...]' notation '<prefix>'`, highlighted on the bracket content.

For `Map` properties the same bracket content is checked against the declared key type and, when it fails, reported as `PROP_VALUE_TYPE_MISMATCH` instead. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_NON_INTEGER_IN_BRACKETS`. There is no automated quick fix. Related codes: `PROP_NO_MATCHING_RBRACK`, `PROP_EXPECTED_DOT_OR_LBRACK`, `PROP_INVALID_INDEXED_NAVIGATION`.

For more details, see:
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Relaxed binding — maps](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.maps)

## Fixes
**Fix 1: Use a numeric, zero-based index**
Collection positions start at `0` and should be contiguous.

*Before:*
```properties
security.user.role[first]=USER
security.user.role[second]=ADMIN
```

*After:*
```properties
security.user.role[0]=USER
security.user.role[1]=ADMIN
```

**Fix 2: Use a comma-separated list for simple element types**
For `String`, numbers and enums a single comma-separated value is equivalent and easier to read.

*Before:*
```properties
security.user.role[admin]=ADMIN
```

*After:*
```properties
security.user.role=USER,ADMIN
```

**Fix 3: Switch to a `Map` when you need named entries**
If the entries really are keyed by name, the target property must be a `Map`; for your own configuration class change the field type accordingly.

*Before:*
```properties
app.endpoints[health]=/actuator/health
```

*After:*
```java
@ConfigurationProperties("app")
public class AppProperties {
    private Map<String, String> endpoints = new LinkedHashMap<>();
    // getter and setter
}
```

*Note: an index that comes from a placeholder such as `my.list[${idx}]` is not checked.*
