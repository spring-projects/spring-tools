## Explanations
This warning appears on a key in a Spring Boot `.properties` file (`application*.properties`, `bootstrap*.properties`) whose configuration metadata carries a `deprecation` block. Spring Boot marks a property as deprecated when it has been renamed or is going to be removed; the metadata may provide a `replacement` (the new key), a `reason` and a `level` of `warning` (the default, the value is still bound) or `error` (the property is no longer managed and is not bound at all).

Keeping deprecated keys is risky: with level `warning` the value still works today but the key disappears in the next generation, and with level `error` the value is already ignored while the file looks complete. Spring Boot logs deprecation hints only when the `spring-boot-properties-migrator` module is present at runtime, so this diagnostic is often the only feedback a developer gets while editing.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot properties file and the project has a non-empty configuration metadata index;
- the key resolves to a metadata entry that has a `deprecation` section, or the key navigates with `.` into a nested bean whose property is deprecated (in that case the message names the owning type: `Property 'x' of type 'T' is Deprecated…`);
- the message is `Property '<key>' is Deprecated: Use '<replacement>' instead. Reason: <reason>` (parts omitted when not present in the metadata) or `Property '<key>' is Deprecated!` when neither is present.

The diagnostic is tagged as `Deprecated` so editors render the key struck through. The default severity is `WARNING` and can be changed with `spring-boot.ls.problem.application-properties.PROP_DEPRECATED`; unlike the YAML reconciler, the `.properties` reconciler uses this one code regardless of the metadata `level`. A quick fix "Replace with `<replacement>`" rewrites the key when the metadata provides a replacement. The YAML equivalents are `YAML_DEPRECATED_WARNING` and `YAML_DEPRECATED_ERROR`.

For more details, see:
- [Spring Boot: Configuration metadata format — deprecation attributes](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)
- [Spring Boot: Configuration metadata annotation processor (`@DeprecatedConfigurationProperty`)](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)
- [Spring Boot: Properties and configuration how-to (`spring-boot-properties-migrator`)](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)
- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)

## Fixes
**Fix 1: Use the replacement key**
Apply the quick fix or rename the key to the `replacement` named in the message; the value stays the same.

*Before:*
```properties
spring.redis.host=cache.internal
spring.redis.port=6379
```

*After:*
```properties
spring.data.redis.host=cache.internal
spring.data.redis.port=6379
```

**Fix 2: Remove a property that has no replacement**
When the message ends in `is Deprecated!` or the reason states that the feature was dropped, delete the key and configure the behaviour the way the reason describes (often a bean or a different mechanism).

*Before:*
```properties
spring.mvc.ignore-default-model-on-redirect=true
```

*After:*
```properties
# removed: deprecated for removal in Spring MVC, the default model is no longer exposed on redirect
```

*Note: for a deprecated property of your own `@ConfigurationProperties` class, put `@DeprecatedConfigurationProperty(replacement = "...", reason = "...")` on the getter of the old property, delegate to the new one, and remove the old property in the next release.*
