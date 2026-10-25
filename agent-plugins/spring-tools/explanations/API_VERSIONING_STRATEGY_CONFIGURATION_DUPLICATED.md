## Explanations
This warning indicates that the same API versioning strategy method is called more than once inside your `configureApiVersioning` method, for example two `useRequestHeader(...)` calls or two `usePathSegment(...)` calls.

API versioning is available as of Spring Framework 7.0 (Spring Boot 4.0) and is configured through `ApiVersionConfigurer`. Every `useRequestHeader`, `useQueryParam`, `useMediaTypeParameter`, `usePathSegment` or `useVersionResolver` call adds an `ApiVersionResolver` to the strategy. Repeating the same strategy is almost always unintentional: a copy-and-paste leftover, or two developers configuring the same thing in one method. In the best case it registers a redundant resolver; in the worst case the two calls disagree (e.g. `useRequestHeader("API-Version")` and `useRequestHeader("X-API-Version")`, or `usePathSegment(0)` and `usePathSegment(1)`), making it unclear which source of the version wins and hard to reason about how requests are matched. For the path segment strategy a second call cannot even be reached, because the path resolver never returns `null` and therefore never yields to the next resolver.

The language server applies this check when the project depends on `spring-web` 7.0.0 or newer. It inspects Java web configurations, i.e. `@Configuration` classes implementing `WebMvcConfigurer` or `WebFluxConfigurer` that override `configureApiVersioning`. Within that method body it groups the invocations of `usePathSegment`, `useQueryParam`, `useRequestHeader`, `useMediaTypeParameter` and `useVersionResolver` by method name. Whenever a name occurs more than once, every one of those invocations is flagged, so two duplicated `useRequestHeader` calls plus two duplicated `usePathSegment` calls produce four warnings. Different strategies used once each are not reported. There is no automatic quick fix.

For more details, see:
- [Spring MVC Config: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html)
- [Spring MVC: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html)
- [`ApiVersionConfigurer` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/ApiVersionConfigurer.html)

## Fixes
**Fix 1: Remove the duplicated call**
Keep a single call per strategy.

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
        configurer.useRequestHeader("API-Version");
    }
}
```

**Fix 2: Decide on one source per strategy when the calls disagree**
If the duplicated calls use different arguments, pick the one that matches what your clients actually send and delete the other. If you really need to accept the version from two different sources, prefer two *different* strategies (for example a request header and a query parameter), each of which yields to the next when its source is absent.

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
        configurer.useRequestHeader("X-API-Version");
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

*Note: for WebFlux, implement `org.springframework.web.reactive.config.WebFluxConfigurer` and use `org.springframework.web.reactive.config.ApiVersionConfigurer` in the same way. Do not combine `usePathSegment(...)` with other strategies; see `API_VERSIONING_VIA_PATH_SEGMENT_CONFIGURED_IN_COMBINATION`. See `API_VERSIONING_NOT_CONFIGURED` for enabling API versioning via properties instead of Java configuration.*
