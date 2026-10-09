## Explanations
This warning appears on a `@Repository` annotation that is placed on a Spring Data repository interface, i.e. an interface that extends `org.springframework.data.repository.Repository` directly or through one of its sub-interfaces (`CrudRepository`, `ListCrudRepository`, `PagingAndSortingRepository`, `JpaRepository`, `MongoRepository`, `ReactiveCrudRepository`, ...). The diagnostic is tagged as "unnecessary": the annotation has no effect there.

`org.springframework.stereotype.Repository` is a stereotype for *classes*. Its API documentation describes it as "a specialization of `@Component`, allowing for implementation classes to be autodetected through classpath scanning", and a class thus annotated "is eligible for Spring `DataAccessException` translation when used in conjunction with a `PersistenceExceptionTranslationPostProcessor`". Neither aspect applies to a Spring Data repository interface: there is no implementation class to detect, and the bean is not created by component scanning at all. Instead, Spring Data's own repository scanning (enabled by `@EnableJpaRepositories` and friends, or by Spring Boot's auto-configuration) finds the interfaces. The Spring Data Commons reference documentation describes the mechanism as follows: Spring "is instructed to scan `com.acme.repositories` and all its sub-packages for interfaces extending `Repository` or one of its sub-interfaces. For each interface found, the infrastructure registers the persistence technology-specific `FactoryBean` to create the appropriate proxies that handle invocations of the query methods." The `@Repository` annotation plays no role in this and only adds noise (and an import).

The language server raises this diagnostic when all of the following hold:
- the project uses Spring Boot 2.0 or newer;
- the annotated type is an *interface* whose type hierarchy contains `org.springframework.data.repository.Repository`;
- the `@Repository` annotation is used without attributes, i.e. `@Repository` or `@Repository()`. An annotation with attributes such as `@Repository("customName")` is not reported, since it could be an intentional way to influence the bean name.

Hand-written repository *classes* (DAOs built on `JdbcTemplate`, `JdbcClient`, `EntityManager`, ...) are not affected: for them `@Repository` is the correct stereotype and remains fully meaningful.

Two quick fixes are available: "Remove Unnecessary @Repository" for a single interface, and "Remove all unnecessary @Repository in file" to clean up every affected interface in the same file.

For more details, see:
- [Spring Data Commons: Creating Repository Instances](https://docs.spring.io/spring-data/commons/reference/repositories/create-instances.html) (how repository interfaces are detected and turned into beans)
- [Spring Data Commons: Defining Repository Interfaces](https://docs.spring.io/spring-data/commons/reference/repositories/definition.html)
- [`@Repository` API documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/stereotype/Repository.html)
- [Spring Boot: SQL Databases, "Spring Data JPA Repositories"](https://docs.spring.io/spring-boot/reference/data/sql.html) (repositories are searched in the auto-configuration packages)

## Fixes
**Fix 1: Remove `@Repository` from the Spring Data repository interface**
Delete the annotation and remove the `org.springframework.stereotype.Repository` import if nothing else in the file uses it. Spring Data keeps creating the repository bean exactly as before; the bean name is still derived from the interface name (`CustomerRepository` becomes `customerRepository`). This is what the quick fix does.

*Before:*
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Customer findByEmail(String email);
}
```

*After:*
```java
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Customer findByEmail(String email);
}
```

*Before (base interface `Repository`):*
```java
import java.util.Optional;

import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface CityRepository extends Repository<City, Long> {

    Optional<City> findByName(String name);
}
```

*After (base interface `Repository`):*
```java
import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface CityRepository extends Repository<City, Long> {

    Optional<City> findByName(String name);
}
```

*Note: if the repository interfaces live outside the packages that Spring Boot scans by default (the package of the `@SpringBootApplication` class and its sub-packages), removing `@Repository` does not change anything either: those interfaces were never picked up by component scanning. Use `@EnableJpaRepositories(basePackages = ...)` (or the equivalent for your store module) to point Spring Data at them.*

**Fix 2: Keep `@Repository` on hand-written repository classes**
The stereotype is still the right choice for a class that implements data access itself. Nothing needs to change there; only move it away from Spring Data interfaces.

*Before:*
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
}

@Repository
class ReportRepository {

    private final JdbcClient jdbcClient;

    ReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    long countOrders() {
        return this.jdbcClient.sql("select count(*) from orders").query(Long.class).single();
    }
}
```

*After:*
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}

@Repository
class ReportRepository {

    private final JdbcClient jdbcClient;

    ReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    long countOrders() {
        return this.jdbcClient.sql("select count(*) from orders").query(Long.class).single();
    }
}
```

*Note: a common base interface for several repositories should be annotated with `@NoRepositoryBean` (from `org.springframework.data.repository`) rather than with `@Repository`, so that Spring Data does not try to create an instance of the generic base interface itself.*
