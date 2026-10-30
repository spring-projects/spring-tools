## Explanations
This warning appears on a `${...}` placeholder in a scalar value of a Spring Boot YAML file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`) that Spring could not parse. The language server scans the raw text of every scalar value, quoted or not, for `${`, finds the matching `}` (nested placeholders such as `${a:${b}}` count as one), and runs the text between the braces through the same placeholder grammar the Java editor uses for `@Value("${...}")`. The grammar accepts `key` or `key:default`, where the key is one or more dot-separated identifiers without spaces.

Two cases are reported without the grammar: a `${` with no closing `}` (highlighted from the `${` to the end of the value) and an empty `${}`. A placeholder preceded by a backslash (`\${...}`) is treated as escaped and skipped. Only values are checked, never keys.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML file and the project has a non-empty configuration metadata index;
- a scalar value contains an unescaped `${` that is unclosed, empty, or whose body the grammar rejects; the message starts with `Placeholder:` and the highlighted range is the offending token.

At runtime Spring leaves an unclosed or malformed placeholder in the value as plain text, so the mistake only shows up later as a wrong setting. The default severity is `WARNING` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_PLACEHOLDER_SYNTAX`. There is no automated quick fix. The same check exists for properties files as `PROP_PLACEHOLDER_SYNTAX`.

For more details, see:
- [Spring Boot: Externalized Configuration — Property placeholders](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.property-placeholders)
- [Spring Framework: `PropertyPlaceholderHelper`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/PropertyPlaceholderHelper.html)

## Fixes
**Fix 1: Close the placeholder**

*Before:*
```yaml
spring:
  datasource:
    url: ${DB_URL
```

*After:*
```yaml
spring:
  datasource:
    url: ${DB_URL}
```

**Fix 2: Remove spaces from the key**
Keys cannot contain spaces. Use the environment variable or property name exactly.

*Before:*
```yaml
spring:
  datasource:
    username: ${DB USER}
```

*After:*
```yaml
spring:
  datasource:
    username: ${DB_USER}
```

**Fix 3: Escape a literal `${`**
If the text is meant to stay as it is, escape it with a backslash. Use a plain or single-quoted scalar, since `\$` is not a valid escape inside double quotes.

*Before:*
```yaml
app:
  template: ${}
```

*After:*
```yaml
app:
  template: '\${name}'
```

*Note: escaping placeholders requires Spring Boot 3.4 or later.*
