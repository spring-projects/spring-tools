## Explanations
This warning appears on test classes that combine `@org.junit.jupiter.api.extension.ExtendWith(SpringExtension.class)` and `@org.springframework.test.context.ContextConfiguration`.

Spring Test provides `@org.springframework.test.context.junit.jupiter.SpringJUnitConfig` as a single, meta-annotated replacement for this common combination (`@SpringJUnitConfig` is itself annotated with `@ExtendWith(SpringExtension.class)` and `@ContextConfiguration`). Using it instead removes the boilerplate of wiring up the JUnit Jupiter extension by hand and is the idiomatic way to declare a Spring TestContext Framework test on JUnit Jupiter.

## Fixes
**Fix 1: Combine into `@SpringJUnitConfig`**
Remove the `@ExtendWith(SpringExtension.class)` and `@ContextConfiguration` annotations and replace them with a single `@SpringJUnitConfig` annotation, carrying over the `@ContextConfiguration` attributes.

*Before:*
```java
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class GreetingTests {
    // ...
}
```

*After:*
```java
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(TestConfig.class)
class GreetingTests {
    // ...
}
```

*Note: every `@ContextConfiguration` attribute has an equivalent of the same name on `@SpringJUnitConfig` and is carried over, with one exception: the default attribute means something different on the two annotations. On `@ContextConfiguration` `value` aliases `locations`, whereas on `@SpringJUnitConfig` it aliases `classes`. A default-attribute value is therefore written out as an explicit `locations` attribute; conversely, a lone `classes` attribute is written out in the shorter default-attribute form.*

*Before (XML locations):*
```java
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration("/test-context.xml")
class GreetingTests {
    // ...
}
```

*After (XML locations):*
```java
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(locations = "/test-context.xml")
class GreetingTests {
    // ...
}
```

*Note: an `@ExtendWith` that registers further extensions alongside `SpringExtension` (e.g. `@ExtendWith({SpringExtension.class, MockitoExtension.class})`) is not flagged, since folding it into `@SpringJUnitConfig` would drop those other extensions. A repeated `@ExtendWith` is fine though - only the one registering just `SpringExtension` is replaced and the others stay in place.*

*Note: classes annotated with `@SpringBootTest` or one of the Spring Boot test slice annotations (`@WebMvcTest`, `@DataJpaTest`, ...) are not flagged. Those already apply the `SpringExtension` themselves, so removing the redundant `@ExtendWith` is the right fix there - see `JAVA_TEST_SPRING_EXTENSION`.*
