## Explanations
This error is raised when a Hibernate Query Language (HQL) string embedded in Java source does not conform to the HQL grammar. The Spring Boot language server extracts the query text from a Spring Data JPA `@Query` annotation that is not marked as native, from the `query` attribute of `@NamedQuery` (both `jakarta.persistence` and `javax.persistence`), and from the first string argument of `jakarta.persistence.EntityManager.createQuery(...)`, and parses it with an ANTLR grammar for HQL. Any lexer or parser error is reported at the offending position inside the string literal with the message prefixed by `HQL:`. Typical causes are misspelled keywords (`selct`, `form`, `wehre`), a missing `from` clause, unbalanced parentheses, a trailing comma in a select or set list, a stray quote, or plain SQL such as `SELECT *` with table and column names.

HQL is a superset of JPQL: everything that is valid JPQL is valid HQL, and Hibernate additionally understands, for example, `limit`/`offset` (the bundled grammar accepts them only after an `order by` clause), the implicit `select` (a query may start directly with `from`), and a number of extra functions and operators. The language server therefore uses the HQL grammar whenever the project depends on Hibernate, so that legitimate Hibernate-only syntax is not flagged. The check is worth acting on because query strings are not compiled: Spring Data JPA parses every declared `@Query` when the repository bean is created, and Hibernate parses named queries and `createQuery` strings at bootstrap or first use. A syntax error therefore surfaces as a `QueryCreationException` or `IllegalArgumentException` at application start-up rather than at compile time. SpEL segments (`#{...}`) inside a query are skipped by the HQL grammar and validated separately; their errors are reported as `JAVA_SPEL_EXPRESSION_SYNTAX`.

The check applies to projects with a dependency whose name starts with `spring-data-jpa` or `spring-data-jdbc` and, for this particular code, a dependency starting with `hibernate-core`. Without Hibernate on the classpath the stricter JPQL grammar is used and problems are reported as `JPQL_SYNTAX`. Native queries (`nativeQuery = true`, `@NativeQuery`, Spring Data JDBC `@Query`) are covered by `SQL_SYNTAX`. Only string literals, text blocks and concatenations of compile-time constants are inspected. The check can be tuned via the `boot-java.validation.data-query` toggle and the `spring-boot.ls.problem.data-query.HQL_SYNTAX` severity setting.

For more details, see:
- [Spring Data JPA: Using `@Query`](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) (declared queries, named queries, native queries, parameter binding and SpEL in queries)
- [Spring Framework: Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html) (syntax of the `#{...}` segments that are allowed inside a query)

## Fixes
**Fix 1: Correct misspelled keywords**
The error position points to the first token the parser could not make sense of; the mistake is usually in the keyword immediately before it.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u form User u wehre u.emailAddress = ?1")
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
Unclosed parentheses and dangling commas are frequent in long queries, in particular when a condition is removed or the query is assembled from several concatenated literals.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o where (o.status = :status and o.total > :minTotal "
            + "order by o.created desc,")
    List<Order> findRecent(@Param("status") String status, @Param("minTotal") long minTotal);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o where (o.status = :status and o.total > :minTotal) "
            + "order by o.created desc")
    List<Order> findRecent(@Param("status") String status, @Param("minTotal") long minTotal);
}
```

**Fix 3: Write HQL against entities, or declare the query as native**
`SELECT *` with table and column names is SQL, not HQL. Either query the entity model or mark the query as native so that it is validated and executed as SQL.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT * FROM USERS WHERE EMAIL_ADDRESS = ?1")
    List<User> findByEmailAddress(String emailAddress);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

public interface UserRepository extends JpaRepository<User, Long> {

    @NativeQuery("SELECT * FROM USERS WHERE EMAIL_ADDRESS = ?1")
    List<User> findByEmailAddress(String emailAddress);
}
```

**Fix 4: Fix the query in `@Modifying` statements**
Bulk `update` and `delete` statements follow the same grammar. A missing `set` keyword or `where` clause separator is reported at the point where the parser gives up.

*Before:*
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("update User u u.firstname = ?1 where u.lastname = ?2")
    int setFixedFirstnameFor(String firstname, String lastname);
}
```

*After:*
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("update User u set u.firstname = ?1 where u.lastname = ?2")
    int setFixedFirstnameFor(String firstname, String lastname);
}
```

*Note: without `hibernate-core` on the classpath the same query locations are validated against the stricter JPQL grammar and reported as `JPQL_SYNTAX`; native SQL queries are reported as `SQL_SYNTAX`; and errors inside `#{...}` SpEL segments are reported as `JAVA_SPEL_EXPRESSION_SYNTAX`. The grammar check does not know your entity model: a syntactically valid query that references a non-existent entity or attribute still fails at start-up.*
