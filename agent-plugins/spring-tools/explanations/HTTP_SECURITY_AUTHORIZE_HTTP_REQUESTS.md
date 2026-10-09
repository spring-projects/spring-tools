## Explanations
This warning indicates the usage of `HttpSecurity.authorizeRequests(...)`, which is deprecated (since Spring Security 5.8) and has been removed in Spring Security 7.0. It should be replaced by `HttpSecurity.authorizeHttpRequests(...)`, which is available since Spring Security 5.5.

The older `authorizeRequests()` method relies on the legacy `FilterSecurityInterceptor` with metadata sources, config attributes, `AccessDecisionManager` and voters. The newer `authorizeHttpRequests()` method installs the `AuthorizationFilter` and uses the simplified `AuthorizationManager` API, which makes custom authorization logic easier to reuse, delays the `Authentication` lookup until an authorization decision actually needs it (requests that are always permitted or denied never touch the session), and supports bean-based configuration. Migrating to the new method is required for compatibility with Spring Security 7.

For more details, see the official Spring Security documentation:
- [Migrating from authorizeRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html#_migrating_from_authorizerequests) (including the "Migrating Expressions" subsection for SpEL-based rules)
- [Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) (current `requestMatchers` / `authorizeHttpRequests` examples)
- [Preparing for Spring Security 7: Configuration Migrations](https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html) (the Lambda DSL is mandatory in 7.0)
- [`HttpSecurity.authorizeHttpRequests` API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/builders/HttpSecurity.html#authorizeHttpRequests(org.springframework.security.config.Customizer))
- [`AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry` API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/config/annotation/web/configurers/AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.html) (This class defines the methods available inside the lambda, such as `requestMatchers`, `dispatcherTypeMatchers`, `permitAll`, `denyAll`, `authenticated`, `hasRole`, `hasAuthority`, `access`, etc.)

## Fixes
**Fix 1: Replace `authorizeRequests` with `authorizeHttpRequests`**
Change the method call from `.authorizeRequests()` to `.authorizeHttpRequests()`. This applies to both the chained configuration style and the Lambda DSL style. The quick fix renames the call (no-arg and `Customizer` variant alike), switches the related registry types (`ExpressionUrlAuthorizationConfigurer` / `ExpressionInterceptUrlRegistry` → `AuthorizeHttpRequestsConfigurer` / `AuthorizationManagerRequestMatcherRegistry`) and leaves the rules themselves untouched.

The examples use the Spring Security 6 API (`SecurityFilterChain` bean, `requestMatchers`), which is what a project that still calls `authorizeRequests()` typically looks like today. The hint is reported for Spring Security 5.6 and newer; `authorizeRequests` no longer exists in 7.0.

*Before (Chained configuration):*
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeRequests()
            .requestMatchers("/blog/**").permitAll()
            .anyRequest().authenticated();
    return http.build();
}
```

*After (Chained configuration):*
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests()
            .requestMatchers("/blog/**").permitAll()
            .anyRequest().authenticated();
    return http.build();
}
```

*Before (Lambda DSL configuration):*
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeRequests(requests -> requests
            .requestMatchers("/blog/**").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
}
```

*After (Lambda DSL configuration):*
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/blog/**").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
}
```

**Legacy request matchers:** if the rules still use `antMatchers(...)`, `mvcMatchers(...)` or `regexMatchers(...)` (deprecated in Spring Security 5.8, removed in 6.0), migrate them to `requestMatchers(...)` as part of this fix; likewise `web.ignoring().antMatchers(...)` → `requestMatchers(...)` and `csrf.ignoringAntMatchers(...)` → `ignoringRequestMatchers(...)`. `requestMatchers` picks the most appropriate `RequestMatcher` for the application; for an explicit regular expression use `requestMatchers(RegexRequestMatcher.regexMatcher("..."))`. The quick fix itself does not touch the matcher calls.

**Spring Security 7 (Spring Boot 4):** the chained style without lambdas (`.authorizeHttpRequests().requestMatchers(...)...`) is not valid anymore in 7.0, so convert to the Lambda DSL in the same step (see `JAVA_LAMBDA_DSL`). The result then matches the current documentation:
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authorize) -> authorize
                .requestMatchers("/blog/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

**Expression-based rules:** SpEL string rules such as `.access("hasRole('ADMIN') and hasRole('USER')")` exist only on the old registry. On `authorizeHttpRequests` prefer the type-safe methods (`hasRole`, `hasAnyAuthority`, `access(AuthorizationManager)`); to keep an existing expression, wrap it: `.access(new WebExpressionAuthorizationManager("hasRole('ADMIN') && hasRole('USER')"))` (see "Migrating Expressions" in the linked documentation).

**Note on `accessDecisionManager`:**
If your configuration uses a custom `.accessDecisionManager(...)`, be aware that this method does not exist in the new `authorizeHttpRequests()` API. The quick fix will remove the `.accessDecisionManager(...)` call and insert a `TODO` comment. You will need to manually migrate your custom decision manager logic to use the new `AuthorizationManager` API via the `.access(...)` method on specific request matchers (for example `.anyRequest().access(myAuthorizationManager)`, where `myAuthorizationManager` implements `AuthorizationManager<RequestAuthorizationContext>`).

*Before (with `accessDecisionManager`):*
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeRequests()
            .accessDecisionManager(myAccessDecisionManager)
            .requestMatchers("/blog/**").permitAll()
            .anyRequest().authenticated();
    return http.build();
}
```

*After (with `accessDecisionManager` removed and a TODO added):*
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests()
            /*TODO: replace removed '.accessDecisionManager(myAccessDecisionManager);' with appropriate call to 'access(AuthorizationManager)' after requestMatchers(...) call etc.*/
            .requestMatchers("/blog/**").permitAll()
            .anyRequest().authenticated();
    return http.build();
}
```

*Note (Spring Security 5.7 and earlier, i.e. Spring Boot 2.x): the rename is applied exactly the same way, but this is the only case where `antMatchers(...)`/`mvcMatchers(...)`/`regexMatchers(...)` and `WebSecurityConfigurerAdapter.configure(HttpSecurity)` have to stay - `requestMatchers(String...)` only exists since 5.8 and the `SecurityFilterChain` bean since 5.4. Migrate them together with the Spring Security upgrade (see the linked migration guides).*