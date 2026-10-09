## Explanations
This error is raised on a string literal inside a Java annotation attribute that is supposed to contain a Spring Expression Language (SpEL) expression, when the language server's SpEL parser cannot parse that string. The diagnostic message starts with `SPEL:` followed by the parser's own description of the problem (for example an unexpected or missing token), and it is reported on the exact position inside the string literal where the parse failed.

The language server only performs this check when the project has a dependency whose artifact name starts with `spring-expression` (that module is pulled in transitively by `spring-context`, so any Spring Boot application has it). It knows which annotation attributes carry SpEL and in which flavour: some attributes are *plain* SpEL, meaning the whole string is an expression (for example `@Cacheable`/`@CachePut`/`@CacheEvict` `key`, `condition` and `unless`, `@EventListener` `condition`, Spring Security's `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter` `value`, `@AuthenticationPrincipal` and `@CurrentSecurityContext` `expression`, `@Payload` `value`/`expression`, Spring Integration `@Gateway`/`@GatewayHeader`/`@MessagingGateway` expression attributes); other attributes are *templates*, in which SpEL only appears inside `#{...}` blocks and the surrounding text is plain (for example `@Value`, `@Async`, `@Scheduled` `cron`/`zone`/`fixedDelayString`/`fixedRateString`/`initialDelayString`, `@JmsListener`, `@RabbitListener`/`@Queue`/`@Exchange`/`@QueueBinding` and `@KafkaListener` attributes, Spring Framework's `org.springframework.resilience.annotation.Retryable`/`ConcurrencyLimit` string attributes, and, as optional templates, Spring Boot's `@ConditionalOnExpression` and Spring Retry's `@Retryable`/`@Backoff` expression attributes). Meta-annotations that are annotated with one of these are recognised as well, and each element of an array attribute is checked separately. Annotation values may be simple string literals, text blocks or concatenations of constants. In templates only the content between `#{` and the matching `}` is parsed, and an unclosed `#{` produces no diagnostic at all. SpEL `#{...}` blocks embedded in Spring Data queries (`@Query`) are also handed to the SpEL parser and their syntax errors are reported under this code.

The check is a pure syntax check: it does not know the evaluation context, so it will not complain about unknown properties, bean names, method names or variables, and it does not validate the content of `${...}` property placeholders inside the expression (see `PROPERTY_PLACE_HOLDER_SYNTAX`). Like the other ANTLR-based checks in the language server, only errors detected by the parser are reported; stray characters the lexer cannot tokenise are skipped. Fixing the syntax matters because Spring parses these expressions at runtime and throws a `ParseException` (typically wrapped in a `BeanCreationException`, `IllegalArgumentException` or, for security annotations, at the first method invocation), so an application with a malformed expression either fails to start or fails on the first call of the annotated method. The check can be tuned via the `boot-java.validation.spel.on` toggle and the `spring-boot.ls.problem.spel.JAVA_SPEL_EXPRESSION_SYNTAX` severity setting.

For more details, see:
- [Spring Framework: Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html) (overview)
- [Spring Framework: SpEL Language Reference](https://docs.spring.io/spring-framework/reference/core/expressions/language-ref.html) (literals, operators, variables, bean references, collection selection and projection)
- [Spring Framework: Expressions in Bean Definitions](https://docs.spring.io/spring-framework/reference/core/expressions/beandef.html) (the `#{ <expression string> }` template syntax used by `@Value`)
- [Spring Framework: Using `@Value`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html) (mixing SpEL with `${...}` placeholders)
- [Spring Framework: Cache Abstraction, Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html) (SpEL in `key`, `condition` and `unless`)
- [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) (SpEL in `@PreAuthorize`, `@PostAuthorize`, `@PreFilter` and `@PostFilter`)

## Fixes
**Fix 1: Balance quotes, parentheses and brackets**
The most common cause is an unterminated string literal or an unbalanced parenthesis or bracket inside the expression. SpEL string literals are delimited by single quotes (a double-quoted form is also accepted), and every `(`, `[` and `{` must be closed. Read the position reported by the diagnostic, find the token that the parser did not expect and complete the literal or the grouping.

*Before:*
```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    @Cacheable(value = "books", key = "#isbn", condition = "#isbn.startsWith('978")
    public Book findByIsbn(String isbn) {
        // ...
    }
}
```

*After:*
```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    @Cacheable(value = "books", key = "#isbn", condition = "#isbn.startsWith('978')")
    public Book findByIsbn(String isbn) {
        // ...
    }
}
```

**Fix 2: Use the template syntax correctly in `@Value`**
In template attributes such as `@Value`, SpEL only lives inside `#{...}`. A `${...}` placeholder on its own is not SpEL and is not parsed as such, but as soon as it is wrapped in `#{...}` the content becomes an expression. Make sure the `#{` has a matching `}` and that what is inside forms a complete expression. When you need a placeholder *inside* the expression, keep the `${...}` intact and, if the resolved value must be treated as a string, put quotes around it.

*Before:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreetingProperties {

    @Value("#{'${app.greeting.prefix}'.toUpperCase( }")
    private String prefix;

    @Value("#{ systemProperties['user.region' }")
    private String region;
}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GreetingProperties {

    @Value("#{'${app.greeting.prefix}'.toUpperCase()}")
    private String prefix;

    @Value("#{ systemProperties['user.region'] }")
    private String region;
}
```

**Fix 3: Quote string arguments and reference variables with `#`**
In Spring Security expressions, role and authority names are string literals and must be written in single quotes (`hasRole('ADMIN')`), and multiple arguments must be separated by commas (`hasAnyRole('ADMIN', 'AUDITOR')`). Method parameters are referenced as variables with a leading `#` (`#id`), the return value as `returnObject` and the current element in filters as `filterObject`. A missing comma between arguments, an unbalanced quote or a dangling `and`/`or` operator without a right-hand side are all reported as syntax errors.

*Before:*
```java
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @PreAuthorize("hasAnyRole('ADMIN' 'AUDITOR') and")
    public Account readAccount(Long id) {
        // ...
    }

    @PostAuthorize("returnObject.owner == authentication.name ||")
    public Account findAccount(Long id) {
        // ...
    }
}
```

*After:*
```java
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR') and #id != null")
    public Account readAccount(Long id) {
        // ...
    }

    @PostAuthorize("returnObject.owner == authentication.name")
    public Account findAccount(Long id) {
        // ...
    }
}
```

*Note: this diagnostic only proves that the expression is syntactically well-formed. Whether `#isbn`, `authentication.name` or a bean referenced as `@myBean` actually exists is decided at runtime by the evaluation context of the respective feature. Malformed `${...}` placeholders inside a SpEL expression are reported separately as `PROPERTY_PLACE_HOLDER_SYNTAX`, and syntax errors in the query text around a `#{...}` block in a Spring Data `@Query` are reported as `JPQL_SYNTAX`, `HQL_SYNTAX` or `SQL_SYNTAX`.*
