## Explanations
This error indicates that a `META-INF/spring.factories` file registers auto-configuration classes under the key `org.springframework.boot.autoconfigure.EnableAutoConfiguration`. Spring Boot 3.0 removed support for this key: auto-configurations must instead be listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, one fully qualified class name per line. Spring Boot 2.7 introduced the imports file and deprecated loading auto-configurations from `spring.factories`; with Spring Boot 3.0 entries under the `EnableAutoConfiguration` key are silently ignored, so an auto-configuration that is still registered only there never becomes active.

Only the `EnableAutoConfiguration` key is affected. Other `spring.factories` keys (for example `org.springframework.context.ApplicationListener`, `org.springframework.boot.env.EnvironmentPostProcessor` or `org.springframework.boot.autoconfigure.AutoConfigurationImportFilter`) continue to work and are not reported.

The language server reconciles `spring.factories` files and raises this error when the problem is enabled explicitly in the settings, or, in the default automatic mode, when the project's Spring Boot version is 3.0.0 or newer. The marker covers the whole key/value pair, including any continuation lines. There is no automatic quick fix; move the entries to the imports file by hand as shown below.

For more details, see the official Spring Boot documentation:
- [Spring Boot 3.0 Migration Guide: Auto-configuration Files](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide#auto-configuration-files) (removal of the `EnableAutoConfiguration` key; other keys are unaffected; libraries targeting 2.x and 3.x can list classes in both files)
- [Spring Boot 2.7 Release Notes: Auto-configuration Registration](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.7-Release-Notes#auto-configuration-registration) (introduction of the imports file and the `@AutoConfiguration` annotation; `spring.factories` entries still honored in 2.7 and de-duplicated)
- [Creating Your Own Auto-configuration: Locating Auto-configuration Candidates](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.locating-auto-configuration-candidates) (file format, `#` comments, `$` for nested classes, and why auto-configurations must not be component-scanned)

## Fixes
**Fix 1: Move the entries to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**
Create the file `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in the same resources directory, list each auto-configuration class on its own line, and delete the `EnableAutoConfiguration` key from `spring.factories`. Comments start with `#`; nested classes use `$` as separator (for example `com.example.Outer$NestedAutoConfiguration`). If `spring.factories` contained no other keys, delete the file altogether.

Top-level classes listed in the imports file should be annotated with `@AutoConfiguration` (which is itself meta-annotated with `@Configuration` and supports `before`, `beforeName`, `after` and `afterName` for ordering) rather than `@Configuration`; configuration classes nested within or imported by an `@AutoConfiguration` class keep `@Configuration`. Auto-configurations must be loaded only through the imports file and must never be picked up by component scanning.

*Before (`META-INF/spring.factories`):*
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.mycorp.libx.autoconfigure.LibXAutoConfiguration,\
com.mycorp.libx.autoconfigure.LibXWebAutoConfiguration

org.springframework.boot.env.EnvironmentPostProcessor=\
com.mycorp.libx.env.LibXEnvironmentPostProcessor
```

*After (`META-INF/spring.factories`, only the unaffected key remains):*
```properties
org.springframework.boot.env.EnvironmentPostProcessor=\
com.mycorp.libx.env.LibXEnvironmentPostProcessor
```

*After (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):*
```properties
# LibX auto-configurations, one class per line
com.mycorp.libx.autoconfigure.LibXAutoConfiguration
com.mycorp.libx.autoconfigure.LibXWebAutoConfiguration
```

*After (the listed classes use `@AutoConfiguration`):*
```java
package com.mycorp.libx.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class LibXAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LibXService libXService() {
        return new LibXService();
    }
}
```

**Fix 2: Keep both registrations for libraries that support Spring Boot 2.x and 3.x**
A library that must still run on Spring Boot 2.6 or earlier cannot drop the `spring.factories` key, because only Spring Boot 2.7 and later read the imports file. In that case list the auto-configuration classes in both files: Spring Boot 2.7 de-duplicates entries that appear in both, and Spring Boot 3.x ignores the `EnableAutoConfiguration` key. The language server still reports the key in such a library when the library itself is built against Spring Boot 3.x, so this fix mainly applies to libraries built against 2.7.

*Before (`META-INF/spring.factories` only):*
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.mycorp.libx.autoconfigure.LibXAutoConfiguration
```

*After (`META-INF/spring.factories` kept for Spring Boot 2.x consumers):*
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.mycorp.libx.autoconfigure.LibXAutoConfiguration
```

*After (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` added for Spring Boot 2.7 and 3.x consumers):*
```properties
com.mycorp.libx.autoconfigure.LibXAutoConfiguration
```

**Note on test slices:** The same change applies to custom test slice annotations. Auto-configurations that used to be registered under a `spring.factories` key named after the slice annotation are now listed in `META-INF/spring/<fully qualified name of the slice annotation>.imports`, again one class per line.
