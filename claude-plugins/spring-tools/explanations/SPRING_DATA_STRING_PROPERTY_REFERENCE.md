## Explanations
This informational hint indicates that a Spring Data API is called with a String-based property reference, such as `Sort.by("firstName")` or `Criteria.where("lastName")`, even though the same API offers a type-safe overload that accepts a method reference to the domain type's property.

String-based property paths are not checked by the compiler. When a property of the domain type is renamed or removed, the string keeps compiling and the mistake only surfaces at runtime, typically as a `PropertyReferenceException` when Spring Data tries to resolve the path, or as a query that silently sorts or filters on the wrong column. Since Spring Data Commons 4.1, the `TypedPropertyPath` abstraction lets you express the same property paths with Java method references (`Customer::getFirstName`), which participate in compile-time checking and in IDE refactorings such as rename. The reference documentation describes method-reference based sort expressions as the more type-safe alternative to strings, and the older `Sort.sort(Person.class)`/`TypedSort` API (which relied on runtime CGLIB proxies) is deprecated since 4.1 in favor of `Sort.by(TypedPropertyPath...)`.

The language server applies this check when the project depends on `spring-data-commons` 4.1.0-M2 or newer, `spring-data-relational` 4.1.0-M2 or newer, `spring-data-mongodb` 5.1.0-M2 or newer, or `spring-data-cassandra` 5.1.0-M2 or newer. It inspects string literals passed to:
- Spring Data Commons: `Sort.by(...)`, `Sort.Order.asc(...)`, `Sort.Order.desc(...)` and `Sort.Order.by(...)`;
- Spring Data MongoDB (`org.springframework.data.mongodb.core.query`): `Criteria.where(...)`, the `Update` operators and `Field.include(...)`/`Field.exclude(...)`;
- Spring Data Relational (`org.springframework.data.relational.core.query`): `Criteria.where(...)`, `Update.update(...)` and `Update.set(...)`;
- Spring Data Cassandra (`org.springframework.data.cassandra.core.query`): `Criteria.where(...)`, the `Update` operators and `Columns.from(...)`.

A literal is only flagged when the declaring class actually provides an overload that accepts `org.springframework.data.core.TypedPropertyPath` at that argument position. The language server then tries to determine the domain type: exactly, when the expression is passed directly to a Spring Data repository method (e.g. `customerRepository.findAll(Sort.by("firstName"))`), or inferred from repository calls in the enclosing block. If the type is known exactly and the property can be resolved, the message names it ("Non type-safe property reference for domain type 'Customer'"); otherwise a generic message ("Non type-safe property reference") is shown. Varargs such as `Sort.by("firstName", "lastName")` produce a single hint spanning all literals.

Quick fixes are offered when the property can be resolved on the domain type: "Replace with Customer::getFirstName" for a single segment, "Replace with PropertyPath.of(Employee::getAddress).then(Address::getCity)" for nested paths, and "Replace all exact matches with type-safe property references in file" when several occurrences are found. Fixes derived from an inferred or only similar domain type are marked accordingly. If the property does not exist on the domain type, no fix is offered; in that case the string is most likely already wrong.

For more details, see:
- [Spring Data Commons: Paging and Sorting](https://docs.spring.io/spring-data/commons/reference/repositories/query-methods-details.html) (string-based vs. type-safe sort expressions)
- [`TypedPropertyPath` API documentation](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/core/TypedPropertyPath.html)
- [`PropertyPath` API documentation](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/core/PropertyPath.html)
- [`Sort` API documentation](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Sort.html) (`by(TypedPropertyPath...)`, deprecation of `sort(Class)`)

## Fixes
**Fix 1: Replace the string with a method reference**
Use the domain type's getter (or record accessor) as a method reference. `Sort.by(...)` and `Sort.Order.asc(...)`/`desc(...)` accept `TypedPropertyPath` arguments directly.

*Before:*
```java
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<Customer> findAllSorted() {
        return repository.findAll(Sort.by("firstName", "lastName"));
    }

    public List<Customer> findAllByLastNameDesc() {
        return repository.findAll(Sort.by(Sort.Order.desc("lastName")));
    }
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<Customer> findAllSorted() {
        return repository.findAll(Sort.by(Customer::getFirstName, Customer::getLastName));
    }

    public List<Customer> findAllByLastNameDesc() {
        return repository.findAll(Sort.by(Sort.Order.desc(Customer::getLastName)));
    }
}
```

*Note: for Java records, reference the accessor instead, e.g. `Sort.by(PersonRecord::firstName)`.*

**Fix 2: Compose nested property paths with `PropertyPath.of(...).then(...)`**
Dotted paths such as `"address.country"` become a chain of method references. `PropertyPath.of(...)` starts the path on the root type and `then(...)` appends the next segment; the resulting `TypedPropertyPath` can be passed wherever a property is expected, for example to the Spring Data MongoDB `Criteria`.

*Before:*
```java
import org.springframework.data.mongodb.core.query.Criteria;

public class PersonQueries {

    public Criteria inCountry() {
        return Criteria.where("address.country").is("CH");
    }
}
```

*After:*
```java
import org.springframework.data.core.PropertyPath;
import org.springframework.data.mongodb.core.query.Criteria;

public class PersonQueries {

    public Criteria inCountry() {
        return Criteria.where(PropertyPath.of(Person::getAddress).then(Address::getCountry)).is("CH");
    }
}
```

**Fix 3: Use the type-safe overloads of store-specific APIs**
The store modules offer the same overloads for their query and update builders. For a single segment a method reference is sufficient.

*Before:*
```java
import org.springframework.data.mongodb.core.query.Criteria;

public class CustomerQueries {

    public Criteria byLastName(String lastName) {
        return Criteria.where("lastName").is(lastName);
    }
}
```

*After:*
```java
import org.springframework.data.mongodb.core.query.Criteria;

public class CustomerQueries {

    public Criteria byLastName(String lastName) {
        return Criteria.where(Customer::getLastName).is(lastName);
    }
}
```

*Note: this hint has INFO severity and never affects the behavior of a correct string. It is safe to keep string-based paths where a method reference is not possible (for example when the property name is computed at runtime); in that case consider suppressing the hint through the language server's problem severity settings. If the property named in the string does not exist on the domain type, the code is already broken and will fail at runtime with a `PropertyReferenceException`. See `JAVA_REPOSITORY` for how repository interfaces, which the language server uses to determine the domain type, are detected.*
