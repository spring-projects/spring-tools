## Explanations
This warning indicates that a class implementing `org.springframework.beans.factory.BeanRegistrar` is annotated with `@Component` or with a stereotype annotation that is itself meta-annotated with `@Component` (such as `@Service`, `@Repository` or `@Controller`).

`BeanRegistrar` is the Spring Framework 7.0 (Spring Boot 4.0) contract for programmatic bean registration. Its `register(BeanRegistry registry, Environment env)` method is meant to be invoked by the container so the registrar can contribute beans. The documented ways to activate a registrar are to import it with `@Import` on a `@Configuration` class, or to register it programmatically via `GenericApplicationContext.register(BeanRegistrar...)`. Component scanning does neither: a `@Component`-annotated registrar is merely picked up as an ordinary bean of that class, which is not how the registrar contract is designed to be activated. The annotation is therefore misleading at best and typically signals that the registrar was wired up incorrectly.

The language server applies this check when the project depends on `spring-context` 7.0.0 or newer. It looks at every type whose type hierarchy implements `BeanRegistrar` and flags each annotation on that type that is `@Component` or meta-annotated with `@Component`. The problem range covers the annotation's type name, and a quick fix is offered to remove the offending annotation. Note that removing the annotation alone does not register the registrar anywhere; the companion check `REGISTRAR_BEAN_DECLARATION` will report if no `@Configuration` class in the same source folder imports it.

For more details, see:
- [Spring Framework: Programmatic Bean Registration](https://docs.spring.io/spring-framework/reference/core/beans/java/programmatic-bean-registration.html)
- [`BeanRegistrar` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/BeanRegistrar.html) (documents `@Import` and `GenericApplicationContext.register(BeanRegistrar...)` as the ways to apply a registrar)
- [`BeanRegistry` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/BeanRegistry.html)

## Fixes
**Fix 1: Remove the stereotype annotation and import the registrar via `@Import`**
Remove `@Component` (or `@Service`, `@Repository`, ...) from the registrar class and reference the registrar from a `@Configuration` class using `@Import`. This is the approach shown in the Spring Framework reference documentation.

*Before:*
```java
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean("foo", Foo.class);
        if (env.matchesProfiles("baz")) {
            registry.registerBean(Baz.class, spec -> spec
                    .supplier(context -> new Baz("Hello World!")));
        }
    }
}
```

*After:*
```java
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;

public class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean("foo", Foo.class);
        if (env.matchesProfiles("baz")) {
            registry.registerBean(Baz.class, spec -> spec
                    .supplier(context -> new Baz("Hello World!")));
        }
    }
}
```

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(MyBeanRegistrar.class)
public class MyConfiguration {
}
```

*Note: the quick fix offered by the language server only removes the annotation. Make sure a `@Configuration` class in the same source folder imports the registrar afterwards; otherwise `REGISTRAR_BEAN_DECLARATION` is reported.*

**Fix 2: Turn the class into a regular component instead**
If the class was never meant to be a registrar and only happens to implement `BeanRegistrar`, drop the interface and keep it as a plain component. Declare any beans it should contribute with `@Bean` methods in a `@Configuration` class instead.

*Before:*
```java
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class FooSupport implements BeanRegistrar {

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
