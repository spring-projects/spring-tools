## Explanations
This error appears in a Spring Boot YAML configuration file (`application*.yml`, `application*.yaml`, `bootstrap*.yml`, `bootstrap*.yaml`) when a `<<` merge key is given a value that cannot be merged. The YAML merge-key convention lets a mapping pull in the entries of another mapping through an alias (`<<: *defaults`) or of several mappings through a list of aliases (`<<: [*a, *b]`). Anything else — a scalar, an alias to a scalar or a list that contains non-mapping items — has no defined meaning. Spring Boot loads YAML with a SnakeYAML `SafeConstructor`, which resolves merge keys and rejects malformed ones, so the application fails at startup with `ConstructorException: while constructing a mapping … expected a mapping or list of mappings for merging, but found scalar`.

The language server raises this diagnostic when all of the following hold:
- the file is recognised as a Spring Boot YAML configuration file and the project has a non-empty configuration metadata index (the same precondition as the `YAML_*` checks);
- a mapping at any nesting level contains a `<<` key, and its value is either not a mapping or a sequence (message `Expected a mapping or list of mappings for merging, but found <kind>`, highlighting the value) or a sequence with an element that is not a mapping (message `Expected a mapping for merging, but found <kind>`, highlighting that element); `<kind>` is `scalar`, `sequence`, `mapping` or `anchor`.

Unlike the other configuration-file diagnostics, this one does not come from `ApplicationYamlProblemType`: the reconciler flattens merge keys with the shared `commons-yaml` `NodeMergeSupport`, which reports through `YamlSchemaProblems.SCHEMA_PROBLEM` — hence the code `YamlSchemaProblem` and the category `yaml-schema-problems`, neither of which is listed in `problem-types.json`. The default severity is `ERROR`; it can still be changed with `spring-boot.ls.problem.yaml-schema-problems.YamlSchemaProblem`. There is no automated quick fix. Once the merge value is valid, the merged entries take part in the ordinary checks (`YAML_UNKNOWN_PROPERTY`, `YAML_DUPLICATE_KEY`, …) as if they were written inline.

For more details, see:
- [Spring Boot: Externalized Configuration — working with YAML](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.yaml)
- [Spring Framework `YamlProcessor` (SnakeYAML loading used by Spring Boot)](https://github.com/spring-projects/spring-framework/blob/main/spring-beans/src/main/java/org/springframework/beans/factory/config/YamlProcessor.java)
- [YAML merge key type](https://yaml.org/type/merge.html)

## Fixes
**Fix 1: Merge from an anchored mapping, not a scalar**
Point the alias at a mapping that holds the shared entries.

*Before:*
```yaml
default-port: &port 8080
server:
  <<: *port
```

*After:*
```yaml
defaults: &server-defaults
  port: 8080
  shutdown: graceful
server:
  <<: *server-defaults
```

**Fix 2: Keep only mappings in a merge list**
Every item of `<<: [ … ]` must itself be a mapping; move plain values out of the list into the mapping.

*Before:*
```yaml
web: &web
  port: 8080
server:
  <<: [*web, 0.0.0.0]
```

*After:*
```yaml
web: &web
  port: 8080
server:
  <<: [*web]
  address: 0.0.0.0
```

**Fix 3: Drop the merge key when there is nothing to share**
If the value was meant as an ordinary property, give it a real key name.

*Before:*
```yaml
server:
  <<: 8080
```

*After:*
```yaml
server:
  port: 8080
```

*Note: merge keys are a SnakeYAML convention rather than part of YAML 1.2 or of the Spring Boot documentation; profile-specific documents (`spring.config.activate.on-profile`) or separate profile files are the documented way to share and override settings, and unlike `<<` they also work for `.properties` files.*
