## Explanations
This error appears in a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) when, after a complete navigation step in a key, the next character is neither a `.` nor a `[`. A key is parsed as a chain of steps — a property name, then `.segment` for nested bean properties or map keys, then `[index]` for collection elements — and every step must be followed by another step or by the end of the key. Text glued directly to a closing bracket, as in `security.user.role[1]crap=foo`, cannot be interpreted; Spring Boot's binder would not bind such a key either.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the key starts with a known property and the navigator, having consumed a `[...]` step (or a nested step that ends before the end of the key), finds a character other than `.` or `[`;
- the message is `Expecting either a '.' or '['`, highlighted from the unexpected character to the end of the key.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_EXPECTED_DOT_OR_LBRACK`. There is no automated quick fix. Related codes: `PROP_NO_MATCHING_RBRACK` (the `[` is never closed) and `PROP_NON_INTEGER_IN_BRACKETS` (the index is not a number).

For more details, see:
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Relaxed binding — maps](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.maps)

## Fixes
**Fix 1: Separate the nested property with a dot**
When the element of the collection is a bean, its properties are addressed with `.` after the index.

*Before:*
```properties
app.servers[0]host=localhost
app.servers[0]port=8080
```

*After:*
```properties
app.servers[0].host=localhost
app.servers[0].port=8080
```

**Fix 2: Remove leftover characters after the index**
If nothing should follow the index, delete the stray text.

*Before:*
```properties
security.user.role[1]crap=ADMIN
```

*After:*
```properties
security.user.role[1]=ADMIN
```

*Note: a redundant `.` in front of `[` (`app.servers.[0].host`) is accepted by both Spring Boot and the language server.*
