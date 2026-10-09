## Explanations
This hint appears on a `@Controller` or `@RestController` class whose method-level mapping annotations (`@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`) all start with the same path prefix, for example when every handler method is mapped below `/api/users`.

Spring MVC and WebFlux allow `@RequestMapping` at the class level: "You can use it at the class level to express shared mappings or at the method level to narrow down to a specific endpoint mapping. [...] A `@RequestMapping` is still needed at the class level to express shared mappings." Method-level paths are relative to the class-level path, and URI variables (`/{id}`) may be declared at both levels. Extracting the shared prefix removes the repetition and makes the controller's base path visible in one place without changing any effective URL.

The diagnostic message names the detected prefix, e.g. "All request mappings share the common parent path `/api/users`". If the class already has a class-level `@RequestMapping` with a single literal path, the message names the additional prefix that can be merged into it, e.g. "... can be merged into the existing class-level `@RequestMapping` (`/api` → `/api/users`)".

The hint is only reported when the rewrite is safe: the class has at least two mapped methods, every method-level mapping has exactly one literal, non-empty path (no bare `@GetMapping`, no multiple paths, no constants that cannot be resolved), and the class is either not mapped at the class level or mapped through a plain `@RequestMapping` with a single literal path (not through a composed/meta-annotation).

For more details, see:
- [Spring MVC: Mapping Requests - @RequestMapping](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#mvc-ann-requestmapping-annotation)
- [Spring MVC: Mapping Requests - URI patterns](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html#mvc-ann-requestmapping-uri-templates) (URI variables at class and method level)

## Fixes
**Fix 1: Extract the common prefix into a new class-level `@RequestMapping`**
Add `@RequestMapping("<common prefix>")` to the class (import `org.springframework.web.bind.annotation.RequestMapping`) and remove the prefix from every method-level mapping. A method whose path is exactly the prefix keeps its mapping annotation without a path (e.g. a bare `@GetMapping`), which maps to the class-level path. All other attributes of the method-level annotations (`method`, `produces`, `consumes`, `params`, ...) stay as they are.

*Before:*
```java
@RestController
public class UserController {

    @GetMapping("/api/users")
    public List<User> list() { /* ... */ }

    @GetMapping("/api/users/{id}")
    public User get(@PathVariable Long id) { /* ... */ }

    @PostMapping(path = "/api/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    public User create(@RequestBody User user) { /* ... */ }
}
```

*After:*
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public List<User> list() { /* ... */ }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) { /* ... */ }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public User create(@RequestBody User user) { /* ... */ }
}
```

**Fix 2: Merge the additional prefix into the existing class-level `@RequestMapping`**
If the class already has `@RequestMapping("<base>")` and all methods share a further prefix, append that prefix to the class-level path and strip it from the methods.

*Before:*
```java
@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/users")
    public List<User> list() { /* ... */ }

    @GetMapping("/users/{id}")
    public User get(@PathVariable Long id) { /* ... */ }
}
```

*After:*
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public List<User> list() { /* ... */ }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) { /* ... */ }
}
```

*Note: only whole path segments are extracted (`/api/users` and `/api/user-groups` share `/api`, not `/api/user`). Verify that every resulting effective URL is unchanged, and leave the class alone if a method intentionally maps outside the common prefix or if the mappings come from an interface (`@HttpExchange` or `@RequestMapping` declared on an implemented interface).*
