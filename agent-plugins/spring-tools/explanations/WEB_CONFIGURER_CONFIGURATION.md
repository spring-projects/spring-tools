## Explanations
This warning indicates that a class implements one of Spring's web configurer callback interfaces, `WebMvcConfigurer` (`org.springframework.web.servlet.config.annotation`) for Spring MVC or `WebFluxConfigurer` (`org.springframework.web.reactive.config`) for Spring WebFlux, but is not annotated with `@Configuration`.

Implementing one of these interfaces is only half of the story. The interface itself does not make the class a Spring bean. The WebFlux reference documentation describes the mechanism: "`@EnableWebFlux` imports `DelegatingWebFluxConfiguration`" which "detects and delegates to `WebFluxConfigurer` implementations to customize that configuration"; Spring MVC works the same way with `DelegatingWebMvcConfiguration` and `WebMvcConfigurer`. Only configurer *beans* found in the application context are consulted. A class that merely implements the interface without being registered as a bean is never called, so interceptors, formatters, CORS mappings, resource handlers or view controllers declared in it are silently ignored. The reference documentation consistently shows configurers as `@Configuration` classes: "In Java configuration, you can implement the `WebMvcConfigurer` interface" with the example `@Configuration public class WebConfiguration implements WebMvcConfigurer`, and for the reactive stack `@Configuration public class WebConfig implements WebFluxConfigurer`.

In a Spring Boot application `@Configuration` is also the way to *extend* the auto-configured web setup rather than replace it: "If you want to keep those Spring Boot MVC customizations and make more MVC customizations ..., you can add your own `@Configuration` class of type `WebMvcConfigurer` but without `@EnableWebMvc`." The same applies to WebFlux ("add your own `@Configuration` class of type `WebFluxConfigurer` but without `@EnableWebFlux`").

The language server raises this diagnostic for a concrete class (not an interface, not an abstract class, and not a non-static inner class) whose type hierarchy contains `WebMvcConfigurer` or `WebFluxConfigurer`, when the class carries neither `@Configuration` nor an annotation that is meta-annotated with `@Configuration`. The check does not depend on a particular Spring Boot version. It is applied regardless of whether the class is annotated with another stereotype such as `@Component`: while `@Component` would also register the bean, `@Configuration` is the documented and intended stereotype for configuration classes, and it expresses that the class contributes container configuration rather than application logic (see also `MISSING_CONFIGURATION_ANNOTATION`).

A quick fix ("Add missing '@Configuration' annotation in file") adds the annotation and its import to the class declarations in the file; review the result when the file contains more than one top-level or nested class.

For more details, see:
- [Spring Framework: MVC Config API](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/customize.html) (implementing `WebMvcConfigurer`)
- [Spring Framework: WebFlux Config](https://docs.spring.io/spring-framework/reference/web/webflux/config.html) (implementing `WebFluxConfigurer`, and the note about Spring Boot)
- [Spring Boot: Servlet Web Applications, "Spring MVC Auto-configuration"](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- [Spring Boot: Reactive Web Applications, "Spring WebFlux Auto-configuration"](https://docs.spring.io/spring-boot/reference/web/reactive.html)

## Fixes
**Fix 1: Annotate the configurer class with `@Configuration`**
Add `@Configuration` (`org.springframework.context.annotation.Configuration`) to the class. In a Spring Boot application do *not* add `@EnableWebMvc` or `@EnableWebFlux` alongside it unless you deliberately want to switch off Boot's web auto-configuration. This is what the quick fix does.

*Before:*
```java
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("https://example.com");
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("https://example.com");
    }
}
```

*Before (WebFlux):*
```java
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

public class ReactiveWebConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/assets/");
    }
}
```

*After (WebFlux):*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class ReactiveWebConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/assets/");
    }
}
```

*Note: the class only needs to be visible to component scanning, i.e. it has to live in (a sub-package of) the package of your `@SpringBootApplication` class, or be imported explicitly with `@Import`.*

**Fix 2: Expose the configurer as a `@Bean` inside an existing configuration class**
If you prefer not to turn the implementing class into a configuration class of its own (for example when the configurer is tiny), declare it as a bean from an existing `@Configuration` class instead. The Spring Boot reference documentation uses this style for a global CORS configuration. Implement the interface as an anonymous class inside the `@Bean` method (as in the example); a named class, whether top-level or static nested, keeps being reported unless it carries `@Configuration`.

*Before:*
```java
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**");
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MyCorsConfiguration {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**");
            }
        };
    }
}
```

*Note: multiple `WebMvcConfigurer` (or `WebFluxConfigurer`) beans may coexist; all of them are called. Splitting configuration across several small `@Configuration` classes is therefore fine.*
