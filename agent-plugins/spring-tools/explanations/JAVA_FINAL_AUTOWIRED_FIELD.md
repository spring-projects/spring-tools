## Explanations
This warning appears on a field that is declared `final` and annotated with `@Autowired`. Field injection happens *after* the object has been constructed: Spring instantiates the bean first and only then populates `@Autowired` fields. A `final` field, however, must be assigned exactly once, either at its declaration or in every constructor, and the Java language does not allow it to be assigned afterwards. Therefore the field has to be initialized independently of Spring, which contradicts the intent of `@Autowired`: a blank `final` field that is not assigned in a constructor does not even compile, and a `final` field with an initializer already has its value before Spring gets to see the object.

The intention behind `final` is usually a good one: immutability and dependencies that are guaranteed to be present. The Spring reference documentation recommends constructor injection for exactly this: "The Spring team generally advocates constructor injection, as it lets you implement application components as immutable objects and ensures that required dependencies are not `null`. Furthermore, constructor-injected components are always returned to the client (calling) code in a fully initialized state." With constructor injection the field can stay `final`, and since Spring Framework 4.3 a single constructor does not even need an `@Autowired` annotation (see `JAVA_AUTOWIRED_CONSTRUCTOR`).

The language server raises this diagnostic when all of the following hold:
- the project uses Spring Boot 2.0 or newer;
- the file is in a main (non-test) source folder;
- a field declared directly in a class or interface (not in a record, enum or anonymous class) has the `final` modifier and is annotated with `@Autowired` (`org.springframework.beans.factory.annotation.Autowired`). Fields annotated with `@Inject`, `@Resource` or `@Value` are not checked by this diagnostic.

There is no automated quick fix for this warning, because choosing between the two fixes below is a design decision.

For more details, see:
- [Spring Framework: Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html) ("Constructor-based or setter-based DI?")
- [Spring Framework: Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html) (constructor, method and field injection)

## Fixes
**Fix 1: Switch to constructor injection and keep the field `final` (recommended)**
Remove `@Autowired` from the field, add a constructor parameter for the dependency and assign it in the constructor. If this is the only constructor, no `@Autowired` is needed on it. The field stays `final`, the dependency is guaranteed to be non-null, and the class can be instantiated in plain unit tests without a Spring container.

*Before:*
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    @Autowired
    private final InvoiceRepository invoiceRepository;

    public Invoice find(long id) {
        return this.invoiceRepository.findById(id).orElseThrow();
    }
}
```

*After:*
```java
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public Invoice find(long id) {
        return this.invoiceRepository.findById(id).orElseThrow();
    }
}
```

*Note: if the class has several `@Autowired` fields, move all of them into the constructor in one go. The related hint `JAVA_CONSTRUCTOR_PARAMETER_INJECTION` describes this refactoring for non-final `@Autowired` fields.*

*Note: with Lombok, `@RequiredArgsConstructor` on the class generates the constructor for all `final` fields; the fields then need neither `@Autowired` nor a hand-written constructor.*

**Fix 2: Drop `final` and keep field injection**
If you want to keep field injection (for example in a class that is only ever created by the container and for which you do not care about immutability), remove the `final` modifier so that Spring can assign the field after construction. Optional dependencies can additionally use `@Autowired(required = false)` or an `Optional<T>` field.

*Before:*
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

    @Autowired
    private final AuditEventPublisher publisher;

    public void log(String message) {
        this.publisher.publish(message);
    }
}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

    @Autowired
    private AuditEventPublisher publisher;

    public void log(String message) {
        this.publisher.publish(message);
    }
}
```

*Note: field injection is convenient but harder to test and hides a class's dependencies from its public API. Prefer Fix 1 for mandatory dependencies; the reference documentation suggests "constructors for mandatory dependencies and setter methods or configuration methods for optional dependencies".*
