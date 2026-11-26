## Explanations
This error appears on an opening `[` in the key of a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) that is never closed with a `]`. Bracket notation is used for collection indexes (`my.servers[0]`) and for map keys that contain characters such as `.` or `/` (`logging.level.[org.hibernate.SQL]`); an unterminated bracket leaves the key unparseable, and Spring Boot cannot bind it to anything.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the longest valid prefix of the key resolves to a `Map`, `List`, `Set` or array property (or nested bean property), the next character is `[`, and no `]` occurs anywhere in the rest of the key;
- the message is `No matching ']'`, highlighted on the `[` character.

The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_NO_MATCHING_RBRACK`. There is no automated quick fix. Related codes: `PROP_EXPECTED_DOT_OR_LBRACK` (text after a closed bracket), `PROP_NON_INTEGER_IN_BRACKETS` (bracket content is not a number for a collection), `PROP_INVALID_INDEXED_NAVIGATION` (brackets used on a non-collection type).

For more details, see:
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Relaxed binding — maps](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.maps)

## Fixes
**Fix 1: Close the bracket**
Add the missing `]` right after the index or map key.

*Before:*
```properties
security.user.role[1=ADMIN
```

*After:*
```properties
security.user.role[1]=ADMIN
```

**Fix 2: Close an escaped map key**
Map keys with dots need the whole key inside the brackets.

*Before:*
```properties
logging.level.[org.hibernate.SQL=DEBUG
```

*After:*
```properties
logging.level.[org.hibernate.SQL]=DEBUG
```

*Note: the properties parser reports the whole line as a key/value pair as long as it contains a separator, so the missing bracket is only detected by the property-navigation check, not as `PROP_SYNTAX_ERROR`.*
