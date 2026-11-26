## Explanations
This warning appears on an `@Autowired` annotation that is placed on the only constructor of a class. The diagnostic is tagged as "unnecessary": the annotation has no effect, because the Spring container already uses a class's single constructor for dependency injection without being told to.

Since Spring Framework 4.3, "it is no longer necessary to specify the `@Autowired` annotation if the target bean only defines one constructor" (see the 4.3 release notes linked below). The current reference documentation states the rule explicitly: "An `@Autowired` annotation on such a constructor is not necessary if the target bean defines only one constructor. However, if several constructors are available and there is no primary or default constructor, at least one of the constructors must be annotated with `@Autowired` in order to instruct the container which one to use." In other words, `@Autowired` on a constructor only carries meaning when the class has more than one constructor. On a single constructor it is boilerplate that also drags in an import of `org.springframework.beans.factory.annotation.Autowired` and makes the class look more Spring-specific than it is; a plain single-constructor class is already a perfectly valid Spring bean (the Spring team "generally advocates constructor injection", see the linked "Dependency Injection" chapter).

The language server raises this diagnostic when all of the following hold:
- the project uses Spring Boot 2.0 or newer;
- the file is in a main (non-test) source folder;
- the class declares exactly one constructor, and that constructor carries an `@Autowired` annotation (any form; the `required` attribute is not taken into account);
- the class does not use one of Lombok's constructor-generating annotations (`@RequiredArgsConstructor`, `@AllArgsConstructor`, `@NoArgsConstructor`), because those add constructors that are not visible in the source.

The language server does not check whether the class is actually a Spring component; it only reasons about the constructor count. Classes with two or more constructors are never reported, since there `@Autowired` is exactly the mechanism to pick the constructor to use.

A quick fix ("Remove unnecessary `@Autowired` annotation") is available; it removes the annotation and, if no longer used, its import.

For more details, see:
- [Spring Framework: Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html) (the tip after the constructor example, and the note on constructor resolution with multiple constructors)
- [Spring Framework: Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html) ("Constructor-based or setter-based DI?")
- [Spring Framework 4.3 release notes: Core Container Improvements](https://docs.spring.io/spring-framework/docs/4.3.x/spring-framework-reference/html/new-in-4.3.html)

## Fixes
**Fix 1: Remove the `@Autowired` annotation from the single constructor**
Delete the annotation and drop the `org.springframework.beans.factory.annotation.Autowired` import if nothing else in the file uses it. Spring keeps injecting the constructor arguments exactly as before. This is what the quick fix does.

*Before:*
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    @Autowired
    public OrderService(OrderRepository orderRepository, PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }
}
```

*After:*
```java
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    public OrderService(OrderRepository orderRepository, PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }
}
```

*Note: the same applies to `@Configuration` classes ("`@Configuration` classes support constructor injection" is listed among the 4.3 improvements as well) and to classes declared via `@Bean` methods with a single constructor.*

**Fix 2: Keep `@Autowired` only if you add a second constructor**
If the class needs more than one constructor (for example a convenience constructor used by tests), `@Autowired` becomes meaningful again and the diagnostic disappears. Annotate the constructor that Spring should use; the documentation notes that "if the `required` attribute is left at its default value `true`, only a single constructor may be annotated with `@Autowired`". Do not add a constructor just to silence the warning, though. Prefer Fix 1 whenever there is a single constructor.

*Before:*
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private final String greeting;

    @Autowired
    public GreetingService(GreetingProperties properties) {
        this.greeting = properties.getGreeting();
    }
}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private final String greeting;

    @Autowired
    public GreetingService(GreetingProperties properties) {
        this.greeting = properties.getGreeting();
    }

    // used by tests only; Spring picks the @Autowired constructor above
    GreetingService(String greeting) {
        this.greeting = greeting;
    }
}
```

*Note: if the class uses Lombok's `@RequiredArgsConstructor` (or `@AllArgsConstructor`) there is no hand-written constructor to annotate at all, and the language server does not report the class. The related hint `JAVA_CONSTRUCTOR_PARAMETER_INJECTION` covers the opposite situation, `@Autowired` on fields, and suggests moving to constructor injection.*
