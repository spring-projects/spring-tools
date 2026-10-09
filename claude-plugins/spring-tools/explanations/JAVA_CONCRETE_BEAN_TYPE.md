## Explanations

This warning indicates that a `@Bean` method declares a return type that is broader (less precise) than the type of the object it actually returns. For example, a method declared as `Number bean()` that returns an `Integer`, or a method declared as `MyInterface myBean()` that returns `new MyImplementation()`.

This is a Spring AOT (Ahead-of-Time) best-practice check. The language server only runs it for projects on **Spring Boot 3.0 or later** (the version where Spring Framework 6.0 introduced the AOT engine used by GraalVM native images). It belongs to the "AOT Optimizations" validation category, which is **disabled by default** in the Spring Tools settings (`boot-java.validation.java.spring-aot`) and reports a warning when enabled.

The Spring Framework reference documentation explains why the declared type matters when AOT processing is used:

> While your application may interact with an interface that a bean implements, it is still very important to declare the most precise type. The AOT engine performs additional checks on the bean type, such as detecting the presence of `@Autowired` members or lifecycle callback methods.
>
> For `@Configuration` classes, make sure that the return type of an `@Bean` factory method is as precise as possible.

Using the documentation's own example, when a method is declared as `public MyInterface myInterface() { return new MyImplementation(); }`:

> In the example above, the declared type for the `myInterface` bean is `MyInterface`. During AOT processing, none of the usual post-processing will take `MyImplementation` into account. For instance, if there is an annotated handler method on `MyImplementation` that the context should register, it will not be detected during AOT processing.

The reason is that AOT processing works on bean *definitions* rather than bean *instances*: "As we cannot rely on the instance, make sure that the bean type is as precise as possible." Anything the AOT engine cannot see from the declared type — annotated methods, lifecycle callbacks, injection points on the concrete class — is silently left out of the generated code.

The language server detects this by inspecting the body of every method annotated (directly or via meta-annotation) with `@Bean` and collecting the static types of all `return` expressions. It then compares the declared return type against the collected types; the problem range covers the declared return type:

- If there is exactly one distinct return type and the declared type is not assignment-compatible with it (i.e. the declared type is a supertype of what is actually returned, or a boxed primitive is returned for a wider declared type), the problem is reported and a quick fix "Replace return type with '<ActualType>'" is offered. If several `@Bean` methods in the same file are affected, an additional "Ensure concrete bean type in file" fix applies all of them at once.
- If the method has several `return` statements whose types are not mutually assignment-compatible (for example returning both a `Double` and an `Integer` from a method declared as `Number`), the problem is still reported but no automatic fix is offered, because the language server cannot choose a single precise type for you.
- If the declared type already matches the returned type exactly (or is a subtype of every returned expression), nothing is reported.

Generic types are taken into account: a method declared as `Collection<Integer>` that returns a `LinkedList<Integer>` in one branch and `List.of(...)` in another is flagged with the fix "Replace return type with 'List<Integer>'", since `List<Integer>` is the most precise common type.

For more details, see:

- [Spring Framework Reference: Ahead of Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html) (section "Expose the Most Precise Bean Type" under "Best Practices")
- [Spring Framework API: @Bean](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Bean.html)
- [Spring Boot Reference: Introducing GraalVM Native Images](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html)

## Fixes

**Fix 1: Declare the concrete implementation type as the return type**

Change the declared return type of the `@Bean` method to the exact type that the method returns. Injection points elsewhere in the application can still use the interface (or any supertype), because Spring resolves candidates by assignability — only the bean *definition* needs the precise type. This is what the "Replace return type with '<ActualType>'" quick fix does.

*Before:*

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UserConfiguration {

	@Bean
	public MyInterface myInterface() {
		return new MyImplementation();
	}

}
```

*After:*

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UserConfiguration {

	@Bean
	public MyImplementation myInterface() {
		return new MyImplementation();
	}

}
```

**Fix 2: Use the most precise common type when returning collections or generic types**

When a method builds its result through different code paths, pick the most specific type that every `return` expression is assignable to. For collections this is usually the concrete collection interface with its type argument (e.g. `List<Integer>`) rather than a wider one such as `Collection<Integer>` or `Iterable<Integer>`.

*Before:*

```java
package com.example.demo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NumbersConfiguration {

	@Bean
	public Collection<Integer> numbers(boolean mutable) {
		if (mutable) {
			return new LinkedList<>();
		}
		return List.of(1, 2, 3);
	}

}
```

*After:*

```java
package com.example.demo;

import java.util.LinkedList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NumbersConfiguration {

	@Bean
	public List<Integer> numbers(boolean mutable) {
		if (mutable) {
			return new LinkedList<>();
		}
		return List.of(1, 2, 3);
	}

}
```

**Fix 3: Split a method that returns unrelated implementations into separate conditional beans**

If a single `@Bean` method returns different, mutually incompatible implementations depending on configuration, there is no single precise type to declare — and this is exactly the situation where the language server reports the problem without a quick fix. Because AOT works from a fixed set of bean definitions, the idiomatic solution is to move the decision into bean conditions: declare one `@Bean` method per implementation, each with a precise return type, and guard them with `@ConditionalOnProperty` (or another `@Conditional`).

Be aware of what this means at runtime: the Spring Framework reference states that "`Environment` properties that impact the presence of a bean (`@Conditional`) are only considered at build time", and the Spring Boot native-image documentation lists "Properties that change if a bean is created" (for example `@ConditionalOnProperty` and `.enabled` properties) among the things that are *not supported* in a native image. In other words, the implementation is selected once, when the AOT processing runs, and is then hard-coded into the generated code; changing the property when starting the native executable has no effect. Choose the property value for the build you are producing.

*Before:*

```java
package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfiguration {

	@Bean
	public StorageService storageService(@Value("${app.storage.type}") String type) {
		if ("s3".equals(type)) {
			return new S3StorageService();
		}
		return new LocalFileStorageService();
	}

}
```

*After:*

```java
package com.example.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfiguration {

	@Bean
	@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
	public S3StorageService s3StorageService() {
		return new S3StorageService();
	}

	@Bean
	@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
	public LocalFileStorageService localFileStorageService() {
		return new LocalFileStorageService();
	}

}
```

*Note: `@ConditionalOnProperty` is part of Spring Boot's auto-configuration support (`spring-boot-autoconfigure`). In a plain Spring Framework application without Spring Boot, use `@Conditional` with a custom `Condition` or `@Profile` instead; the same build-time evaluation applies.*

*Note: Injection points do not need to change after applying any of these fixes. A field or constructor parameter typed as `MyInterface` or `Collection<Integer>` is still satisfied by a bean whose definition declares `MyImplementation` or `List<Integer>`.*
