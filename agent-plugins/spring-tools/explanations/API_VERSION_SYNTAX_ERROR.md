## Explanations
This warning indicates that an API version declared in your code, such as `@GetMapping(version = "a.b.c")` or `RequestPredicates.version("1.1++")`, cannot be parsed by Spring's default `SemanticApiVersionParser`.

API versioning is available as of Spring Framework 7.0 (Spring Boot 4.0). Unless you configure a custom `ApiVersionParser`, the version strings declared in request mappings are parsed with `SemanticApiVersionParser`, which understands `major`, `minor` and `patch` integer values, e.g. `"1"`, `"1.0"`, `"1.2"`, `"1.2.0"` or `"1.2.3"` (missing minor and patch values default to `0`, and leading non-integer characters such as the `v` in `"v1.0"` are skipped). In addition, the `version` attribute of `@RequestMapping` supports a baseline notation with a trailing `+` (for example `"1.2+"`, meaning the given version and all supported versions above it). A value that does not follow these rules is not a valid mapping and will fail when Spring parses the declared versions.

The language server applies this check when the project depends on `spring-web` 7.0.0 or newer **and** API versioning is configured in the project, either in Java (a `@Configuration` implementing `WebMvcConfigurer`/`WebFluxConfigurer` that overrides `configureApiVersioning`) or via `spring.mvc.apiversion.*` / `spring.webflux.apiversion.*` properties. If any web configuration installs a parser other than `SemanticApiVersionParser` via `setVersionParser(...)`, the check is skipped entirely, because the language server cannot know which format the custom parser accepts. It inspects:
- the `version` attribute of `@RequestMapping` and of every annotation meta-annotated with it (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, and your own composed annotations), and
- calls to `version(...)` declared on `RequestPredicates` (Web MVC or WebFlux functional endpoints).

For each string literal it strips a single trailing `+` (the baseline marker) and then parses the remainder with `SemanticApiVersionParser`. If parsing fails, the literal is flagged. There is no automatic quick fix; the value has to be corrected by hand.

For more details, see:
- [Spring MVC: Mapping Requests - API Version](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html) (fixed and baseline `"1.2+"` versions)
- [Spring MVC: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html) (see the linked equivalent for the reactive stack)
- [`SemanticApiVersionParser` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/accept/SemanticApiVersionParser.html)
- [`ApiVersionConfigurer` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/config/annotation/ApiVersionConfigurer.html) (`setVersionParser`)

## Fixes
**Fix 1: Use a semantic version in the mapping annotation**
Replace the invalid value with a `major[.minor[.patch]]` version, optionally followed by a single `+` for a baseline match.

*Before:*
```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account/{id}")
public class AccountController {

    @GetMapping(version = "a.b.c")
    public Account getAccount() {
        return new Account();
    }

    @GetMapping(version = "1.1++")
    public Account getAccountLatest() {
        return new Account();
    }
}
```

*After:*
```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account/{id}")
public class AccountController {

    @GetMapping(version = "1.0")
    public Account getAccount() {
        return new Account();
    }

    @GetMapping(version = "1.1+")
    public Account getAccountLatest() {
        return new Account();
    }
}
```

**Fix 2: Use a semantic version in a functional endpoint**
The same rule applies to `RequestPredicates.version(...)` in router functions.

*Before:*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class AccountRoutes {

    @Bean
    RouterFunction<ServerResponse> accountRoutes() {
        return RouterFunctions.route()
                .GET("/account/{id}", RequestPredicates.version("latest"),
                        request -> ServerResponse.ok().build())
                .build();
    }
}
```

*After:*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class AccountRoutes {

    @Bean
    RouterFunction<ServerResponse> accountRoutes() {
        return RouterFunctions.route()
                .GET("/account/{id}", RequestPredicates.version("1.2+"),
                        request -> ServerResponse.ok().build())
                .build();
    }
}
```

*Note: for WebFlux, use the equivalent types from `org.springframework.web.reactive.function.server`.*

**Fix 3: Configure a custom `ApiVersionParser` if your versions are intentionally not semantic**
If your API uses a non-semantic scheme (for example date-based versions), register your own `ApiVersionParser` implementation with `setVersionParser(...)` in `configureApiVersioning`. The language server then stops validating the declared versions against the semantic format.

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
        configurer.setVersionParser(new MyApiVersionParser());
    }
}
```

*Note: `MyApiVersionParser` stands for your own implementation of `org.springframework.web.accept.ApiVersionParser`. Only do this when you really need a different version format; the default semantic parser is what clients and the Spring documentation expect. See `API_VERSIONING_NOT_CONFIGURED` for how to enable API versioning in the first place.*
