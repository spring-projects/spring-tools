## Explanations
This warning appears in Spring Modulith projects on methods that combine `@Async`, `@Transactional`, and `@TransactionalEventListener`.

Spring Modulith provides the `@org.springframework.modulith.events.ApplicationModuleListener` annotation as a single, meta-annotated replacement for this common combination: it is declared as `@Async @Transactional(propagation = Propagation.REQUIRES_NEW) @TransactionalEventListener`. Using it instead makes the intent of the method clearer (an event listener that reacts to published events after the original transaction committed, asynchronously and in a new transaction of its own), reduces annotation clutter, and is the idiomatic way to declare event listeners in Spring Modulith applications. The reference documentation also recommends combining these listeners with the Event Publication Registry so that a publication is not lost if the listener fails.

Note that since Spring Framework 6.1, a `@TransactionalEventListener` method (for any phase other than `BEFORE_COMMIT`) that is also annotated with `@Transactional` is rejected at startup unless the propagation is `REQUIRES_NEW` or `NOT_SUPPORTED` (`RestrictedTransactionalEventListenerFactory`). A plain `@Transactional` (default propagation `REQUIRED`) on such a method therefore no longer works; `@ApplicationModuleListener` uses `REQUIRES_NEW` by default.

For more details, see:
- [Spring Modulith: Working with Application Events - Application Module Listener](https://docs.spring.io/spring-modulith/reference/events.html#aml)
- [`ApplicationModuleListener` source](https://github.com/spring-projects/spring-modulith/blob/main/spring-modulith-events/spring-modulith-events-api/src/main/java/org/springframework/modulith/events/ApplicationModuleListener.java) (attributes: `readOnlyTransaction`, `id` since 1.1, `condition` since 1.2, `propagation` since 1.3)
- [Spring Framework 6.1 Release Notes - Data Access and Transactions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes#data-access-and-transactions)

## Fixes
**Fix 1: Combine into `@ApplicationModuleListener`**
Remove the `@Async`, `@Transactional`, and `@TransactionalEventListener` annotations and replace them with a single `@ApplicationModuleListener` annotation.

*Before:*
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

class OrderEventListener {

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(OrderCompleted event) {
        // ...
    }

}
```

*After:*
```java
import org.springframework.modulith.events.ApplicationModuleListener;

class OrderEventListener {

    @ApplicationModuleListener
    void on(OrderCompleted event) {
        // ...
    }

}
```

*Note: Supported attributes on the original annotations are carried over to `@ApplicationModuleListener`: `@Transactional`'s `readOnly` becomes `readOnlyTransaction`, `@Transactional`'s `propagation` is kept as `propagation`, and `@TransactionalEventListener`'s `id` and `condition` are kept as-is. `id` requires Spring Modulith 1.1+, `condition` 1.2+ and `propagation` 1.3+. Because `REQUIRES_NEW` is the default of `@ApplicationModuleListener`, an explicit `propagation = Propagation.REQUIRES_NEW` can also simply be dropped.*

*Before (with supported attributes):*
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

class OrderEventListener {

    @Async
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(id = "orderCompleted", condition = "#event.valid")
    void on(OrderCompleted event) {
        // ...
    }

}
```

*After (with supported attributes):*
```java
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Propagation;

class OrderEventListener {

    @ApplicationModuleListener(readOnlyTransaction = true, propagation = Propagation.REQUIRES_NEW, id = "orderCompleted", condition = "#event.valid")
    void on(OrderCompleted event) {
        // ...
    }

}
```

*Note: If any of the three annotations is missing, or uses an attribute that `@ApplicationModuleListener` does not support (e.g. `@Async("executorName")`, `@Transactional(timeout = ...)`, or `@TransactionalEventListener(phase = ...)`), the method is left unchanged, since combining them would silently drop behavior.*
