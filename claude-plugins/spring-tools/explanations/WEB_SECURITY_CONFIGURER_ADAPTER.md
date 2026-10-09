## Explanations
This warning indicates that a class extends `org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter`. The adapter was deprecated in Spring Security 5.7 (5.7.0-M2) and removed in Spring Security 6.0, together with its `configure(HttpSecurity)`, `configure(WebSecurity)` and `configure(AuthenticationManagerBuilder)` callbacks. Spring Boot 3.0 upgrades to Spring Security 6.0, so any project that still relies on the adapter cannot move to Spring Boot 3 until the class is refactored.

The replacement is a component-based configuration where each callback becomes a `@Bean` in a plain `@Configuration` class: `configure(HttpSecurity)` becomes a `SecurityFilterChain` bean (available since Spring Security 5.4), `configure(WebSecurity)` becomes a `WebSecurityCustomizer` bean (also since 5.4), and `configure(AuthenticationManagerBuilder)` is replaced by publishing `UserDetailsService`, `AuthenticationManager` or `AuthenticationProvider` beans (or by `HttpSecurity#authenticationManager`, since 5.6).

The language server reports this warning for projects whose `spring-security-config` dependency is at version 5.7.0 or newer and older than 6.1.0, i.e. the versions in which the adapter is either deprecated (5.7.x, 5.8.x) or already gone (6.0.x, where the code no longer compiles). The marker is placed on the superclass reference in the `extends` clause; the check recognizes the fully qualified name as well as classes imported explicitly or through a wildcard import. A quick fix is offered when the class is also annotated with `@Configuration`, directly or through a meta-annotation such as `@SpringBootApplication`. The quick fix drops the `extends` clause and the import, converts `configure(HttpSecurity)` into a `SecurityFilterChain` bean that returns `http.build()`, converts `configure(WebSecurity)` into a `WebSecurityCustomizer` bean, and converts in-memory `configure(AuthenticationManagerBuilder)` setups into an `InMemoryUserDetailsManager` bean (carrying only the user names, see Fix 3). Classes that use JDBC or LDAP authentication, or that expose `authenticationManagerBean()` / `userDetailsServiceBean()`, are left untouched and must be migrated by hand as described in Fix 3 and Fix 4. The quick fix is available for the current file and for the whole project.

For more details, see the official Spring Security documentation:
- [Spring Security without the WebSecurityConfigurerAdapter](https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter) (the canonical migration guide with before/after examples for `HttpSecurity`, `WebSecurity`, in-memory, JDBC and LDAP authentication)
- [Spring Security 5.8 migration guide: Configuration](https://raw.githubusercontent.com/spring-projects/spring-security/5.8.x/docs/modules/ROOT/pages/migration/servlet/config.adoc) (confirms the removal in 6.0, the `requestMatchers` replacement for `antMatchers`/`mvcMatchers`/`regexMatchers`, and the need to add `@Configuration` next to `@EnableWebSecurity`)
- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide) (Spring Boot 3.0 uses Spring Security 6.0; upgrading to Spring Security 5.8 first is recommended)
- [Spring Boot 2.7 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.7-Release-Notes) ("Migrating From WebSecurityConfigurerAdapter to SecurityFilterChain": `@WebMvcTest` slices may need to `@Import` the security configuration)
- [Preparing for Spring Security 7: Configuration Migrations](https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html) (the Lambda DSL used in the examples below becomes mandatory in 7.0)

## Fixes
**Fix 1: Replace `configure(HttpSecurity)` with a `SecurityFilterChain` bean**
Remove the `extends WebSecurityConfigurerAdapter` clause and turn the `configure(HttpSecurity)` override into a `@Bean` method that receives the `HttpSecurity` builder and returns `http.build()`. This is exactly what the quick fix produces. While doing so, prefer the Lambda DSL (see `JAVA_LAMBDA_DSL`), `authorizeHttpRequests` instead of `authorizeRequests` (see `HTTP_SECURITY_AUTHORIZE_HTTP_REQUESTS`), and `requestMatchers(...)` instead of `antMatchers`/`mvcMatchers`/`regexMatchers`, which were deprecated in Spring Security 5.8 and removed in 6.0.

Note that Spring Security 6.0 no longer meta-annotates `@EnableWebSecurity` with `@Configuration`, so the class must carry `@Configuration` itself (see `MISSING_CONFIGURATION_ANNOTATION`).

*Before (extending the adapter):*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .and()
            .httpBasic();
    }
}
```

*After (`SecurityFilterChain` bean):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

**Fix 2: Replace `configure(WebSecurity)` with a `WebSecurityCustomizer` bean**
The `configure(WebSecurity)` override, typically used to ignore static resources, becomes a `@Bean` of type `WebSecurityCustomizer` that returns a lambda operating on the `WebSecurity` builder. The quick fix moves the entire body of the old method into that lambda (`return (web) -> { ... };`) and names the bean method `webSecurityCustomizer`; the single-statement form below is the equivalent hand-written result. Note that ignoring requests removes them from the security filter chain entirely; the Spring Security team recommends `permitAll` inside `authorizeHttpRequests` for endpoints that should still receive security headers and other protections.

*Before (extending the adapter):*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    public void configure(WebSecurity web) {
        web.ignoring().antMatchers("/ignore1", "/ignore2");
    }
}
```

*After (`WebSecurityCustomizer` bean):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/ignore1", "/ignore2");
    }
}
```

**Fix 3: Replace `configure(AuthenticationManagerBuilder)` with a `UserDetailsService` bean**
Instead of configuring users on the `AuthenticationManagerBuilder`, publish the resulting `UserDetailsService` as a bean. For in-memory authentication this is an `InMemoryUserDetailsManager` (the quick fix generates this bean, named `inMemoryAuthManager`, when it recognizes `auth.inMemoryAuthentication().withUser(...)` calls — but it carries only the user names: re-add `password(...)`, `roles(...)` and a password encoder by hand). For JDBC authentication expose a `JdbcUserDetailsManager` built from the `DataSource`; for LDAP authentication expose an `AuthenticationManager` created by `LdapBindAuthenticationManagerFactory` (available since Spring Security 5.7) — see the linked blog post for complete JDBC and LDAP examples. `User.withDefaultPasswordEncoder()` is meant for demos only and should not be used in production.

*Before (extending the adapter):*
```java
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .inMemoryAuthentication()
                .withUser("user")
                .password("{noop}password")
                .roles("USER");
    }
}
```

*After (`InMemoryUserDetailsManager` bean):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.builder()
            .username("user")
            .password("{noop}password")
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(user);
    }
}
```

*After (`JdbcUserDetailsManager` bean, when the adapter used `auth.jdbcAuthentication()`):*
```java
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public UserDetailsManager users(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }
}
```

**Fix 4: Replace `authenticationManagerBean()` overrides with an explicit `AuthenticationManager`**
Classes that override `authenticationManagerBean()` to expose the `AuthenticationManager` (typically to hand it to a custom filter) can no longer do so, and the quick fix does not transform them. There are two verified alternatives: for an application-wide manager, register your own `AuthenticationManager` as a `@Bean` (as shown for LDAP in the linked blog post); for a manager scoped to one filter chain, set it on the builder via `http.authenticationManager(...)` (since Spring Security 5.6). A custom filter that needs the chain-local manager can obtain it through `http.getSharedObject(AuthenticationManager.class)` inside a custom DSL (`AbstractHttpConfigurer`), which is how Spring Security's own configurers are implemented.

*Before (extending the adapter):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.addFilter(new CustomFilter(authenticationManagerBean())); // CustomFilter is your own filter class
    }
}
```

*After (custom DSL reading the chain-local `AuthenticationManager`):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.with(new CustomFilterDsl(), Customizer.withDefaults());
        return http.build();
    }

    static class CustomFilterDsl extends AbstractHttpConfigurer<CustomFilterDsl, HttpSecurity> {

        @Override
        public void configure(HttpSecurity http) {
            AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
            http.addFilter(new CustomFilter(authenticationManager));
        }
    }
}
```

*Note: `HttpSecurity.with(...)` exists since Spring Security 6.2. On 6.0 and 6.1 use `http.apply(new CustomFilterDsl())` instead; `apply` is deprecated since 6.2 and removed in 7.0 (see `JAVA_LAMBDA_DSL`).*

*After (application-wide `AuthenticationManager` bean, then used via `http.authenticationManager(...)`):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public AuthenticationManager authenticationManager() {
        return new CustomAuthenticationManager(); // your own AuthenticationManager implementation
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
            .authenticationManager(authenticationManager)
            .addFilter(new CustomFilter(authenticationManager));
        return http.build();
    }
}
```

**Note on nested adapter classes:** A `WebSecurityConfigurerAdapter` declared as a static nested class inside an outer `@Configuration` (a common pattern for multiple filter chains ordered with `@Order`) is flattened by the quick fix: its `configure(HttpSecurity)` method becomes a `SecurityFilterChain` bean of the outer class, named after the nested class (for example `ApiWebSecurityConfigurerAdapter` becomes `apiSecurityFilterChain`). Re-add the `@Order` annotation to the resulting bean method (the quick fix does not carry annotations from the nested class over) and use `http.securityMatcher(...)` (the 6.0 replacement for `HttpSecurity.antMatcher`/`requestMatcher`) to scope each chain.
