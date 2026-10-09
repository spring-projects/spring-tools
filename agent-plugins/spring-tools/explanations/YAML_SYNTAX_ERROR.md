## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) that is not well-formed YAML. The language server parses the file with SnakeYAML — the same library Spring Boot uses through `YamlPropertySourceLoader` — and reports the parser's `MarkedYAMLException` at the position it points to. Typical causes are inconsistent indentation, a missing space after `:`, tab characters used for indentation, an unclosed quote or flow collection (`[`, `{`), or a stray `-` item under a mapping.

Because Spring Boot loads the file with the same parser, an application whose configuration file has a syntax error fails at startup with a `ScannerException` / `ParserException`; this diagnostic reports it while editing instead.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file;
- SnakeYAML throws a `MarkedYAMLException` while building the document; the message is the parser's `problem` text (for example `mapping values are not allowed here` or `expected <block end>, but found '-'`) and the range is one character at the reported problem mark.

Only the syntax error is reported for the document: property validation (`YAML_UNKNOWN_PROPERTY`, `YAML_VALUE_TYPE_MISMATCH`, …) resumes once the file parses again. Unlike the property checks, the syntax check does not depend on configuration metadata being available. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_SYNTAX_ERROR`. There is no automated quick fix. The counterpart for `.properties` files is `PROP_SYNTAX_ERROR`.

For more details, see:
- [Spring Boot: Externalized Configuration — working with YAML](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)
- [YAML 1.2 specification](https://yaml.org/spec/1.2.2/)

## Fixes
**Fix 1: Put a space after the colon and indent consistently**
`key:value` without a space is a single scalar; nested keys must be indented with spaces (tabs are not allowed).

*Before:*
```yaml
server:
port:8080
  servlet:
     context-path: /app
```

*After:*
```yaml
server:
  port: 8080
  servlet:
    context-path: /app
```

**Fix 2: Close quotes and flow collections**

*Before:*
```yaml
spring:
  profiles:
    active: [dev, local
  application:
    name: "demo
```

*After:*
```yaml
spring:
  profiles:
    active: [dev, local]
  application:
    name: "demo"
```

**Fix 3: Do not mix a sequence with mapping entries at the same level**

*Before:*
```yaml
app:
  servers:
    host: a
    - b
```

*After:*
```yaml
app:
  servers:
    - host: a
    - host: b
```

*Note: multiple documents in one file are separated by a line containing only `---`; Spring Boot reads each as a separate property source, so a syntax error in one document is reported at that document's position.*
