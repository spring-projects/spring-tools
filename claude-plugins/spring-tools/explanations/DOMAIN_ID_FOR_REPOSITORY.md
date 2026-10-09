## Explanations
This error appears on the ID type argument of a Spring Data repository interface (for example the `Long` in `extends CrudRepository<Customer, Long>`), or on the `idClass` attribute of `@RepositoryDefinition`, when that type does not match the type of the identifier property declared in the domain class.

Spring Data's central marker interface is `org.springframework.data.repository.Repository<T, ID>`, where `T` is "the domain type the repository manages" and `ID` is "the type of the id of the entity the repository manages". All store-specific and CRUD variants (`CrudRepository`, `ListCrudRepository`, `PagingAndSortingRepository`, `JpaRepository`, `MongoRepository`, `ReactiveCrudRepository`, ...) inherit these two type parameters. Spring Data uses the `ID` argument to type methods such as `findById(ID)`, `existsById(ID)` and `deleteById(ID)`. If it disagrees with the actual identifier property of the entity, the mismatch is not caught by the Java compiler: the code compiles, but callers pass values of the wrong type, and the repository fails at runtime (for example when Spring Data tries to convert or bind the identifier value) or silently never finds anything.

How the language server decides: the reconciler runs in projects with Spring Boot 2.0 or newer and inspects every type that (directly or through intermediate generic interfaces and type variables) extends `org.springframework.data.repository.Repository`, as well as types annotated with `@RepositoryDefinition` (it reads `domainClass` and `idClass`). Interfaces annotated (or meta-annotated) with `@NoRepositoryBean` are skipped, because they still contain unresolved type variables by design. For the domain type it looks up the identifier in this order:

- a `@jakarta.persistence.IdClass` / `@javax.persistence.IdClass` annotation on the domain class, whose `value` becomes the expected ID type
- fields and (for non-record classes) methods annotated or meta-annotated with `@org.springframework.data.annotation.Id`, `@jakarta.persistence.Id`, `@javax.persistence.Id`, `@jakarta.persistence.EmbeddedId` or `@javax.persistence.EmbeddedId`, walking up the superclass chain
- if nothing is annotated and `spring-data-mongodb` is on the classpath, a field named `id` or a method named `id` (without generic type arguments) with a non-void return type (Spring Data MongoDB's implicit identifier convention)

The error is only reported when exactly one identifier type is discovered; entities with several `@Id` properties (composite keys without `@IdClass`) are not marked. The comparison is deliberately lenient: any two boxed numeric types out of `Integer`, `Long`, `Short`, `Float`, `Double` and `Byte` are accepted as compatible, and a type argument that is cast-compatible with the identifier type (for example a primitive `long` field versus a `Long` argument, or a superclass/subclass) is accepted as well. `String` versus `Long`, `UUID` versus `Long`, or two unrelated classes are flagged. The message names the expected type, for example `Expected Domain ID type is 'java.lang.Long'`.

For more details, see:
- [Spring Data Commons: Defining Repository Interfaces](https://docs.spring.io/spring-data/commons/reference/repositories/definition.html) ("The interface must extend `Repository` and be typed to the domain class and an ID type"; also covers `@RepositoryDefinition` and `@NoRepositoryBean` base interfaces)
- [`Repository<T, ID>` API documentation](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/repository/Repository.html) (type parameters `T` and `ID`)

## Fixes
**Fix 1: Change the repository's ID type argument to match the identifier property**
The most common cause is a repository declared with a different ID type than the entity's `@Id` field. Change the second type argument to the identifier's (boxed) type.

*Before:*
```java
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.repository.CrudRepository;

@Entity
class Customer {

    @Id
    private Long id;

    private String name;

}

interface CustomerRepository extends CrudRepository<Customer, String> {
}
```

*After:*
```java
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.repository.CrudRepository;

@Entity
class Customer {

    @Id
    private Long id;

    private String name;

}

interface CustomerRepository extends CrudRepository<Customer, Long> {
}
```

*Note: Primitive identifier fields (`long id`) are compatible with their boxed wrapper (`Long`) as the type argument, and a repository declared with `Integer` for a `Long` identifier is tolerated by the language server as well. Prefer using the exact boxed type of the identifier property anyway, so that `findById(...)`, `deleteById(...)` and derived query methods carry the correct signature.*

**Fix 2: Change the identifier property to the type the repository already exposes**
If the repository's ID type is the one that is actually used throughout the application (for example `UUID` identifiers exposed via a REST API) and the entity is the odd one out, change the identifier property instead. Make sure the change is reflected in the database schema and in any existing data.

*Before:*
```java
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;

@Document
class Order {

    @Id
    private String id;

    private String customerNumber;

}

interface OrderRepository extends MongoRepository<Order, UUID> {
}
```

*After:*
```java
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;

@Document
class Order {

    @Id
    private UUID id;

    private String customerNumber;

}

interface OrderRepository extends MongoRepository<Order, UUID> {
}
```

**Fix 3: Use the `@IdClass` type for composite identifiers**
For JPA entities with a composite key declared via `@IdClass`, the repository's ID type must be the `@IdClass` value, not one of the individual key columns. (Entities that use `@EmbeddedId` must use the embeddable type as the repository ID type; entities with several bare `@Id` properties and no `@IdClass` are not checked by the language server.)

*Before:*
```java
import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import org.springframework.data.jpa.repository.JpaRepository;

class OrderLineId implements Serializable {

    private Long orderId;

    private Integer lineNumber;

    // equals(), hashCode(), constructors ...

}

@Entity
@IdClass(OrderLineId.class)
class OrderLine {

    @Id
    private Long orderId;

    @Id
    private Integer lineNumber;

    private String product;

}

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
}
```

*After:*
```java
import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import org.springframework.data.jpa.repository.JpaRepository;

class OrderLineId implements Serializable {

    private Long orderId;

    private Integer lineNumber;

    // equals(), hashCode(), constructors ...

}

@Entity
@IdClass(OrderLineId.class)
class OrderLine {

    @Id
    private Long orderId;

    @Id
    private Integer lineNumber;

    private String product;

}

interface OrderLineRepository extends JpaRepository<OrderLine, OrderLineId> {
}
```

**Fix 4: Fix the `idClass` attribute of `@RepositoryDefinition`**
Repositories that do not extend a Spring Data interface but are declared with `@RepositoryDefinition` are checked the same way; the error is placed on the `idClass` attribute.

*Before:*
```java
import java.util.Optional;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.repository.RepositoryDefinition;

@Entity
class Product {

    @Id
    private Long id;

    private String sku;

}

@RepositoryDefinition(domainClass = Product.class, idClass = String.class)
interface ProductRepository {

    Optional<Product> findById(String id);

}
```

*After:*
```java
import java.util.Optional;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.repository.RepositoryDefinition;

@Entity
class Product {

    @Id
    private Long id;

    private String sku;

}

@RepositoryDefinition(domainClass = Product.class, idClass = Long.class)
interface ProductRepository {

    Optional<Product> findById(Long id);

}
```

*Note: When the ID type is passed through an intermediate generic base interface (for example `interface MyBaseRepository<T, ID> extends Repository<T, ID>` annotated with `@NoRepositoryBean`), the language server resolves the type variables and reports the error on the concrete sub-interface (`interface UserRepository extends MyBaseRepository<User, Long>`), which is also where the fix belongs.*
