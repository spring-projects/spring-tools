## Explanations
This warning appears on a `@Value` annotation whose value is a classpath location (a string starting with `classpath:` or `classpath*:`) when the annotated field or parameter has a type that cannot receive an injected classpath resource.

Spring's `Resource` abstraction is what makes `@Value("classpath:...")` useful: the container registers a `PropertyEditor` that converts the location string into a `Resource` (or, via the `ResourcePatternResolver`, into a `Resource[]` for `classpath*:` patterns); the language server additionally accepts `java.io.InputStream`, `java.io.File`, `java.net.URL` and `java.nio.file.Path` targets, for which Spring registers dedicated property editors in `org.springframework.beans.propertyeditors`. If the target is declared as `String` instead, no resolution happens at all. The field simply receives the literal text `classpath:config/template.txt`, and code that later treats that value as a file name or a path fails with a "file not found" style error, or works only by accident when a resource loader is applied to the string manually. Other unrelated types (for example a domain object or a `List<String>`) cannot be converted from a resource at all and fail at startup.

How the language server decides: the reconciler runs in projects with Spring Boot 2.0 or newer. It visits field declarations and single variable declarations (constructor and method parameters) that carry `@org.springframework.beans.factory.annotation.Value` (directly or as a meta-annotation), reads the `value` attribute, trims it, removes a surrounding `${` ... `}` wrapper if the whole value is wrapped in one, and triggers only if the resulting text starts with `classpath:` or `classpath*:`. A property placeholder with a classpath default such as `@Value("${report.template:classpath:reports/default.html}")` is therefore not detected (the text after unwrapping starts with the property name), and neither is a location that comes entirely from configuration. The declared type is then compared against the accepted set: `org.springframework.core.io.Resource`, `java.io.InputStream`, `java.io.File`, `java.net.URL`, `java.nio.file.Path`, and arrays of any of these. Everything else, including `String`, is reported with the message `Type '...' is not compatible with classpath resource injection. Use org.springframework.core.io.Resource, java.io.InputStream, java.io.File, java.net.URL, or java.nio.file.Path`. The warning is placed on the annotation. There is no automatic quick fix.

For more details, see:
- [Spring Framework: Resources - Resources as Dependencies](https://docs.spring.io/spring-framework/reference/core/resources.html) (injecting `Resource` and `Resource[]` via `@Value`, the `classpath:` and `classpath*:` prefixes, `ResourceLoader` and `ResourcePatternResolver`)
- [`Resource` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/Resource.html) (`getInputStream()`, `getContentAsString(Charset)` and `getContentAsByteArray()` since Spring Framework 6.0.5, `getFile()` only for file-system resources)
- [`org.springframework.beans.propertyeditors` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/propertyeditors/package-summary.html) (`InputStreamEditor`, `FileEditor`, `URLEditor`, `PathEditor`)

## Fixes
**Fix 1: Inject a `Resource` instead of a `String` and read its content**
Declare the field or parameter as `org.springframework.core.io.Resource`. Read the content through `getInputStream()` or, since Spring Framework 6.0.5 (Spring Boot 3.0.3 and newer), through the convenience methods `getContentAsString(Charset)` and `getContentAsByteArray()`.

*Before:*
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class TemplateService {

    @Value("classpath:templates/welcome.txt")
    private String templateLocation;

    String render() throws IOException {
        // fails: the string literally is "classpath:templates/welcome.txt"
        return Files.readString(Path.of(templateLocation), StandardCharsets.UTF_8);
    }

}
```

*After:*
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
class TemplateService {

    @Value("classpath:templates/welcome.txt")
    private Resource template;

    String render() throws IOException {
        return template.getContentAsString(StandardCharsets.UTF_8);
    }

}
```

*Note: Avoid calling `Resource.getFile()` on classpath resources. It only works for resources that live in the file system and throws a `FileNotFoundException` once the application runs from a packaged JAR, where the resource is an entry inside the archive. `getInputStream()`, `getContentAsString(...)`, `getContentAsByteArray()`, `getURL()` and `getURI()` work in both cases.*

**Fix 2: Use constructor injection with a `Resource` parameter**
The same applies to constructor and method parameters. Prefer constructor injection so the resource is available as soon as the bean is created and the field can be `final`.

*Before:*
```java
import java.io.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SchemaLoader {

    private final File schemaFile;

    SchemaLoader(@Value("classpath:schema/openapi.yaml") String schemaLocation) {
        this.schemaFile = new File(schemaLocation);
    }

}
```

*After:*
```java
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
class SchemaLoader {

    private final Resource schema;

    SchemaLoader(@Value("classpath:schema/openapi.yaml") Resource schema) {
        this.schema = schema;
    }

    InputStream open() throws IOException {
        return schema.getInputStream();
    }

}
```

**Fix 3: Inject `Resource[]` for `classpath*:` wildcard patterns**
A `classpath*:` location with wildcards can match several resources. Declare the target as an array of `Resource`; Spring resolves the pattern via its `ResourcePatternResolver` and injects all matches.

*Before:*
```java
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class RuleRepository {

    @Value("classpath*:rules/*.json")
    private List<String> ruleFiles;

}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
class RuleRepository {

    @Value("classpath*:rules/*.json")
    private Resource[] ruleFiles;

}
```

**Fix 4: Keep the `String` but resolve it through a `ResourceLoader`**
If the location must stay available as a plain string (for example because it is also used in log output or in a non-Spring API), do not inject a `classpath:` literal into a `String`. Instead inject the `ResourceLoader` (or `ResourcePatternResolver` for patterns) and resolve the location explicitly where it is needed. Moving the location into a configuration property at the same time lets the value switch between `classpath:`, `file:` and `https:` locations without code changes.

*Before:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ReportConfig {

    @Value("classpath:reports/default.html")
    private String templateLocation;

}
```

*After:*
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
class ReportConfig {

    private final ResourceLoader resourceLoader;

    private final String templateLocation;

    ReportConfig(ResourceLoader resourceLoader,
            @Value("${report.template:classpath:reports/default.html}") String templateLocation) {
        this.resourceLoader = resourceLoader;
        this.templateLocation = templateLocation;
    }

    Resource template() {
        return resourceLoader.getResource(templateLocation);
    }

}
```

*Note: A placeholder with a classpath default (`@Value("${report.template:classpath:...}") String`) is not reported by the language server, but it has the same problem at runtime: the `String` receives the location text, not the content. Simply injecting the value as a `Resource` (as in Fix 1) is usually the better option when the value is only ever used to load content; the `ResourceLoader` variant is for the cases where the raw string is genuinely needed.*
