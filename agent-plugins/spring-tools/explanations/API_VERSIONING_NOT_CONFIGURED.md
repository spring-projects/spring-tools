## Explanations
This warning indicates that you are using the `version` attribute in a Spring mapping annotation (such as `@RequestMapping(path = "/api", version = "1")`), but you have not configured how Spring should resolve the API version from the incoming HTTP request.

Spring needs to know whether the client will send the version in a request header, a query parameter, a URI path segment, or a media type parameter. If this is not configured globally, the versioning condition will not work correctly. API versioning is available as of Spring Framework 7.0 (Spring Boot 4.0).

For more details, see the official documentation:
- [Spring MVC: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html) and the [MVC Config: API Version](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html) section (see the linked equivalents for the reactive stack)
- [Spring Boot: Servlet Web Applications - API Versioning](https://docs.spring.io/spring-boot/reference/web/servlet.html) (the `spring.mvc.apiversion.*` / `spring.webflux.apiversion.*` properties)
- [`ApiVersionConfigurer` API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/ApiVersionConfigurer.html) (the methods available inside `configureApiVersioning`)

## Fixes
Before applying a fix, **you must determine if the project uses Spring Web MVC or Spring WebFlux** by checking the project's dependencies (e.g., `spring-boot-starter-web` vs `spring-boot-starter-webflux`) or existing imports in the codebase.

You can fix this by configuring API versioning either through a Java configuration bean or via application properties.

### Fix 1: Configure via Java Bean
If you already have a `@Configuration` class that implements the appropriate configurer, add the `configureApiVersioning` method to it. If not, create a new configuration class.

**For Spring Web MVC:**
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        // Choose ONE of the following resolvers:
        configurer.useRequestHeader("X-API-Version");
        // configurer.useQueryParam("version");
        // configurer.usePathSegment(1); // 0-based index of the version segment, e.g. 1 for "/api/{version}/..."
        // configurer.useMediaTypeParameter(MediaType.APPLICATION_JSON, "version");
    }
}
```

**For Spring WebFlux:**
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.ApiVersionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxConfig implements WebFluxConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        // Choose ONE of the following resolvers:
        configurer.useRequestHeader("X-API-Version");
        // configurer.useQueryParam("version");
        // configurer.usePathSegment(1); // 0-based index of the version segment, e.g. 1 for "/api/{version}/..."
        // configurer.useMediaTypeParameter(MediaType.APPLICATION_JSON, "version");
    }
}
```

*Note: by default a version is required once versioning is enabled (a request without one is rejected with a 400 response, `MissingApiVersionException`). Use `configurer.setDefaultVersion("1.0.0")` to fall back to a default instead (this automatically makes the version optional), `setVersionRequired(false)` to accept any version when none is sent, and `addSupportedVersions(...)` for versions that do not appear in any mapping. For `usePathSegment`, the segment must be declared as a URI variable in the mappings (e.g. `/api/{version}/...`); this resolver never resolves to `null` and therefore cannot yield to other resolvers.*

### Fix 2: Configure via Application Properties / YAML
Alternatively, you can configure the versioning strategy directly in your `application.properties` or `application.yml` file. Choose the prefix based on your web stack (`spring.mvc` vs `spring.webflux`).

**Available `use.*` properties (choose ONE):**
- `header` - header name (e.g., `X-API-Version`)
- `query-parameter` - query parameter name (e.g., `version`)
- `path-segment` - 0-based index of the path segment holding the version (e.g., `1` for `/api/{version}/...`); this is an integer, not a boolean
- `media-type-parameter.<media-type>` - a map from media type to the parameter name (e.g., `media-type-parameter[application/json]=version`)

Related properties: `default` (version assumed when the request has none), `required`, `supported` and `detect-supported`.

*For `application.properties`:*
```properties
# For Web MVC:
spring.mvc.apiversion.use.header=X-API-Version
# spring.mvc.apiversion.use.query-parameter=version
# spring.mvc.apiversion.use.path-segment=1
# spring.mvc.apiversion.use.media-type-parameter[application/json]=version
# spring.mvc.apiversion.default=1.0.0

# For WebFlux:
spring.webflux.apiversion.use.header=X-API-Version
# spring.webflux.apiversion.use.query-parameter=version
# spring.webflux.apiversion.use.path-segment=1
# spring.webflux.apiversion.use.media-type-parameter[application/json]=version
# spring.webflux.apiversion.default=1.0.0
```

*For `application.yml`:*
```yaml
spring:
  mvc: # Use 'webflux' instead of 'mvc' for Spring WebFlux projects
    apiversion:
      # default: 1.0.0
      use:
        header: X-API-Version
        # query-parameter: version
        # path-segment: 1
        # media-type-parameter:
        #   "[application/json]": version
```

*Note: if you need more than one resolver (e.g. header and query parameter), configure them programmatically via `configureApiVersioning` so their order is explicit.*