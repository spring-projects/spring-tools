## Explanations
This error is raised when a JPQL query string embedded in Java source does not conform to the JPQL grammar. The Spring Boot language server extracts the query text from a Spring Data JPA `@Query` annotation that is not marked as native, from the `query` attribute of `@NamedQuery` (both `jakarta.persistence` and `javax.persistence`), and from the first string argument of `jakarta.persistence.EntityManager.createQuery(...)`, and parses it with an ANTLR grammar for JPQL. Any lexer or parser error is reported at the offending position inside the string literal with the message prefixed by `JPQL:`. Typical causes are misspelled keywords (`selct`, `form`, `wehre`), a missing `from` clause or entity alias, unbalanced parentheses, a trailing comma in a select or set list, a stray quote, or SQL-only constructs such as `SELECT *`, table-style column lists or `LIMIT` that do not exist in JPQL.

The check is worth acting on because these queries are not compiled: Spring Data JPA parses and validates every declared `@Query` when the repository bean is created, and the JPA provider does the same for named queries and `createQuery` calls. A query with a syntax error therefore fails at application start-up (or, for `createQuery`, at the first invocation) with an `IllegalArgumentException` or a `QueryCreationException`, not at compile time. The language server surfaces the same class of problem while you type. Because Spring Data JPA supports SpEL inside query strings, the language server does not try to parse `#{...}` segments as JPQL; they are validated separately by the SpEL check and reported as `JAVA_SPEL_EXPRESSION_SYNTAX`. Positional (`?1`) and named (`:name`) parameters are part of the grammar and accepted.

The check applies to projects with a dependency whose name starts with `spring-data-jpa` or `spring-data-jdbc`. Non-native queries are validated with the JPQL grammar only when the project has no dependency starting with `hibernate-core`; when Hibernate is present, the more permissive HQL grammar is used instead and problems are reported as `HQL_SYNTAX`. Native queries (`nativeQuery = true`, `@NativeQuery`, Spring Data JDBC `@Query`) are covered by `SQL_SYNTAX`. Only string literals, text blocks and concatenations of compile-time constants are inspected. The check can be tuned via the `boot-java.validation.data-query` toggle and the `spring-boot.ls.problem.data-query.JPQL_SYNTAX` severity setting.

For more details, see:
- [Spring Data JPA: Using `@Query`](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) (declared queries, named queries, native queries, parameter binding and SpEL in queries)
- [Spring Framework: Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html) (syntax of the `#{...}` segments that are allowed inside a query)

## Fixes
**Fix 1: Correct misspelled keywords and complete the query**
The error position points to the first token the parser could not make sense of. Check the keyword in front of it and make sure the query has `select`, `from` with an alias, and a complete `where` clause.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("selct u form User u wehre u.emailAddress = ?1")
    List<User> findByEmailAddress(String emailAddress);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.emailAddress = ?1")
    List<User> findByEmailAddress(String emailAddress);
}
```

**Fix 2: Balance parentheses and lists**
Unclosed parentheses and dangling commas are among the most frequent errors in long queries, especially when a condition is removed or a query is split across concatenated strings.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o.id, o.total, from Order o where (o.status = :status and o.total > :minTotal")
    List<Object[]> findSummary(@Param("status") String status, @Param("minTotal") long minTotal);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o.id, o.total from Order o where (o.status = :status and o.total > :minTotal)")
    List<Object[]> findSummary(@Param("status") String status, @Param("minTotal") long minTotal);
}
```

**Fix 3: Do not write SQL in a non-native query**
`SELECT *`, table names, and database-specific clauses are not JPQL. Either rewrite the query against the entity model or mark it as native so that it is validated and executed as SQL.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT * FROM USERS WHERE EMAIL_ADDRESS = ?1 LIMIT 10")
    List<User> findByEmailAddress(String emailAddress);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.emailAddress = ?1")
    List<User> findByEmailAddress(String emailAddress, Pageable pageable);
}
```

**Fix 4: Fix the JPQL in `@NamedQuery` and `EntityManager.createQuery`**
The same grammar applies to named queries declared on the entity and to queries built programmatically. Fix the string where it is declared; the error is reported inside the literal.

*Before:*
```java
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;

@Entity
@NamedQuery(name = "User.findByEmailAddress",
        query = "select u from User where u.emailAddress = ?1")
public class User {

    @Id
    private Long id;

    private String emailAddress;
}
```

*After:*
```java
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;

@Entity
@NamedQuery(name = "User.findByEmailAddress",
        query = "select u from User u where u.emailAddress = ?1")
public class User {

    @Id
    private Long id;

    private String emailAddress;
}
```

*Note: when the project depends on Hibernate (`hibernate-core`), the same query locations are validated against the HQL grammar and reported as `HQL_SYNTAX`; native SQL queries are reported as `SQL_SYNTAX`; and errors inside `#{...}` SpEL segments are reported as `JAVA_SPEL_EXPRESSION_SYNTAX`. The grammar check does not know your entity model: a syntactically valid query that references a non-existent entity or attribute still fails at start-up.*
