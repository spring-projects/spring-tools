## Explanations
This hint appears on a `@ModelAttribute` method parameter that explicitly specifies a `value` or `name` attribute which is exactly the name Spring would use for that parameter anyway, for example `@ModelAttribute("user") User user` or `@ModelAttribute(name = "user") User account`.

When `@ModelAttribute` doesn't declare a name, Spring derives the model attribute name from the declared parameter type, not from the parameter name: it takes the simple class name and lowercases the first character (`User` becomes `user`, `SignupForm` becomes `signupForm`, and for a nested class `Outer.Inner` becomes `inner`). Following the JavaBeans convention, a class name that starts with two upper case letters is used unchanged (`URLInfo` stays `URLInfo`). Declaring that same name explicitly is redundant.

## Fixes
**Fix 1: Remove the redundant name**
Remove the `value` or `name` attribute from `@ModelAttribute`. Keep the annotation itself, and keep any other attributes such as `binding`.

*Before:*
```java
@PostMapping("/users")
public String save(@ModelAttribute("user") User account, BindingResult result) {
    // ...
}
```

*After:*
```java
@PostMapping("/users")
public String save(@ModelAttribute User account, BindingResult result) {
    // ...
}
```

*Before (other attributes):*
```java
@PostMapping("/users/preview")
public String preview(@ModelAttribute(name = "user", binding = false) User user) {
    // ...
}
```

*After (other attributes):*
```java
@PostMapping("/users/preview")
public String preview(@ModelAttribute(binding = false) User user) {
    // ...
}
```

*Note: the parameter name does not matter here. `@ModelAttribute("account") User account` is not redundant, since the default name would be `user`. Never remove a name that differs from the type-derived default: that changes the model attribute name, which breaks views, `@SessionAttributes` declarations and other code that refer to it.*

*Note: only parameters with a plain, non-generic class type are flagged. Arrays, collections and reactive types like `Mono` or `CompletableFuture` get different default names (e.g. `orderList` for `List<Order>` or `Order[]`), and other generic types such as `Optional<User>` are not checked either, so leave their names alone. `@ModelAttribute` on methods (e.g. `@ModelAttribute("types")` on a method that provides reference data) is not flagged either.*

*Note: for other web annotations like `@RequestParam` or `@PathVariable`, the default name is the parameter name instead - see `WEB_ANNOTATION_NAMES`.*

## References
- [`@ModelAttribute` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/ModelAttribute.html)
