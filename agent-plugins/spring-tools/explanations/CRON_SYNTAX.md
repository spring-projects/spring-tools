## Explanations
This error is raised on the string value of the `cron` attribute of `@Scheduled` (`org.springframework.scheduling.annotation.Scheduled`) when the value does not conform to the structure of a Spring cron expression. The language server parses the string with its own cron grammar; every lexer or parser error is reported under this code with a message that starts with `CRON:` followed by the parser's description (for example a missing field or an unexpected character), positioned at the offending place inside the string literal. Errors in the *value* of an otherwise well-structured field (such as `60` in the seconds field) are a separate diagnostic, see `CRON_FIELD`.

A Spring cron expression consists of exactly six whitespace-separated fields in the order second, minute, hour, day of month, month and day of week, or, alternatively, of a single macro such as `@hourly`. Each field is either `*` or `?`, a number or an upper-case three-letter name (`JAN`-`DEC`, `MON`-`SUN`), an inclusive range `a-b`, a comma-separated list of those, or any of these followed by `/n` for a step. The grammar accepts `?` and month/day names in any field; using them in a field where Spring does not allow them (for example `?` outside the day-of-month and day-of-week fields) is reported by the `CRON_FIELD` check. The day-of-month field additionally accepts `L`, `L-n`, `nW` and `LW`, and the day-of-week field accepts `dL`, `DDDL`, `d#n` and `DDD#n`. Anything else is a syntax error: most commonly a classic five-field Unix crontab string (`0 8 * * MON-FRI`) that lacks the leading seconds field, a seventh field, a stray character, a doubled separator, a misspelled or spelled-out name (`MONDAY`, `January`), or `L`, `W` and `#` used in a field whose grammar does not allow them.

The check applies to projects that have a dependency whose name starts with `spring-context` (every Spring Boot application), only to the `@Scheduled(cron = ...)` attribute written in the `name = value` form, and only when the value is a string literal, a text block or a concatenation of compile-time constants. Values that start with `#{` or `${` are skipped entirely, because the expression or placeholder is resolved at runtime and the literal text is not a cron expression yet. Fixing this matters because Spring resolves the value with `CronExpression.parse(String)` when the bean is initialised and that method throws an `IllegalArgumentException` for anything that does not follow the format, so the application context fails to start. The check can be tuned via the `boot-java.validation.cron` toggle and the `spring-boot.ls.problem.cron.CRON_SYNTAX` severity setting.

For more details, see:
- [Spring Framework: Task Execution and Scheduling, Cron Expressions](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) (the six-field format, special characters and the list of macros)
- [`CronExpression` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html) (the exact parsing rules and example expressions)
- [`@Scheduled` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html) (the `cron`, `zone` and `CRON_DISABLED` elements)

## Fixes
**Fix 1: Add the seconds field to a five-field Unix cron string**
Unix `crontab` expressions start with the minute; Spring expressions start with the second. Prepend a seconds field (usually `0`) so that the string has six fields.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportJob {

    @Scheduled(cron = "0 8 * * MON-FRI")
    public void sendDailyReport() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportJob {

    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void sendDailyReport() {
        // ...
    }
}
```

**Fix 2: Remove extra fields and stray characters**
Seven or more fields (for example a Quartz-style trailing year field), doubled separators or characters that are not part of the grammar (`;`, `.`, unbalanced parentheses) make the whole expression unparseable. Reduce the string to six well-formed fields separated by single spaces.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {

    @Scheduled(cron = "0 0 3 * * ? 2026")
    public void purgeExpiredTokens() {
        // ...
    }

    @Scheduled(cron = "0 */15 9-17 * * MON;FRI")
    public void refreshCache() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {

    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeExpiredTokens() {
        // ...
    }

    @Scheduled(cron = "0 */15 9-17 * * MON,FRI")
    public void refreshCache() {
        // ...
    }
}
```

**Fix 3: Use the upper-case three-letter names**
The grammar knows the names `JAN`-`DEC` and `MON`-`SUN` only in their upper-case three-letter form. Spelled-out or lower-case names are not recognised as names and break the expression.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsletterJob {

    @Scheduled(cron = "0 0 9 * * Monday-Friday")
    public void sendNewsletter() {
        // ...
    }

    @Scheduled(cron = "0 0 9 1 January *")
    public void sendYearlySummary() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsletterJob {

    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void sendNewsletter() {
        // ...
    }

    @Scheduled(cron = "0 0 9 1 JAN *")
    public void sendYearlySummary() {
        // ...
    }
}
```

**Fix 4: Use a macro for common schedules**
For the standard intervals Spring supports the macros `@yearly` (or `@annually`), `@monthly`, `@weekly`, `@daily` (or `@midnight`) and `@hourly`. A macro replaces the whole six-field expression and is easier to read than the equivalent numeric form.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HousekeepingJob {

    @Scheduled(cron = "0 0 0 * *")
    public void rotateLogs() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HousekeepingJob {

    @Scheduled(cron = "@daily")
    public void rotateLogs() {
        // ...
    }
}
```

**Fix 5: Externalise the expression with a placeholder**
If the schedule should be configurable, move the expression to `application.properties` and reference it with a `${...}` placeholder (optionally with a default). The language server then skips the literal, and the special value `-` (`Scheduled.CRON_DISABLED`) can be used in configuration to switch the trigger off.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncJob {

    @Scheduled(cron = "0 0 2 * * *,")
    public void synchronise() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncJob {

    @Scheduled(cron = "${sync.cron:0 0 2 * * *}")
    public void synchronise() {
        // ...
    }
}
```

```properties
# application.properties
sync.cron=0 0 2 * * *
# use "-" to disable the trigger in an environment
# sync.cron=-
```

*Note: the grammar check only validates the structure of the expression. A structurally valid field whose value is out of range, whose range is inverted, or that is otherwise rejected by Spring's own `CronExpression` parser is reported as `CRON_FIELD`. An unknown macro name such as `@weekdays` is accepted by this grammar check but is rejected by Spring at runtime.*
