## Explanations
This warning appears on a key in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) whose configuration metadata carries a `deprecation` block with the default level `warning`. Spring Boot marks a property as deprecated when it has been renamed or is scheduled for removal; the metadata may name a `replacement` key and a `reason`. At level `warning` the value is still bound, so the application keeps working — until the property is dropped in a following Boot generation.

Spring Boot itself only reports deprecated keys at runtime when the `spring-boot-properties-migrator` module is on the classpath, so this diagnostic is often the only feedback while editing.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index;
- the key path resolves to a metadata property with a `deprecation` section whose `level` is not `error`, or the key is a deprecated property of a nested bean type (then the message names the owning type: `Property 'x' of type 'T' is Deprecated…`);
- the message is `Property '<key>' is Deprecated: Use '<replacement>' instead. Reason: <reason>` (parts omitted when absent) or `Property '<key>' is Deprecated!`.

The diagnostic is tagged as `Deprecated`, so editors render the key struck through. The default severity is `WARNING` and can be changed with `spring-boot.ls.problem.application-yaml.YAML_DEPRECATED_WARNING`. A quick fix "Replace with `<replacement>`" is offered when the metadata provides a replacement. Properties deprecated with `level: error` are reported as `YAML_DEPRECATED_ERROR`; the `.properties` counterpart is `PROP_DEPRECATED`.

For more details, see:
- [Spring Boot: Configuration metadata format — deprecation attributes](https://docs.spring.io/spring-boot/specification/configuration-metadata/format.html)
- [Spring Boot: Configuration metadata annotation processor (`@DeprecatedConfigurationProperty`)](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)
- [Spring Boot: Properties and configuration how-to (`spring-boot-properties-migrator`)](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)
- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)

## Fixes
**Fix 1: Use the replacement key**
Apply the quick fix or move the value to the key named in the message.

*Before:*
```yaml
spring:
  codec:
    max-in-memory-size: 2MB
```

*After:*
```yaml
spring:
  http:
    codecs:
      max-in-memory-size: 2MB
```

**Fix 2: Remove a property without replacement**
When the message ends in `is Deprecated!` or the reason says the feature was dropped, delete the key and configure the behaviour the way the reason describes.

*Before:*
```yaml
spring:
  flyway:
    clean-on-validation-error: true
```

*After:*
```yaml
# removed: deprecated in Flyway 10.18 and removed in Flyway 11.0; run `flyway clean` explicitly instead
```

*Note: for a deprecated property of your own `@ConfigurationProperties` class, annotate the getter of the old property with `@DeprecatedConfigurationProperty(replacement = "...", reason = "...")`, delegate to the new property, and remove the old one in the next release.*
