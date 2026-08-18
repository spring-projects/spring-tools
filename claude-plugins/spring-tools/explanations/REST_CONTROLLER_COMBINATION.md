## Explanations
This warning appears on classes that combine `@org.springframework.stereotype.Controller` and `@org.springframework.web.bind.annotation.ResponseBody`.

Spring Web provides `@org.springframework.web.bind.annotation.RestController` as a single, meta-annotated replacement for this common combination (`@RestController` is itself annotated with `@Controller` and `@ResponseBody`). Using it instead makes the intent of the class clearer (a controller whose handler methods write directly to the response body rather than resolving a view), and is the idiomatic way to declare REST endpoints in Spring MVC/WebFlux applications.

## Fixes
**Fix 1: Combine into `@RestController`**
Remove the `@Controller` and `@ResponseBody` annotations and replace them with a single `@RestController` annotation.

*Before:*
```java
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ResponseBody
class GreetingController {
    // ...
}
```

*After:*
```java
import org.springframework.web.bind.annotation.RestController;

@RestController
class GreetingController {
    // ...
}
```

*Note: `@Controller`'s only attribute, `value` (the bean name), has a direct equivalent of the same name on `@RestController`, so a bean name is carried over as-is. `@ResponseBody` never has any attributes.*

*Before (with bean name):*
```java
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller("greetingController")
@ResponseBody
class GreetingController {
    // ...
}
```

*After (with bean name):*
```java
import org.springframework.web.bind.annotation.RestController;

@RestController("greetingController")
class GreetingController {
    // ...
}
```

*Note: If either `@Controller` or `@ResponseBody` is missing, or the class is already annotated with `@RestController` directly, the class is left unchanged since there is nothing to combine.*
