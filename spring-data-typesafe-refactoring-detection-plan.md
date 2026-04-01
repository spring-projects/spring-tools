# Spring Data Type-Safe Property References — Refactoring Detection Plan

## Goal

Detect string-based property references in Spring Data code (`Sort.by(...)`, `Criteria.where(...)`, etc.) and provide automated refactoring to the new type-safe property path API introduced in Spring Data 2026.0.0-M1.

The key challenge: **determining the Domain Type** so that string property names can be mapped to method references (e.g. `"firstName"` → `Person::getFirstName`).

## Target Transformations

| Before (string-based) | After (type-safe) |
|---|---|
| `Sort.by("firstName", "lastName")` | `Sort.by(Person::getFirstName, Person::getLastName)` |
| `Sort.by(Direction.ASC, "lastName")` | `Sort.by(Person::getLastName).ascending()` (or equivalent) |
| `Sort.by(Sort.Order.desc("total"))` | `Sort.by(Person::getTotal).descending()` (or equivalent) |
| `where("firstName").is(...)` | `where(Person::getFirstName).is(...)` |
| `Criteria.where("address.country").is(...)` | `where(PropertyPath.of(Person::getAddress).then(Address::getCountry)).is(...)` |

---

## Detection Scenarios & Domain Type Resolution

### Scenario 1: Sort/Criteria passed as argument to a Repository method call

**Pattern:**
```java
repository.findByLastname(name, Sort.by("firstname"));
repository.totalOrdersPerCustomer(Sort.by(Sort.Order.desc("total")));
repository.findPagedProjectedBy(PageRequest.of(0, 1, Sort.by(Direction.ASC, "lastname")));
```

**How to determine Domain Type:**
1. Resolve the type of the receiver (`repository`) — it implements a `Repository<T, ID>` interface (e.g. `CrudRepository<Customer, String>`).
2. Extract the first type parameter `T` from the `Repository<T, ID>` superinterface — that is the domain type.
3. Alternatively, resolve the called method's declaring interface and find the domain type from its `Repository<T, ID>` bound.

**JDT AST Strategy:**
- Locate the `MethodInvocation` node containing the `Sort.by(...)` or `PageRequest.of(...)` expression as an argument.
- Resolve the receiver's type binding → walk the type hierarchy to find `Repository<T, ID>` → extract `T`.
- The `T` type binding gives us access to its declared methods/fields for property name validation and getter resolution.

**Observed examples from sample projects:**
- `repository.findByLastname(dave.getLastname(), Sort.by("firstname"))` — receiver is `AdvancedRepository extends CustomerRepository extends CrudRepository<Customer, String>` → **Domain Type = Customer**
- `repository.totalOrdersPerCustomer(Sort.by(Sort.Order.desc("total")))` — receiver is `OrderRepository extends CrudRepository<Order, String>` → **Domain Type = Order** (this is an `@Aggregation`-annotated method; `"total"` is a computed field that won't match a domain property)
- `customers.findPagedProjectedBy(PageRequest.of(0, 1, Sort.by(Direction.ASC, "lastname")))` — receiver is `CustomerRepository extends CrudRepository<Customer, String>` → **Domain Type = Customer**
- `repository.findTop2By(Sort.by(ASC, "lastname"))` — receiver is `SimpleUserRepository extends ListCrudRepository<User, Long>` → **Domain Type = User**

**Caveat for `@Aggregation`-annotated methods:** The repository method may be annotated with `@Aggregation`, and the `Sort` parameter appends to the aggregation pipeline. The domain type resolution is identical (from `Repository<T, ID>`), but the sort field may reference computed/projected names (like `"total"`) that do NOT correspond to properties on the domain type. These should be flagged as **not refactorable** (no matching getter on the domain type).

**Confidence: HIGH** — the repository type parameter is the single authoritative source.

---

### Scenario 2: Fluent Template API — `operations.query(X.class).matching(where(...))` / `operations.update(X.class).matching(...)`

**Pattern:**
```java
// Query
mongoOps.query(SWCharacter.class)
    .matching(query(where("name").is("luke")))
    .one();

// Update
template.update(Process.class)
    .matching(Query.query(Criteria.where("id").is(process.id())))
    .apply(Update.update("state", State.DONE))
    .first();
```

**How to determine Domain Type:**
1. Walk UP the method invocation chain to find the root `.query(X.class)` or `.update(X.class)` call.
2. The `X.class` class literal argument is the domain type.

**JDT AST Strategy:**
- From the `where("...")` call site, traverse the parent `MethodInvocation` chain (fluent API chaining).
- Look for a call to `query(Class)` or `update(Class)` on a `MongoOperations` / `ReactiveMongoOperations` / `FluentMongoOperations` receiver.
- Extract the `Class<X>` literal argument — `X` is the domain type.
- Note: if `.as(Y.class)` is present, the query mapping still uses the originating domain type `X` for property resolution, not `Y`.

**Observed examples from sample projects:**
- `mongoOps.query(SWCharacter.class).matching(query(where("name").is("luke")))` → **Domain Type = SWCharacter**
- `mongoOps.query(Jedi.class).matching(query(where("firstname").is("anakin")))` → **Domain Type = Jedi**
- `mongoOps.update(Jedi.class).matching(query(where("lastname").is("windu")))` → **Domain Type = Jedi**
- `template.update(Process.class).matching(Query.query(Criteria.where("id").is(...)))` → **Domain Type = Process**
- `operations.update(Manager.class).matching(where("id").is(...))` → **Domain Type = Manager**
- `operations.query(Manager.class).matching(where("id").is(...))` → **Domain Type = Manager**

**Confidence: HIGH** — the `.class` literal is explicitly in scope.

---

### Scenario 3: Template `find` / `findOne` with explicit Class parameter

**Pattern:**
```java
template.find(Query.query(Criteria.where("lastname").is("White")), Person.class);
operations.findOne(new Query(), Customer.class, "customer");
operations.find(query(byExample(example)), Person.class);
template.findAll(query(where("name").is("Marry")), PetOwner.class);  // JDBC
```

**How to determine Domain Type:**
- The second parameter to `find()` / `findOne()` / `findAll()` is the `Class<T>` domain type.

**JDT AST Strategy:**
- Locate `MethodInvocation` where method name is `find`/`findOne`/`findAll` on a template/operations type.
- The `Criteria.where("...")` or `where("...")` call is nested inside a `Query` that is the first argument.
- The second argument is `X.class` → domain type = `X`.

**Observed examples from sample projects:**
- `template.find(Query.query(Criteria.where("lastname").is("White")), Person.class)` → **Domain Type = Person**
- `operations.find(bq, Store.class)` → **Domain Type = Store**
- `operations.find(query(criteria), BlogPost.class)` → **Domain Type = BlogPost**
- `template.findAll(query(where("name").is("Marry")), PetOwner.class)` (JDBC) → **Domain Type = PetOwner**

**Confidence: HIGH** — the `Class` parameter is explicit.

---

### Scenario 4: Aggregation Framework — `newAggregation(X.class, ...)` with embedded `match(where(...))` / `sort(...)` / `project(...)`

**Pattern:**
```java
operations.aggregate(newAggregation(Order.class,
    match(where("id").is(order.getId())),
    unwind("items"),
    project("id", "customerId", "items")
        .andExpression("...").as("lineTotal"),
    sort(Direction.DESC, "totalPageCount")
), Invoice.class);
```

**How to determine Domain Type:**
1. If `newAggregation(X.class, ...)` form is used — `X` is the domain type (first parameter is the input type for field mapping).
2. If `newAggregation(...)` form is used (no Class parameter) — domain type must come from the `operations.aggregate(aggregation, "collectionName", OutputType.class)` call, but this is the **output** type, not necessarily the input domain type. This case is **ambiguous** and harder to resolve.

**JDT AST Strategy:**
- Walk to the `newAggregation(...)` call. Check if the first argument is a `Class<X>` literal → domain type = `X`.
- If no class literal, look for the enclosing `operations.aggregate(aggregation, ...)` call — the third parameter is the output type mapping (may differ from input domain type).

**Observed examples from sample projects:**
- `newAggregation(Order.class, match(where("id").is(...)), ...)` → **Domain Type = Order** (explicit)
- `newAggregation(sort(Direction.ASC, "volumeInfo.title"), project(...))` → No explicit domain type; the `operations.aggregate(aggregation, "books", BookTitle.class)` only gives output type. **Domain type is ambiguous.**

**Confidence: HIGH when Class parameter present, LOW when absent.**

---

### Scenario 5: Criteria used with `UpdateOptions` (e.g. Cassandra)

**Pattern:**
```java
operations.update(person,
    UpdateOptions.builder()
        .ifCondition(Criteria.where("name").is("Walter White"))
        .build());
```

**How to determine Domain Type:**
- The first argument to `operations.update(entity, options)` is the entity instance. Its declared/runtime type is the domain type.

**JDT AST Strategy:**
- From `Criteria.where("name")`, walk up to find the enclosing `operations.update(entity, ...)` call.
- Resolve the type binding of the first argument — that's the domain type.

**Confidence: MEDIUM** — requires resolving the type of a variable (not a `.class` literal), but usually straightforward.

---

### Scenario 6: JPA Specification / Criteria API — `root.get("property")` / `cb.equal(root.get("property"), ...)`

**Pattern:**
```java
public static Specification<Customer> accountExpiresBefore(LocalDate date) {
    return (root, query, cb) -> {
        var accounts = query.from(Account.class);
        var expiryDate = accounts.<Date>get("expiryDate");
        var customerIsAccountOwner = cb.equal(accounts.<Customer>get("customer"), root);
        return cb.and(customerIsAccountOwner, accountExpiryDateBefore);
    };
}
```

**How to determine Domain Type:**
- The `Specification<Customer>` return type carries the domain type.
- For `root.get("property")` — `root` is of type `Root<T>` where `T` is the domain type. Resolve its type parameter.
- For `accounts.get("property")` — `accounts` comes from `query.from(Account.class)`, so the domain type for that path is `Account`.

**JDT AST Strategy:**
- Find calls to `.get(String)` on `Root<T>`, `Path<T>`, `From<?,T>` types.
- Resolve the type parameter `T` from the receiver's type binding to determine the domain type.

**Note:** This is JPA Criteria API, which already has its own metamodel generator (`_` classes). The new Spring Data type-safe paths are a separate mechanism, but users may want both approaches. **This scenario may be out of scope** for the initial Spring Data type-safe property refactoring since the JPA Criteria API has its own type-safe metamodel approach.

**Confidence: MEDIUM** — type parameter resolution on generic types is reliable, but the refactoring target is different from `Sort.by()`/`Criteria.where()`.

---

## Detection Algorithm — Summary

### Step 1: Find String-Based Property Reference Sites

Scan for AST nodes matching:
1. **`Sort.by(String...)`** — `MethodInvocation` on `Sort`, method name `by`, with `String` literal arguments
2. **`Sort.by(Direction, String...)`** — same as above but first arg is `Direction`
3. **`Sort.Order.asc(String)` / `Sort.Order.desc(String)`** — `MethodInvocation` on `Sort.Order`
4. **`Criteria.where(String)`** / **`where(String)`** (static import) — `MethodInvocation` returning `Criteria`
5. **`Update.update(String, Object)`** — first argument is a property name string
6. **`PageRequest.of(int, int, Sort)`** — transitively contains `Sort.by(String...)` (already caught by rule 1)

### Step 2: Determine the Domain Type

For each detected site, walk up the AST to find the **enclosing context** and extract the domain type:

| Context | Domain Type Source |
|---|---|
| Argument to a Repository method call | Repository's `T` from `Repository<T, ID>` type hierarchy |
| Inside `.query(X.class).matching(...)` chain | `X` from the `query(X.class)` call |
| Inside `.update(X.class).matching(...)` chain | `X` from the `update(X.class)` call |
| Argument to `template.find(query, X.class)` | `X` from the second parameter |
| Inside `newAggregation(X.class, ...)` | `X` from the first parameter |
| Argument to `operations.update(entity, options)` | Type of `entity` |
| `root.get("prop")` in JPA Specification | `T` from `Root<T>` type parameter |

### Step 3: Validate Property Name Against Domain Type

1. Resolve the domain type's properties via JDT type bindings.
2. For a simple property name like `"firstName"`:
   - Look for a getter `getFirstName()` or `isFirstName()` on the domain type.
   - If found → refactoring is possible → suggest `DomainType::getFirstName`.
3. For a dotted path like `"address.country"`:
   - Split on `.`, resolve each segment's type transitively.
   - `"address"` → `Person::getAddress` returns `Address`.
   - `"country"` → `Address::getCountry`.
   - Suggest `PropertyPath.of(Person::getAddress).then(Address::getCountry)`.
4. If property name doesn't match any domain type property → **skip** (likely a computed/projected field).

### Step 4: Generate Refactored Code

- Simple property: `Sort.by("firstName")` → `Sort.by(Person::getFirstName)`
- Multiple properties: `Sort.by("firstName", "lastName")` → `Sort.by(Person::getFirstName, Person::getLastName)`
- With direction: `Sort.by(Direction.ASC, "lastName")` → TBD based on new API shape
- Nested path: `where("address.country")` → `where(PropertyPath.of(Person::getAddress).then(Address::getCountry))`
- Criteria chains: `where("firstName").is("Dave")` → `where(Person::getFirstName).is("Dave")`

---

## Priority Order for Implementation

1. **Scenario 1 (Repository method call with Sort)** — Most common, highest confidence domain type resolution (includes `@Aggregation`-annotated repository methods)
2. **Scenario 2 (Fluent Template API `.query(X.class)` / `.update(X.class)`)** — Very common in MongoDB, explicit domain type
3. **Scenario 3 (Template `find`/`findOne` with Class parameter)** — Common, explicit domain type
4. **Scenario 4 (Aggregation with explicit Class)** — Common in MongoDB, but only when `newAggregation(X.class, ...)` form used
5. **Scenario 5 (Cassandra UpdateOptions)** — Less common, requires variable type resolution
6. **Scenario 6 (JPA Specification/Criteria API)** — Likely out of scope (JPA has its own metamodel approach)

---

## Edge Cases & Limitations

1. **Computed/projected field names** — Aggregation pipelines often reference fields like `"count"`, `"total"`, `"lineTotal"` that are computed within the pipeline, not domain properties. These cannot be refactored.
2. **Dynamic property names** — Property names constructed at runtime (`Sort.by(someVariable)`) cannot be refactored.
3. **String constants / static final fields** — Property names stored in `static final String PROP = "firstName"` require constant resolution. Should be supported if the constant can be resolved at analysis time.
4. **MongoDB field name mapping** — `@Field("actual_name")` annotations may cause the string used in Criteria to differ from the Java property name. The refactoring should use the Java property name for the method reference, not the MongoDB field name.
5. **Inherited properties** — Properties declared on a superclass/superinterface of the domain type should be resolved correctly via the type hierarchy.
6. **`newAggregation(...)` without Class parameter** — Domain type is ambiguous; skip or mark as needing manual intervention.
7. **Multiple Sort properties with direction** — `Sort.by(Direction.ASC, "a", "b")` applies direction to all properties. Need to verify new API supports this or if each needs its own direction.

---

## Spring Data Modules Covered

| Module | String-based APIs Found | Scenarios |
|---|---|---|
| **MongoDB** | `Criteria.where(String)`, `Sort.by(String...)`, `Update.update(String, Object)`, `sort(Direction, String)`, `project(String...)`, `group(String)` | 1, 2, 3, 4 |
| **JPA** | `Sort.by(String...)`, `PageRequest.of(..., Sort)`, JPA Criteria `root.get(String)` | 1, 6 |
| **Cassandra** | `Criteria.where(String)` | 5 |
| **JDBC** | `where(String)` in template queries | 3 |
| **R2DBC** | (no string-based Criteria/Sort patterns found in samples) | — |
| **Redis** | (uses repository methods with Pageable, no direct Criteria/Sort) | 1 |

---

## Open Questions

1. What is the exact new API signature for `Sort.by()` with direction + type-safe reference? Is it `Sort.by(Person::getLastName).ascending()` or `Sort.by(Direction.ASC, Person::getLastName)`?
2. Does `Criteria.where(Person::getFirstName)` accept a method reference directly, or does it require `TypedPropertyPath`?
3. Should we also refactor `Update.update("property", value)` to a type-safe form? Does the new API support that?
4. Should `project("field1", "field2")` and `group("field")` in the aggregation framework also be refactored, or are those only applicable to MongoDB-specific contexts?
5. What is the minimum Spring Data version required for the type-safe API? Should the refactoring check the project's dependency version first?
