## Explanations
This warning indicates that your `configureApiVersioning` method configures the path segment strategy with `usePathSegment(...)` **and** at least one other versioning strategy (`useRequestHeader`, `useQueryParam`, `useMediaTypeParameter` or `useVersionResolver`) at the same time.

API versioning is available as of Spring Framework 7.0 (Spring Boot 4.0). Each `use...` call on `ApiVersionConfigurer` adds an `ApiVersionResolver`. For most strategies a resolver returns `null` when the request carries no version, so Spring can move on and consult the next configured resolver. The path segment resolver is different: it selects the version from a path segment at a fixed index and, if that segment is missing, raises `InvalidApiVersionException` rather than returning `null`. The `ApiVersionConfigurer` documentation states that this resolver "never returns null, and therefore cannot yield to other resolvers". Combining it with header, query parameter or media type parameter resolvers is therefore misleading: either the path segment resolver resolves the version, or the request fails, and the additional strategies are never given a chance.

The language server applies this check when the project depends on `spring-web` 7.0.0 or newer. It inspects Java web configurations only, i.e. `@Configuration` classes implementing `WebMvcConfigurer` or `WebFluxConfigurer` that override `configureApiVersioning`. Inside that method body it looks for invocations of `usePathSegment`, `useQueryParam`, `useRequestHeader`, `useMediaTypeParameter` and `useVersionResolver`. If `usePathSegment` is invoked together with any of the other four, the `usePathSegment(...)` invocation is flagged. Calling `usePathSegment` alone, or several non-path strategies without `usePathSegment`, is not reported. There is no automatic quick fix.

Spring Framework 7.0.6 added a `usePathSegment(int, Predicate<RequestPath>)` variant whose predicate lets the resolver skip requests and return `null`, which does allow combination with other resolvers. The language server does not distinguish this overload and reports it as well; if you use it intentionally, you can ignore the warning for that configuration.

For more details, see:
- [Spring MVC: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html) (behavior of the path resolver)
- [Spring MVC Config: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html)
- [`ApiVersionConfigurer` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/ApiVersionConfigurer.html)

## Fixes
**Fix 1: Keep only the path segment strategy**
If the version is part of the URL, remove the other strategies. The path segment index is zero-based: use `0` for `/{version}/...` and `1` for `/api/{version}/...`. Remember that the version segment must be declared as a URI variable in your request mappings (for example `@RequestMapping("/api/{version}/account")`).

*Before:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(1);
        configurer.useRequestHeader("API-Version");
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(1);
    }
}
```

**Fix 2: Drop the path segment strategy and keep the others**
If clients send the version via a header, query parameter or media type parameter, remove `usePathSegment(...)`. Multiple non-path strategies can be combined, because each of them returns `null` when its source is absent and lets the next resolver try.

*Before:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version");
        configurer.useQueryParam("api-version");
        configurer.usePathSegment(1);
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version");
        configurer.useQueryParam("api-version");
    }
}
```

*Note: for WebFlux, implement `org.springframework.web.reactive.config.WebFluxConfigurer` and use `org.springframework.web.reactive.config.ApiVersionConfigurer` in the same way. When configuring versioning through Spring Boot properties instead of Java, see `API_VERSIONING_NOT_CONFIGURED`. A related check, `API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED`, reports the same strategy being configured more than once.*
