## Explanations

This warning appears on a concrete class (not an interface and not abstract) that implements `org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor` or `org.springframework.beans.factory.aot.BeanRegistrationAotProcessor` — directly or through a superclass or superinterface — but is not registered as a Spring bean anywhere in the project.

This is a Spring AOT (Ahead-of-Time) check. The language server only runs it for projects on **Spring Boot 3.0 or later**, where Spring Framework 6.0 introduced these two AOT processor interfaces. It belongs to the "AOT Optimizations" validation category, which is **disabled by default** in the Spring Tools settings (`boot-java.validation.java.spring-aot`) and reports a warning when enabled.

Both interfaces are the extension points through which application or library code participates in Spring's AOT processing. The Spring Framework reference documentation describes two ways to make them known to the AOT engine:

> A `BeanFactoryInitializationAotProcessor` implementation can be registered in `META-INF/spring/aot.factories` with a key equal to the fully-qualified name of the interface.
>
> The `BeanFactoryInitializationAotProcessor` interface can also be implemented directly by a bean. In this mode, the bean provides an AOT contribution equivalent to the feature it provides with a regular runtime. Consequently, such a bean is automatically excluded from the AOT-optimized context.

and for `BeanRegistrationAotProcessor`:

> - Implemented by a `BeanPostProcessor` bean, to replace its runtime behavior. [...]
> - Implemented by a type registered in `META-INF/spring/aot.factories` with a key equal to the fully-qualified name of the interface.

A class that implements one of these interfaces but is neither a bean nor listed in `aot.factories` is never invoked: the AOT engine has no way of discovering it, so the contribution it was written to provide (generated code, runtime hints, and so on) is simply missing from the AOT output. Because the class compiles and nothing fails at build time, this is easy to miss until the native image or the `spring.aot.enabled=true` application misbehaves at runtime.

The language server decides this by walking the type hierarchy of every concrete type declaration in the file and checking whether it includes either AOT processor interface. For each such type it consults the Spring index of the project for beans of that type; if none is found, the problem is reported on the class name. When the project contains `@Configuration` classes, a quick fix is offered for each combination of configuration class and declared constructor of the flagged type (visibility is not checked), labelled "Define bean in config '<configBeanName>' with constructor (<parameterTypes>)". The fix adds a `@Bean` method to the chosen configuration class that instantiates the type and passes its constructor parameters through as method parameters. If the project has no `@Configuration` classes, the problem is still reported but no automatic fix is available.

Note that the language server checks for **bean** registrations only. It does not inspect `META-INF/spring/aot.factories`, so a processor that is correctly registered through that file (and is intentionally not a bean) is still reported. In that case the warning can be treated as a false positive for the class in question.

For more details, see:

- [Spring Framework Reference: Ahead of Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html) (sections "Bean Factory Initialization AOT Contributions" and "Bean Registration AOT Contributions")
- [Spring Framework API: BeanFactoryInitializationAotProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/aot/BeanFactoryInitializationAotProcessor.html)
- [Spring Framework API: BeanRegistrationAotProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/aot/BeanRegistrationAotProcessor.html)

## Fixes

**Fix 1: Declare the processor as a `static` `@Bean` in a `@Configuration` class**

Register the class as a bean so that Spring can discover it during the AOT refresh. This mirrors what the quick fix generates, with one recommended addition: make the `@Bean` method `static`. Both Javadocs warn that "Using this interface on a registered bean will cause the bean and all of its dependencies to be initialized during AOT processing" and advise: "If such a bean is registered using a factory method, make sure to make it `static` so that its enclosing class does not have to be initialized." Keep the processor's own dependencies minimal for the same reason.

*Before:*

```java
package com.example.demo;

import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class ResourceHintsAotProcessor implements BeanFactoryInitializationAotProcessor {

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (generationContext, beanFactoryInitializationCode) -> generationContext.getRuntimeHints()
				.resources().registerPattern("templates/*.txt");
	}

}
```

*After:*

```java
package com.example.demo;

import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ResourceHintsAotProcessor implements BeanFactoryInitializationAotProcessor {

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (generationContext, beanFactoryInitializationCode) -> generationContext.getRuntimeHints()
				.resources().registerPattern("templates/*.txt");
	}

}

@Configuration(proxyBeanMethods = false)
class AotProcessorConfiguration {

	@Bean
	static ResourceHintsAotProcessor resourceHintsAotProcessor() {
		return new ResourceHintsAotProcessor();
	}

}
```

**Fix 2: Register the processor in `META-INF/spring/aot.factories` instead of as a bean**

If the processor is infrastructure that should not live in the application context at all — the typical choice for library code — list its fully-qualified class name in `META-INF/spring/aot.factories` under a key equal to the fully-qualified name of the interface it implements. The class does not have to be a bean in this case. As explained above, the language server does not read `aot.factories`, so the warning remains visible for this class; the registration itself is nevertheless correct and complete.

*Before:*

```java
package com.example.demo;

import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;

public class SerializableBeansAotProcessor implements BeanRegistrationAotProcessor {

	@Override
	public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
		if (java.io.Serializable.class.isAssignableFrom(registeredBean.getBeanClass())) {
			return (generationContext, beanRegistrationCode) -> generationContext.getRuntimeHints()
					.serialization().registerType(registeredBean.getBeanClass().asSubclass(java.io.Serializable.class));
		}
		return null;
	}

}
```

*After:*

```java
package com.example.demo;

import java.io.Serializable;

import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;

// Registered in src/main/resources/META-INF/spring/aot.factories:
//
// org.springframework.beans.factory.aot.BeanRegistrationAotProcessor=\
// com.example.demo.SerializableBeansAotProcessor
public class SerializableBeansAotProcessor implements BeanRegistrationAotProcessor {

	@Override
	public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
		if (Serializable.class.isAssignableFrom(registeredBean.getBeanClass())) {
			return (generationContext, beanRegistrationCode) -> generationContext.getRuntimeHints()
					.serialization().registerType(registeredBean.getBeanClass().asSubclass(Serializable.class));
		}
		return null;
	}

}
```

*Note: A `BeanRegistrationAotProcessor` that is registered as a bean is excluded from the AOT-optimized context by default (`isBeanExcludedFromAotProcessing()` returns `true`). If the class is also a `BeanPostProcessor` whose runtime behaviour must be preserved, see `JAVA_BEAN_POST_PROCESSOR_IGNORED_IN_AOT`.*
