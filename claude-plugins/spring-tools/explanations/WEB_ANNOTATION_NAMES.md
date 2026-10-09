## Explanations
This error occurs when a Spring web annotation (such as `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@RequestAttribute`, `@CookieValue`, `@ModelAttribute`, or `@SessionAttribute`) explicitly specifies a `value` or `name` attribute that is exactly the same as the method parameter's name.

For example, `@PathVariable("id") Long id` or `@RequestParam(name = "count", defaultValue = "3") int count`.

Spring can automatically infer the name of the web parameter directly from the Java method parameter name, so specifying the name explicitly when it matches the parameter name is redundant and clutters the code. The reference documentation states it for URI variables like this: "You can explicitly name URI variables (for example, `@PathVariable("customId")`), but you can leave that detail out if the names are the same and your code is compiled with the `-parameters` compiler flag."

The `-parameters` flag is the only supported way to retain parameter names: since Spring Framework 6.1 the `LocalVariableTableParameterNameDiscoverer` has been removed, so Spring no longer deduces parameter names from debug information (`-g`) in the bytecode. Spring Boot projects already compile with `-parameters` by default when they inherit from `spring-boot-starter-parent` (Maven) or apply the Spring Boot Gradle plugin (it configures all `JavaCompile` tasks with `-parameters`; Kotlin gets `-java-parameters`). For other builds, configure `maven-compiler-plugin` with `<parameters>true</parameters>` or add `options.compilerArgs.add("-parameters")` to the `JavaCompile` tasks before applying this fix.

For more details, see:
- [Spring MVC: Mapping Requests - URI patterns](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#mvc-ann-requestmapping-uri-templates)
- [Spring Framework 6.1 Release Notes - Parameter Name Retention](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes#parameter-name-retention)
- [Spring Boot Maven Plugin: Using the Plugin](https://docs.spring.io/spring-boot/maven-plugin/using.html) and [Spring Boot Gradle Plugin: Reacting to the Java Plugin](https://docs.spring.io/spring-boot/gradle-plugin/reacting.html)

## Fixes
**Fix 1: Remove the redundant name/value attribute**
If the annotation only has the redundant `value` or `name` attribute, you can remove the attribute entirely, leaving just the annotation.
- *Before:* `@PathVariable("id") Long id`
- *After:* `@PathVariable Long id`

**Fix 2: Remove the redundant attribute but keep others**
If the annotation has other attributes (like `required`, `defaultValue`, etc.), remove only the redundant `name` or `value` attribute and keep the rest.
- *Before:* `@RequestParam(name = "count", defaultValue = "3") int count`
- *After:* `@RequestParam(defaultValue = "3") int count`

*Note: Do not remove the annotation name/value if it differs from the method parameter name (e.g., `@PathVariable("userId") Long id`), or if the annotation is applied at the method level (e.g., `@ModelAttribute("types")`).*