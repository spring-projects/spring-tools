## Explanations
This error appears on a key in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) whose configuration metadata carries a `deprecation` block with `level: error`. According to the metadata specification, a property with an error deprecation level "is no longer managed and is not bound": Spring Boot keeps the metadata entry only so that tools can point to the replacement, but the value in the file has no effect at all. A file that still sets such a key looks complete while the application silently runs with defaults — for example every `spring.redis.*` key after the move to `spring.data.redis.*` in Spring Boot 3.0.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path resolves to a metadata property whose `deprecation.level` is `error`, or the key is a nested bean property deprecated at that level (then the message names the owning type: `Property 'x' of type 'T' is Deprecated…`);
- the message is `Property '<key>' is Deprecated: Use '<replacement>' instead. Reason: <reason>` (parts omitted when absent) or `Property '<key>' is Deprecated!`.

The diagnostic is tagged as `Deprecated`, so editors render the key struck through. The default severity is `ERROR` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_DEPRECATED_ERROR`. A quick fix "Replace with `<replacement>`" is offered when the metadata provides a replacement. Properties deprecated at the default `warning` level are reported as `YAML_DEPRECATED_WARNING`; the `.properties` reconciler does not distinguish the level and always reports `PROP_DEPRECATED`.

For more details, see:
- [Spring Boot: Configuration metadata format — deprecation attributes](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)
- [Spring Boot: Configuration metadata annotation processor (`@DeprecatedConfigurationProperty`)](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)
- [Spring Boot: Properties and configuration how-to (`spring-boot-properties-migrator`)](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)
- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)

## Fixes
**Fix 1: Move the value to the replacement key**
Apply the quick fix or rename the key path; the value is unchanged.

*Before:*
```yaml
spring:
  redis:
    host: cache.internal
    port: 6379
```

*After:*
```yaml
spring:
  data:
    redis:
      host: cache.internal
      port: 6379
```

**Fix 2: Delete a property that has no replacement**
The reason in the message explains why the setting no longer exists; remove the key and, if needed, achieve the behaviour differently.

*Before:*
```yaml
spring:
  jpa:
    hibernate:
      use-new-id-generator-mappings: false
```

*After:*
```yaml
# removed: Hibernate no longer supports disabling the new ID generator mappings;
# configure the generator on the entity (@GeneratedValue(strategy = ...)) instead
```

*Note: because error-level properties are not bound, fixing this diagnostic can change runtime behaviour — the value in the file has been ignored so far. Review the replacement's default before deploying.*
