## Explanations
This error is raised on a `${...}` property placeholder that appears *inside* a Spring Expression Language (SpEL) expression in a Java annotation attribute, when the placeholder itself is malformed. The typical location is a `@Value` template such as `@Value("#{${app.timeout:30} * 1000}")`, but it applies to every annotation attribute the language server treats as SpEL (see `JAVA_SPEL_EXPRESSION_SYNTAX` for the list). While the SpEL expression is parsed, each `${...}` token is extracted, the `${` and `}` delimiters are stripped, and the remaining `key` or `key:defaultValue` text is checked against a small placeholder grammar. The diagnostic message starts with `Place-Holder:` followed by the parser's description, and it is reported at the position inside the placeholder where the parse failed (for `@Value("#{${property.}}")` this is right after the trailing dot).

A placeholder key is a dot-separated sequence of identifiers, so `${app.name}`, `${server.port}` and `${a.b.c}` are valid, and an optional default value can follow after a colon (`${app.name:demo}`, or `${app.name:}` for an empty default). The grammar rejects an empty key (`${}`), a key that starts or ends with a dot (`${.app}`, `${app.}`), two consecutive dots (`${app..name}`), and an `=` character in the key. The default value part is more permissive and may contain spaces, colons, dots, `!`, `#` and escaped characters. Nested placeholders inside the default value are not part of this grammar.

Fixing this matters because the same text is resolved at runtime by Spring's `PropertySourcesPlaceholderConfigurer` (which Spring Boot registers by default): a placeholder that does not name a real property and has no default causes the bean to fail with an `IllegalArgumentException` ("Could not resolve placeholder ..."), and a placeholder that is malformed usually ends up being resolved to a key that does not exist, or breaks the surrounding SpEL expression once it has been substituted. The check only applies when the project depends on `spring-expression`, only to placeholders that sit inside a SpEL `#{...}` block or a plain SpEL attribute, and can be tuned via the `boot-java.validation.spel.on` toggle and the `spring-boot.ls.problem.spel.PROPERTY_PLACE_HOLDER_SYNTAX` severity setting. A `${...}` placeholder that is used on its own, outside of any SpEL expression (for example `@Value("${app.name}")`), is not covered by this diagnostic.

For more details, see:
- [Spring Framework: Using `@Value`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html) (placeholder syntax, default values with `:`, `PropertySourcesPlaceholderConfigurer`, combining placeholders with SpEL)
- [Spring Framework: Expressions in Bean Definitions](https://docs.spring.io/spring-framework/reference/core/expressions/beandef.html) (the `#{ <expression string> }` template syntax)
- [Spring Framework: Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html)

## Fixes
**Fix 1: Complete the property key**
A key must not start or end with a dot and must not contain empty segments. Usually the author stopped typing or left a dangling separator. Complete the key so that every dot sits between two identifiers.

*Before:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlags {

    @Value("#{${feature.}}")
    private boolean betaEnabled;

    @Value("#{${feature..timeout} * 1000}")
    private long timeoutMillis;
}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlags {

    @Value("#{${feature.beta.enabled:false}}")
    private boolean betaEnabled;

    @Value("#{${feature.timeout:30} * 1000}")
    private long timeoutMillis;
}
```

**Fix 2: Provide a default value with `:` instead of `=`**
The separator between a key and its default value is a colon. An equals sign is not allowed inside the key and is reported as a syntax error.

*Before:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreetingProperties {

    @Value("#{'${app.greeting=Hello}'.toUpperCase()}")
    private String greeting;
}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreetingProperties {

    @Value("#{'${app.greeting:Hello}'.toUpperCase()}")
    private String greeting;
}
```

**Fix 3: Drop the SpEL wrapper when no expression is needed**
If the annotation value is nothing more than a placeholder, there is no reason to wrap it in `#{...}`. A plain `${...}` value is resolved by the placeholder configurer and is not subject to this check, and the intent is clearer. Keep `#{...}` only when you actually operate on the resolved value (string methods, arithmetic, conditionals, collection literals).

*Before:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailProperties {

    @Value("#{${mail.}}")
    private String host;
}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailProperties {

    @Value("${mail.host:localhost}")
    private String host;
}
```

*Note: the check validates the shape of the placeholder only; it does not verify that the property exists in your `application.properties`/`application.yaml` or that its type matches the field. Errors in the SpEL expression around the placeholder are reported as `JAVA_SPEL_EXPRESSION_SYNTAX`. For type-safe access to groups of properties, consider a `@ConfigurationProperties` class instead of many `@Value` fields.*
