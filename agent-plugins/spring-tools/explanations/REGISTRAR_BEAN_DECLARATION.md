## Explanations
This warning indicates that a class implementing `org.springframework.beans.factory.BeanRegistrar` exists in your project, but no `@Configuration` class imports it via `@Import`.

`BeanRegistrar` is the Spring Framework 7.0 (Spring Boot 4.0) contract for programmatic bean registration: the container calls `register(BeanRegistry registry, Environment env)` and the registrar contributes beans using the `BeanRegistry` API (conditionally, in loops, with custom suppliers, and so on). A registrar only takes effect once it is activated. According to the reference documentation, registrars are typically imported with `@Import` on a `@Configuration` class; the alternative is to register them programmatically with `GenericApplicationContext.register(BeanRegistrar...)`. A registrar that is neither imported nor registered is dead code, and the beans it is supposed to provide are silently missing from the application context.

The language server applies this check when the project depends on `spring-context` 7.0.0 or newer. For each type whose hierarchy implements `BeanRegistrar`, it collects the `@Configuration` beans that live in the same source folders as the registrar (for example `src/main/java`) and checks whether any of them has an `@Import` whose `value` contains the registrar class. If none does, the type name is flagged with the message "No @Import found for bean registrar". A quick fix is offered for each candidate configuration class to add the registrar to its `@Import` annotation. Registrations from other source folders (for example a `@Configuration` in `src/test/java` importing a registrar from `src/main/java`) and programmatic registrations on a `GenericApplicationContext` are not taken into account.

For more details, see:
- [Spring Framework: Programmatic Bean Registration](https://docs.spring.io/spring-framework/reference/core/beans/java/programmatic-bean-registration.html)
- [`BeanRegistrar` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/BeanRegistrar.html)
- [`BeanRegistry` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/BeanRegistry.html)

## Fixes
**Fix 1: Import the registrar from an existing `@Configuration` class**
Add the registrar class to the `@Import` annotation of a configuration class in the same source folder. If the configuration already has an `@Import`, add the registrar to its value array.

*Before:*
```java
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

public class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean("foo", Foo.class);
        registry.registerBean("bar", Bar.class, spec -> spec
                .prototype()
                .lazyInit()
                .description("Custom description")
                .supplier(context -> new Bar(context.bean(Foo.class))));
    }
}
```

```java
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {
}
```

*After:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(MyBeanRegistrar.class)
public class MyConfiguration {
}
```

*Note: when the configuration class already imports other classes, extend the array, e.g. `@Import({ OtherConfig.class, MyBeanRegistrar.class })`. Type-level conditional annotations such as `@Conditional` on the registrar are honored by `@Import`.*

**Fix 2: Create a dedicated `@Configuration` class for the import**
If there is no suitable configuration class in the same source folder, add a small one whose only purpose is to import the registrar.

*Before:*
```java
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

public class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean(MyRepository.class);
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(MyBeanRegistrar.class)
public class MyBeanRegistrarConfiguration {
}
```

**Fix 3: Remove an unused registrar**
If the registrar is a leftover and the beans it registers are already declared elsewhere (for example via `@Bean` methods or component scanning), delete the registrar class instead of importing it.

*Before:*
```java
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

public class LegacyRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean("foo", Foo.class);
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FooConfiguration {

    @Bean
    Foo foo() {
        return new Foo();
    }
}
```

*Note: registrars must not be annotated with `@Component` or a stereotype derived from it; see `REGISTRAR_BEAN_INVALID_ANNOTATION`.*
