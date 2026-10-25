## Explanations
This error is raised when a native SQL query string embedded in Java source does not conform to the SQL grammar of the database the project uses. The Spring Boot language server extracts the query text from a Spring Data JPA `@Query` annotation with `nativeQuery = true`, from the `@NativeQuery` annotation, and from a Spring Data JDBC `@Query` annotation (which is always native SQL), and parses it with an ANTLR grammar for the detected SQL dialect. Any lexer or parser error is reported at the offending position inside the string literal, prefixed with the dialect name, for example `MySQL: ...` or `PostgreSQL: ...`. Typical causes are misspelled keywords (`SELCT`, `FORM`, `WEHRE`), a missing `FROM` clause, unbalanced parentheses, a trailing comma in a column list, a stray quote, or JPQL written where SQL is expected (entity names with property paths such as `u.emailAddress` instead of table and column names).

The dialect is chosen from the JDBC driver on the classpath: a dependency whose name starts with `mysql-connector` or `mariadb-java-client` selects the MySQL grammar, and a dependency starting with `postgresql` or `h2` selects the PostgreSQL grammar. If none of these drivers is present, native queries are not validated at all. The check is worth acting on because native query strings are not compiled and, unlike JPQL, are typically not even parsed by the persistence provider until the statement is executed. A syntax error therefore surfaces as a `BadSqlGrammarException` or `SQLGrammarException` the first time the repository method is called, possibly long after start-up. SpEL segments (`#{...}`) inside a query are skipped by the SQL grammar and validated separately; their errors are reported as `JAVA_SPEL_EXPRESSION_SYNTAX`. Named parameters (`:lastname`) and JPA positional parameters (`?1`) are accepted by the grammars.

The check applies to projects with a dependency whose name starts with `spring-data-jpa` or `spring-data-jdbc`. Only string literals, text blocks and concatenations of compile-time constants are inspected; a `nativeQuery` attribute must be a boolean constant to be recognised. Non-native queries are covered by `JPQL_SYNTAX` or `HQL_SYNTAX`. The check can be tuned via the `boot-java.validation.data-query` toggle and the `spring-boot.ls.problem.data-query.SQL_SYNTAX` severity setting.

For more details, see:
- [Spring Data JPA: Using `@Query`, Native Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) (`nativeQuery = true`, `@NativeQuery`, parameter binding and SpEL in queries)
- [Spring Data JDBC: Query Methods](https://docs.spring.io/spring-data/relational/reference/jdbc/query-methods.html) (`@Query` with named parameters, `@Modifying`, SpEL in JDBC queries)
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

    @Query(value = "SELCT * FORM USERS WHERE EMAIL_ADDRESS = ?1", nativeQuery = true)
    List<User> findByEmailAddress(String emailAddress);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM USERS WHERE EMAIL_ADDRESS = ?1", nativeQuery = true)
    List<User> findByEmailAddress(String emailAddress);
}
```

**Fix 2: Balance parentheses and column lists**
Unclosed parentheses and dangling commas are frequent in long statements, especially when a column or condition is removed or the query is assembled from several concatenated literals.

*Before:*
```java
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("SELECT id, first_name, last_name, FROM person WHERE (last_name = :lastname AND active = TRUE")
    List<Person> findActiveByLastname(@Param("lastname") String lastname);
}
```

*After:*
```java
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("SELECT id, first_name, last_name FROM person WHERE (last_name = :lastname AND active = TRUE)")
    List<Person> findActiveByLastname(@Param("lastname") String lastname);
}
```

**Fix 3: Write SQL in native queries, or drop the `nativeQuery` flag**
A native query is sent to the database as-is, so it must use table and column names. If the query is really JPQL, remove `nativeQuery = true` so that it is validated and executed as JPQL or HQL instead.

*Before:*
```java
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

public interface UserRepository extends JpaRepository<User, Long> {

    @NativeQuery("select u from User u where u.emailAddress = ?1")
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

**Fix 4: Complete `UPDATE` and `DELETE` statements in `@Modifying` queries**
Modifying statements follow the same grammar. A missing `SET` keyword or an incomplete `WHERE` clause is reported at the point where the parser gives up.

*Before:*
```java
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends CrudRepository<Person, Long> {

    @Modifying
    @Query("UPDATE person first_name = :name WHERE id = ")
    boolean updateName(@Param("id") Long id, @Param("name") String name);
}
```

*After:*
```java
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends CrudRepository<Person, Long> {

    @Modifying
    @Query("UPDATE person SET first_name = :name WHERE id = :id")
    boolean updateName(@Param("id") Long id, @Param("name") String name);
}
```

*Note: the grammar is selected from the JDBC driver dependency, so a query written for one database may be flagged after switching drivers (for example MySQL backtick quoting or `LIMIT x, y` when moving to PostgreSQL). Non-native queries are reported as `JPQL_SYNTAX` or `HQL_SYNTAX`, and errors inside `#{...}` SpEL segments as `JAVA_SPEL_EXPRESSION_SYNTAX`. The grammar check does not know your schema: a syntactically valid statement that references a non-existent table or column still fails when executed.*
