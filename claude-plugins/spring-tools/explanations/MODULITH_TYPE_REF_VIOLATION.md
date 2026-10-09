## Explanations
This error appears in Spring Modulith projects on a type reference (an import, a fully qualified name, or a simple type name in a declaration, parameter, field or expression) that points to an internal type of another application module, i.e. a type the owning module does not expose.

Spring Modulith derives application modules from the package structure below the `@SpringBootApplication` class: by default each direct sub-package of the main package is an application module. A module's base package is its API package; all public types in it are exposed to other modules (the so-called unnamed interface). Every sub-package of the base package (for example `example.order.internal`) is considered internal and must not be referenced from other modules, unless the package is explicitly exposed as a named interface via `@NamedInterface` in its `package-info.java`, or the module is declared as an open module with `@ApplicationModule(type = Type.OPEN)`. Because Java visibility cannot express this rule (a class in an internal package is often `public` so that the module's own API classes can use it), the Java compiler does not prevent such references. Spring Modulith's `ApplicationModules.of(Application.class).verify()` rejects them at test time ("Efferent module access via API packages only"); the language server surfaces the same violation while you edit, before the verification test runs.

How the language server decides: the check is active when `spring-modulith-core` is on the project's (non-test) classpath. It only inspects Java sources in non-test source folders. For each type reference it looks up the module that owns the referenced type by walking the referenced package hierarchy upwards until it hits a module base package; the reference is fine if the referencing package belongs to the same module, or if the referenced type is contained in one of the owning module's exposed (named or unnamed) interfaces. Otherwise the error `Invalid reference to non-exposed type of module '<module>'!` is reported. Module metadata is not derived from source: the language server runs Spring Modulith's own `ApplicationModulesExporter` on the project's compiled classes and classpath and caches the resulting module structure (base packages, display names and named interfaces). Consequences:

- The project must be compiled; if the output folder has no `.class` files, no module metadata exists and no violations are reported.
- Metadata is refreshed automatically when `package-info.class` files or new class files appear (or on demand via the `sts/modulith/metadata/refresh` LSP command, exposed in VS Code as the "Refresh Modulith Metadata" command). Automatic project tracking is controlled by the `boot-java.modulith-project-tracking` setting, which is enabled by default in VS Code and Eclipse; check that it has not been switched off (or trigger the refresh) if no Modulith diagnostics show up. Clients that send no value for the setting (for example a bare MCP setup) leave tracking disabled on the server side.
- Nested modules, open modules and `allowedDependencies` restrictions follow whatever the exporter reports for the current classes; the error only concerns access to non-exposed types, not module cycles or violations of explicitly declared `allowedDependencies`, which remain the job of `ApplicationModules.verify()`.

There is no automatic quick fix, because resolving the violation is a design decision: either the referenced type is supposed to be public API (expose it) or the referencing code should depend on the module's API instead.

For more details, see:
- [Spring Modulith: Fundamentals - Application Modules](https://docs.spring.io/spring-modulith/reference/fundamentals.html) (simple and advanced modules, API vs. internal packages, named interfaces, explicit `allowedDependencies`, open modules, nested modules since 1.3)
- [Spring Modulith: Verifying Application Module Structure](https://docs.spring.io/spring-modulith/reference/verification.html) (`ApplicationModules.of(...).verify()`, the rules it enforces, `detectViolations()`)

## Fixes
**Fix 1: Depend on the module's API type instead of its internal implementation**
The preferred fix is to change the referencing code so it uses a type from the other module's base package (its API), for example a service facade or a published event, and let that API delegate to the internal type. Internals stay hidden and the module boundary remains intact.

*Before:*
```java
package example.inventory;

import example.order.internal.OrderRepository;
import org.springframework.stereotype.Service;

@Service
class InventoryManagement {

    private final OrderRepository orders;

    InventoryManagement(OrderRepository orders) {
        this.orders = orders;
    }

    boolean hasOpenOrders(String productId) {
        return !orders.findOpenByProductId(productId).isEmpty();
    }

}
```

*After:*
```java
package example.inventory;

import example.order.OrderManagement;
import org.springframework.stereotype.Service;

@Service
class InventoryManagement {

    private final OrderManagement orders;

    InventoryManagement(OrderManagement orders) {
        this.orders = orders;
    }

    boolean hasOpenOrders(String productId) {
        return orders.hasOpenOrders(productId);
    }

}
```

*Note: `OrderManagement` lives in the module's base package `example.order` and is `public`, so it is part of the module's API. Add the needed operation there (e.g. `hasOpenOrders(String)`) and keep `OrderRepository` in `example.order.internal`.*

**Fix 2: Move the type into the module's base package to expose it**
If the referenced type is genuinely meant to be part of the module's public API (typically an interface, a value type or an event), move it from the internal sub-package into the module's base package. Types in the base package are exposed through the unnamed interface.

*Before:*
```java
package example.order.internal;

public record OrderCompleted(String orderId) {
}
```

*After:*
```java
package example.order;

public record OrderCompleted(String orderId) {
}
```

*Note: Update the import in the referencing module accordingly (`import example.order.OrderCompleted;`). Keep the base package small: only types other modules must know about belong there.*

**Fix 3: Expose the sub-package as a named interface**
When a whole sub-package constitutes an API of its own (an SPI, a set of DTOs, ...), keep the types where they are and mark the package as a named interface in its `package-info.java`. Other modules may then reference the types in that package, and modules with explicit `allowedDependencies` can target it via `order :: spi` (or all named interfaces via `order :: *`).

*Before:*
```java
package example.order.spi;

public interface OrderPriceCalculator {

    long calculatePrice(String orderId);

}
```

*After:*
```java
// src/main/java/example/order/spi/package-info.java
@org.springframework.modulith.NamedInterface("spi")
package example.order.spi;
```

```java
package example.order.spi;

public interface OrderPriceCalculator {

    long calculatePrice(String orderId);

}
```

*Note: If the referencing module restricts its dependencies with `@ApplicationModule(allowedDependencies = ...)`, add the named interface there as well (`allowedDependencies = "order :: spi"`), otherwise `ApplicationModules.verify()` will still fail even though the language server no longer reports the type reference. After adding or changing a `package-info.java`, rebuild the project so the language server picks up the new module metadata.*

**Fix 4: Declare the module as open (legacy code bases only)**
For existing applications that are being modularized step by step, a module can temporarily be declared open with `@ApplicationModule(type = Type.OPEN)` in its `package-info.java`. All of its types, including those in sub-packages, then count as exposed and references to them are allowed.

*Before:*
```java
// src/main/java/example/order/package-info.java
package example.order;
```

*After:*
```java
// src/main/java/example/order/package-info.java
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package example.order;
```

*Note: The Spring Modulith reference documentation describes open modules as a migration aid; in a fully modularized application they usually indicate sub-optimal packaging. Prefer Fix 1 to Fix 3 and remove the open declaration once the module has a proper API. Event-based interaction between modules (see `MODULITH_APPLICATION_MODULE_LISTENER`) is another way to avoid direct type dependencies on other modules' internals altogether.*
