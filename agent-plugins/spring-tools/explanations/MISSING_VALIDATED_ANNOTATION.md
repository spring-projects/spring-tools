## Explanations
This warning indicates that a Spring component (a class annotated with `@Component`, `@Service`, `@Repository` or another stereotype meta-annotated with `@Component`) declares Bean Validation constraints on method parameters or on a method's return value, for example `@NotNull`, `@Size`, `@Min` or `@Valid`, but the class is not annotated with `@Validated` (`org.springframework.validation.annotation.Validated`).

Constraint annotations on methods of an ordinary bean do nothing on their own. Method-level Bean Validation in Spring is driven by a `MethodValidationPostProcessor`, which wraps eligible beans in an AOP proxy that validates arguments and return values on each call. The Spring Framework reference documentation states the requirement: "To be eligible for Spring-driven method validation, target classes need to be annotated with Spring's `@Validated` annotation, which can optionally also declare the validation groups to use." Spring Boot's documentation says the same for auto-configured applications: "The method validation feature supported by Bean Validation 1.1 is automatically enabled as long as a JSR-303 implementation (such as Hibernate Validator, typically provided by `spring-boot-starter-validation`) is on the classpath. This lets bean methods be annotated with `jakarta.validation` constraints on their parameters and/or on their return value. Target classes with such annotated methods need to be annotated with the `@Validated` annotation at the type level for their methods to be searched for inline constraint annotations." Without `@Validated`, constraints such as `@Size(min = 8)` on a service method parameter are never enforced, which typically goes unnoticed until invalid data reaches the database or a downstream system.

Controllers are the exception. Spring MVC and WebFlux "have built-in support for the same underlying method validation but without the need for AOP" (Spring Framework 6.1 and later), and the MVC documentation even advises: "If a controller has a class level `@Validated`, then method validation is applied through an AOP proxy. In order to take advantage of the Spring MVC built-in support for method validation added in Spring Framework 6.1, you need to remove the class level `@Validated` annotation from the controller." The language server therefore does not report classes that are annotated (directly or through meta-annotation) with `@Controller`, which includes `@RestController`.

The language server raises this diagnostic when all of the following hold:
- the class is a concrete class (not an interface, not abstract, not a non-static inner class) that is annotated or meta-annotated with `org.springframework.stereotype.Component`;
- the class is not annotated or meta-annotated with `@Controller`;
- the class is not already annotated with `@Validated`;
- at least one method has a parameter annotation or a method (return value) annotation from `jakarta.validation` or the legacy `javax.validation` namespace: `@Valid` or one of the standard constraints (`@NotNull`, `@NotEmpty`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Email`, `@Pattern`, `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero`, `@Past`, `@PastOrPresent`, `@Future`, `@FutureOrPresent`, `@Digits`, `@DecimalMin`, `@DecimalMax`, `@AssertTrue`, `@AssertFalse`, `@Null`).

Constraints on *fields* of the component are not considered by this check; they are validated when the object itself is validated (for example via `@Valid` on a parameter), not through method validation. The check does not depend on a particular Spring Boot version.

A quick fix ("Add missing '@Validated' annotation in file") adds the annotation and its import to the class declarations in the file; review the result when the file contains more than one top-level or nested class.

For more details, see:
- [Spring Framework: Java Bean Validation, "Spring-driven Method Validation"](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- [Spring Boot: Validation](https://docs.spring.io/spring-boot/reference/io/validation.html) (method validation auto-configuration and `spring-boot-starter-validation`)
- [Spring Framework: Spring MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html) (why controllers should *not* carry a class-level `@Validated`)

## Fixes
**Fix 1: Add `@Validated` to the component class**
Annotate the class with `@Validated` from `org.springframework.validation.annotation`. Make sure a Bean Validation provider is on the classpath; in a Spring Boot application add the `spring-boot-starter-validation` dependency, which brings Hibernate Validator and enables the auto-configured `MethodValidationPostProcessor`. Violations are reported as `jakarta.validation.ConstraintViolationException` by default. This is what the quick fix does.

*Before:*
```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account create(@Valid AccountRequest request) {
        return this.accountRepository.save(request.toAccount());
    }

    public Account findByCode(@NotBlank @Size(min = 8, max = 10) String code) {
        return this.accountRepository.findByCode(code);
    }
}
```

*After:*
```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account create(@Valid AccountRequest request) {
        return this.accountRepository.save(request.toAccount());
    }

    public Account findByCode(@NotBlank @Size(min = 8, max = 10) String code) {
        return this.accountRepository.findByCode(code);
    }
}
```

*Note: method validation "relies on AOP Proxies around the target classes, either JDK dynamic proxies for methods on interfaces or CGLIB proxies". Constraints are only checked for calls that go through the proxy, so a method calling another constrained method on `this` bypasses validation; the documentation reminds you to "always use methods and accessors on proxied classes; direct field access will not work".*

*Note: if you prefer Spring's `MethodValidationException` over `ConstraintViolationException`, declare your own `MethodValidationPostProcessor` bean and call `setAdaptConstraintViolations(true)` on it, as described in the linked Spring Framework reference documentation.*

**Fix 2: Remove the constraint annotations if method validation is not wanted**
If the constraints were added by mistake or are enforced elsewhere (for example the same request object is already validated with `@Valid` in the controller that calls the service), remove them from the component's method signatures instead of adding `@Validated`. Leaving them in place gives the false impression that they are enforced.

*Before:*
```java
import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Component;

@Component
public class PriceCalculator {

    public Money total(@NotNull Order order) {
        return order.lines().stream().map(OrderLine::price).reduce(Money.ZERO, Money::add);
    }
}
```

*After:*
```java
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class PriceCalculator {

    public Money total(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        return order.lines().stream().map(OrderLine::price).reduce(Money.ZERO, Money::add);
    }
}
```

*Note: for controllers the situation is reversed. Constraint annotations on `@RequestMapping` method parameters are enforced by Spring MVC and WebFlux themselves (Spring Framework 6.1+), and a class-level `@Validated` should be removed there. The language server does not report `@Controller` and `@RestController` classes for this reason.*
