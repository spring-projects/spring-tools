## Explanations
This error is raised on a single field of the cron expression in `@Scheduled(cron = ...)` when the field is structurally well-formed but its value is rejected by Spring Framework's own cron parser. After the language server has verified the overall structure of the expression (see `CRON_SYNTAX`), it hands each of the six fields to the corresponding parsing routine of Spring's `org.springframework.scheduling.support.CronField` class (the same code that `CronExpression.parse(String)` uses at runtime). If Spring throws, the exception message is reported under this code, prefixed with `CRON:` and underlining exactly the field in question; a failure to read a number is reported as `CRON: Number expected`.

Typical causes are values outside the allowed range of a field (seconds and minutes `0-59`, hours `0-23`, day of month `1-31`, month `1-12` or `JAN`-`DEC`, day of week `0-7` or `MON`-`SUN` where both `0` and `7` mean Sunday), a range whose lower bound is greater than its upper bound, a step of `0`, a month or weekday name in a field that only accepts numbers (`MON` in the hour field), or an out-of-range number inside one of the special forms `nW`, `L-n`, `dL` and `d#n`. Because the language server runs the real Spring parser, a `CRON_FIELD` error is a reliable prediction that `CronExpression.parse` will throw an `IllegalArgumentException` when the bean is initialised, which prevents the application context from starting.

The check applies to projects with a dependency whose name starts with `spring-context`, only to the `cron` attribute of `@Scheduled` written as `cron = "..."`, and only when the value is a string literal, a text block or a concatenation of compile-time constants. Values starting with `#{` or `${` are not validated because they are resolved at runtime. The check can be tuned via the `boot-java.validation.cron` toggle and the `spring-boot.ls.problem.cron.CRON_FIELD` severity setting.

For more details, see:
- [Spring Framework: Task Execution and Scheduling, Cron Expressions](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) (valid ranges per field, special characters, examples)
- [`CronExpression` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html) (the exact rules, including `L`, `W`, `#` and the day-of-week numbering)
- [`@Scheduled` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)

## Fixes
**Fix 1: Keep each value inside the range of its field**
Check the underlined field against the allowed range: second `0-59`, minute `0-59`, hour `0-23`, day of month `1-31`, month `1-12`, day of week `0-7`. Remember that the first field is the *second*, so a value that would be valid as a minute in a Unix crontab may be out of range once it lands in a different position.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingJob {

    @Scheduled(cron = "0 60 24 * * *")
    public void closeDay() {
        // ...
    }

    @Scheduled(cron = "0 0 6 31 13 *")
    public void yearEnd() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingJob {

    @Scheduled(cron = "0 0 0 * * *")
    public void closeDay() {
        // ...
    }

    @Scheduled(cron = "0 0 6 31 12 *")
    public void yearEnd() {
        // ...
    }
}
```

**Fix 2: Write ranges and steps in ascending order with a non-zero step**
A range must be `low-high` and a step must be a positive number. `17-9` or `*/0` pass the structural check but are rejected by Spring.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricsJob {

    @Scheduled(cron = "*/0 * 17-9 * * *")
    public void publishMetrics() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricsJob {

    @Scheduled(cron = "*/30 * 9-17 * * *")
    public void publishMetrics() {
        // ...
    }
}
```

**Fix 3: Put names into the field they belong to**
Month names (`JAN`-`DEC`) are only valid in the month field and weekday names (`MON`-`SUN`) only in the day-of-week field. A name in another position is rejected even though the expression looks well-formed. This usually happens when a field was dropped or the order of fields was mixed up.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsletterJob {

    @Scheduled(cron = "0 0 9 * MON-FRI *")
    public void sendNewsletter() {
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
}
```

**Fix 4: Use in-range numbers with `W`, `L` and `#`**
The special forms are `nW` (nearest weekday to day `n` of the month, `n` between 1 and 31), `L-n` (`n` days before the last day of the month), `dL` (last weekday `d` of the month) and `d#n` (the `n`-th weekday `d` of the month). The numbers in these forms are validated by Spring just like plain field values.

*Before:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PayrollJob {

    @Scheduled(cron = "0 0 0 32W * *")
    public void runPayroll() {
        // ...
    }

    @Scheduled(cron = "0 0 0 ? * MON#0")
    public void teamMeeting() {
        // ...
    }
}
```

*After:*
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PayrollJob {

    @Scheduled(cron = "0 0 0 LW * *")
    public void runPayroll() {
        // ...
    }

    @Scheduled(cron = "0 0 0 ? * MON#1")
    public void teamMeeting() {
        // ...
    }
}
```

*Note: a value that breaks the structure of the expression (missing or extra fields, stray characters, misspelled or lower-case names, `L`/`W`/`#` in a field where the grammar does not allow them) is reported as `CRON_SYNTAX` instead. The language server only checks literal expressions; if you use a `${...}` placeholder to externalise the schedule, the value in `application.properties` is validated by Spring at startup, not by this diagnostic.*
