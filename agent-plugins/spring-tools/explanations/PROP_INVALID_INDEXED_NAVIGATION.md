## Explanations
This error appears on the remainder of a key in a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) when `[...]` index notation is used after a property whose type is neither a `List`, `Set` or array nor a `Map`. Bracket notation is how Boot addresses collection elements (`my.servers[0]`) and map entries with special characters in the key (`logging.level.[org.hibernate.SQL]`); applied to a scalar such as `server.port[0]=8888` it has no meaning, and Spring Boot ignores the key.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the longest valid prefix of the key resolves to a property (or nested bean property) whose type is not bracketable (not a `Map`, `List`, `Set` or array) and is also not `java.lang.Object`, and the next character after that prefix is `[`;
- the message is `Can't use '[..]' navigation for property '<prefix>' of type <type>`, highlighted from the `[` to the end of the key.

A redundant `.` before the bracket (`my.list.[0]`) is tolerated. Properties typed as `java.lang.Object` are exempt because their real shape is unknown. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-properties.PROP_INVALID_INDEXED_NAVIGATION`. There is no automated quick fix. Related codes: `PROP_INVALID_BEAN_NAVIGATION` (the same mistake with `.`), `PROP_NON_INTEGER_IN_BRACKETS` and `PROP_NO_MATCHING_RBRACK` (bracket notation on a valid collection but malformed); the YAML equivalent is `YAML_EXPECT_TYPE_FOUND_SEQUENCE`.

For more details, see:
- [Spring Boot: Relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding)
- [Spring Boot: Relaxed binding — maps](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.maps)

## Fixes
**Fix 1: Assign a single value to the scalar property**
The property holds one value; remove the index.

*Before:*
```properties
server.port[0]=8888
```

*After:*
```properties
server.port=8888
```

**Fix 2: Model multiple values with a collection-typed property**
If several values are genuinely needed, the target must be a `List`/`Set`/array (or `Map`) property — a scalar Boot property cannot be turned into one from the configuration file. Declare your own `@ConfigurationProperties` holder instead.

*Before:*
```properties
spring.datasource.url[0]=jdbc:postgresql://db1/app
spring.datasource.url[1]=jdbc:postgresql://db2/app
```

*After:*
```properties
app.datasources[0].url=jdbc:postgresql://db1/app
app.datasources[1].url=jdbc:postgresql://db2/app
```

```java
@ConfigurationProperties("app")
public class AppProperties {
    private List<DataSourceProperties> datasources = new ArrayList<>();
    // getter and setter
}
```

*Note: a `java.lang.Object` typed property is never flagged by this check because its real shape is unknown to the language server.*
