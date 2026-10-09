## Explanations
This warning appears on test classes that are annotated with `@SpringBootTest` or one of Spring Boot's test slice annotations (`@WebMvcTest`, `@WebFluxTest`, `@DataJpaTest`, `@JdbcTest`, `@DataJdbcTest`, `@JsonTest`, `@RestClientTest`, `@WebServiceClientTest`, `@JooqTest`, `@DataMongoTest`, `@DataNeo4jTest`, `@DataRedisTest`, `@DataLdapTest`, `@DataCassandraTest`, `@DataR2dbcTest`, ...) and that additionally declare `@ExtendWith(SpringExtension.class)`.

As of Spring Boot 2.1, `@SpringBootTest` and all `@...Test` slice annotations are themselves meta-annotated with `@ExtendWith(SpringExtension.class)`, so the explicit declaration is redundant and only adds noise (the diagnostic is tagged as "unnecessary"). The Spring Boot reference documentation states it as follows: "If you are using JUnit 4, do not forget to also add `@RunWith(SpringRunner.class)` to your test, otherwise the annotations will be ignored. If you are using JUnit [Jupiter], there is no need to add the equivalent `@ExtendWith(SpringExtension.class)` as `@SpringBootTest` and the other `@...Test` annotations are already annotated with it."

For more details, see:
- [Spring Boot: Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) (the tip at the beginning of the page, and "Auto-configured Tests" for the slice annotations)
- [`@SpringBootTest` API documentation](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/test/context/SpringBootTest.html)

## Fixes
**Fix 1: Remove the redundant `@ExtendWith(SpringExtension.class)`**
Delete the `@ExtendWith(SpringExtension.class)` annotation from the test class and remove the imports of `org.junit.jupiter.api.extension.ExtendWith` and `org.springframework.test.context.junit.jupiter.SpringExtension` if nothing else in the file uses them. Nothing else changes; the Spring Boot test annotation keeps registering the `SpringExtension`.

*Before:*
```java
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class GreetingApplicationTests {
    // ...
}
```

*After:*
```java
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GreetingApplicationTests {
    // ...
}
```

*Before (test slice):*
```java
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@WebMvcTest(GreetingController.class)
class GreetingControllerTests {
    // ...
}
```

*After (test slice):*
```java
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

@WebMvcTest(GreetingController.class)
class GreetingControllerTests {
    // ...
}
```

*Note: if `@ExtendWith` registers further extensions alongside `SpringExtension` (e.g. `@ExtendWith({SpringExtension.class, MockitoExtension.class})`), only remove `SpringExtension.class` from the array and keep the others (`@ExtendWith(MockitoExtension.class)`). The quick fix only handles the single-value form.*

*Note: the language server recognizes the slice annotations both in their Spring Boot 2.x/3.x packages (`org.springframework.boot.test.autoconfigure.*`) and in the per-technology test modules of Spring Boot 4 (e.g. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`). A test class that combines `@ExtendWith(SpringExtension.class)` with `@ContextConfiguration` but no Spring Boot test annotation is reported separately as `SPRING_JUNIT_CONFIG_COMBINATION` (combine into `@SpringJUnitConfig`). JUnit 4 tests still need `@RunWith(SpringRunner.class)`; this hint does not apply to them.*
