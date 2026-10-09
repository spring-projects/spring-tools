## Explanations
This error indicates that the source file refers to a type that is no longer supported once the project runs on Spring Boot 3 (which requires Java 17 or later and is built on Spring Framework 6.0). The language server checks for exactly three fully qualified types:

- `org.springframework.web.multipart.commons.CommonsMultipartResolver` — the Apache Commons FileUpload integration. Spring Framework 6.0 dropped several outdated Servlet-based integrations, among them Commons FileUpload, and recommends `org.springframework.web.multipart.support.StandardServletMultipartResolver` (the Servlet container's own multipart parsing) instead. The class does not exist on the Spring Framework 6 classpath, so the code no longer compiles.
- `java.lang.SecurityManager` and `java.security.AccessControlException` — the Security Manager API of the JDK. JEP 411 deprecated `SecurityManager`, `System.setSecurityManager`/`getSecurityManager`, `AccessController`, `AccessControlContext`, `AccessControlException` and related classes for removal in Java 17, the minimum Java version of Spring Boot 3. JEP 486 (Java 24) then made it an error to enable a Security Manager at startup, made `System.setSecurityManager` always throw `UnsupportedOperationException`, and rendered the remaining API non-functional ahead of its removal in a future release. Code that installs a Security Manager or catches `AccessControlException` therefore either fails at run time or becomes dead code.

The check is active for projects on Spring Boot 3.0.0 or newer. It reports every `import` declaration of one of the three types, every fully qualified use, and every simple-name use that resolves through a wildcard import; simple-name uses backed by an explicit single-type import are covered by the marker on the import itself. The marker is placed on the import or on the type reference, and the message names the offending type. No automatic quick fix is available; the affected code has to be rewritten by hand as shown below.

For more details, see the official documentation:
- [Spring Framework 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes) (Java 17+ baseline and the list of dropped Servlet integrations, including `CommonsMultipartResolver`, with `StandardServletMultipartResolver` as the recommended replacement)
- [Upgrading to Spring Framework 6.x](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x) (upgrade entry point for the Framework 6 line)
- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide) (Spring Boot 3.0 requires Java 17 and Spring Framework 6.0)
- [Spring Framework reference: Multipart Resolver](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/multipart.html) (how to enable Servlet multipart parsing and register a `StandardServletMultipartResolver`)
- [Spring Boot reference: Handling Multipart File Uploads](https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart-file-uploads) (Spring Boot auto-configures multipart support based on the Servlet `Part` API and recommends the container's built-in support over Apache Commons FileUpload; limits are set through `spring.servlet.multipart.*` properties)
- [JEP 411: Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411) (Java 17; lists the terminally deprecated classes and methods)
- [JEP 486: Permanently Disable the Security Manager](https://openjdk.org/jeps/486) (Java 24; enabling a Security Manager is an error, `System.setSecurityManager` throws `UnsupportedOperationException`)

## Fixes
**Fix 1: Remove the `CommonsMultipartResolver` bean and rely on Servlet multipart support**
In a Spring Boot application, delete the `CommonsMultipartResolver` bean (and the `commons-fileupload` dependency). Spring Boot's `MultipartAutoConfiguration` registers a `StandardServletMultipartResolver` for you and configures the Servlet container's `MultipartConfigElement` from the `spring.servlet.multipart.*` properties, so size limits that used to be set on the resolver move into `application.properties`. Controller code using `MultipartFile` does not change.

*Before (Commons FileUpload resolver bean):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

@Configuration
public class UploadConfiguration {

    @Bean
    public MultipartResolver multipartResolver() {
        CommonsMultipartResolver resolver = new CommonsMultipartResolver();
        resolver.setMaxUploadSize(10 * 1024 * 1024);
        resolver.setMaxUploadSizePerFile(1024 * 1024);
        return resolver;
    }
}
```

*After (no resolver bean; limits configured through properties):*
```java
import org.springframework.context.annotation.Configuration;

@Configuration
public class UploadConfiguration {
    // Spring Boot auto-configures a StandardServletMultipartResolver.
}
```

```properties
# application.properties
spring.servlet.multipart.max-file-size=1MB
spring.servlet.multipart.max-request-size=10MB
```

**Fix 2: Register a `StandardServletMultipartResolver` explicitly (plain Spring MVC without Spring Boot auto-configuration)**
Outside of Spring Boot, or when the multipart auto-configuration is excluded, enable Servlet multipart parsing on the `DispatcherServlet` registration with a `MultipartConfigElement` and declare a `StandardServletMultipartResolver` bean named `multipartResolver`. The `DispatcherServlet` detects the bean by that name.

*Before (Commons FileUpload resolver bean):*
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

@Configuration
public class UploadConfiguration {

    @Bean
    public MultipartResolver multipartResolver() {
        return new CommonsMultipartResolver();
    }
}
```

*After (`StandardServletMultipartResolver` plus Servlet multipart configuration):*
```java
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletRegistration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

@Configuration
public class UploadConfiguration {

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}

class AppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null;
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] { UploadConfiguration.class };
    }

    @Override
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        // Optionally also set maxFileSize, maxRequestSize, fileSizeThreshold
        registration.setMultipartConfig(new MultipartConfigElement("/tmp"));
    }
}
```

**Fix 3: Remove Security Manager usage**
Code that installs a `SecurityManager` via `System.setSecurityManager(...)` cannot work on Java 24 or later and only produces deprecation warnings on Java 17 through 23. Remove the installation and delete `catch (AccessControlException ...)` blocks, which can never be reached without a Security Manager. If the Security Manager was used to sandbox code or to block calls such as `System.exit`, JEP 486 points to alternatives outside the JDK (containers, OS-level sandboxing, or a Java agent that rewrites the offending calls). Code that merely consults `System.getSecurityManager()` and guards a permission check is compatible for now (the method returns `null`), but should be removed as well since the API is scheduled for removal.

*Before (installing a Security Manager and catching `AccessControlException`):*
```java
import java.security.AccessControlException;

public class Sandbox {

    public void run(Runnable task) {
        System.setSecurityManager(new SecurityManager());
        try {
            task.run();
        }
        catch (AccessControlException ex) {
            throw new IllegalStateException("Task tried to access a protected resource", ex);
        }
    }
}
```

*After (no Security Manager; enforce restrictions outside the JVM or reject the operation explicitly):*
```java
public class Sandbox {

    public void run(Runnable task) {
        task.run();
    }
}
```
