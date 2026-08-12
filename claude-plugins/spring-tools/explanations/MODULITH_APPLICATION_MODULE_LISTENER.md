## Explanations
This warning appears in Spring Modulith projects on methods that combine `@Async`, `@Transactional`, and `@TransactionalEventListener`.

Spring Modulith provides the `@org.springframework.modulith.events.ApplicationModuleListener` annotation as a single, meta-annotated replacement for this common combination. Using it instead makes the intent of the method clearer (an event listener that reacts to published events in a new transaction, asynchronously), reduces annotation clutter, and is the idiomatic way to declare event listeners in Spring Modulith applications.

## Fixes
**Fix 1: Combine into `@ApplicationModuleListener`**
Remove the `@Async`, `@Transactional`, and `@TransactionalEventListener` annotations and replace them with a single `@ApplicationModuleListener` annotation.

*Before:*
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

class OrderEventListener {

    @Async
    @Transactional
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

*Note: Supported attributes on the original annotations are carried over to `@ApplicationModuleListener`: `@Transactional`'s `readOnly` becomes `readOnlyTransaction`, `@Transactional`'s `propagation` is kept as `propagation`, and `@TransactionalEventListener`'s `id` and `condition` are kept as-is.*

*Before (with supported attributes):*
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

class OrderEventListener {

    @Async
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
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

    @ApplicationModuleListener(readOnlyTransaction = true, propagation = Propagation.REQUIRED, id = "orderCompleted", condition = "#event.valid")
    void on(OrderCompleted event) {
        // ...
    }

}
```

*Note: If any of the three annotations is missing, or uses an attribute that `@ApplicationModuleListener` does not support (e.g. `@Async("executorName")`, `@Transactional(timeout = ...)`, or `@TransactionalEventListener(phase = ...)`), the method is left unchanged, since combining them would silently drop behavior.*
