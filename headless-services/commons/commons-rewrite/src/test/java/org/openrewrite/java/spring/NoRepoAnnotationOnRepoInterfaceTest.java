/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoRepoAnnotationOnRepoInterfaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoRepoAnnotationOnRepoInterface())
          .parser(JavaParser.fromJavaVersion().classpath("spring-context", "spring-beans", "spring-data"));
    }

    @DocumentExample
    @Test
    void simpleCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.stereotype.Repository;

              @Repository
              public interface MyRepo extends org.springframework.data.repository.Repository {
              }
              """,
            """

              public interface MyRepo extends org.springframework.data.repository.Repository {
              }
              """
          )
        );
    }

    @Test
    void simpleCaseWithNoParameters() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.stereotype.Repository;

              @Repository( )
              public interface MyRepo extends org.springframework.data.repository.Repository {
              }
              """,
            """

              public interface MyRepo extends org.springframework.data.repository.Repository {
              }
              """
          )
        );
    }

    @Test
    void crudRepoClass() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Optional;

              import org.springframework.data.repository.CrudRepository;
              import org.springframework.stereotype.Repository;

              @Repository
              public class MyRepo implements CrudRepository<String, String> {

                  @Override
                  public <S extends String> S save(S entity) {
                      return null;
                  }

                  @Override
                  public <S extends String> Iterable<S> saveAll(Iterable<S> entities) {
                      return null;
                  }

                  @Override
                  public Optional<String> findById(String id) {
                      return Optional.empty();
                  }

                  @Override
                  public boolean existsById(String id) {
                      return false;
                  }

                  @Override
                  public Iterable<String> findAll() {
                      return null;
                  }

                  @Override
                  public Iterable<String> findAllById(Iterable<String> ids) {
                      return null;
                  }

                  @Override
                  public long count() {
                      return 0;
                  }

                  @Override
                  public void deleteById(String id) {
                  }

                  @Override
                  public void delete(String entity) {
                  }

                  @Override
                  public void deleteAllById(Iterable<? extends String> ids) {
                  }

                  @Override
                  public void deleteAll(Iterable<? extends String> entities) {
                  }

                  @Override
                  public void deleteAll() {
                  }

              }
              """
          )
        );
    }

    @Test
    void crudRepoInterface() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.CrudRepository;
              import org.springframework.stereotype.Repository;

              @Repository
              public interface MyRepo extends CrudRepository<String, String> {

              }
              """,
            """
              import org.springframework.data.repository.CrudRepository;

              public interface MyRepo extends CrudRepository<String, String> {

              }
              """
          )
        );
    }

    @Test
    void crudRepoInterfaceWithMultipleAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.CrudRepository;
              import org.springframework.stereotype.Repository;

              @Repository
              @Deprecated
              public interface MyRepo extends CrudRepository<String, String> {

              }
              """,
            """
              import org.springframework.data.repository.CrudRepository;

              @Deprecated
              public interface MyRepo extends CrudRepository<String, String> {

              }
              """
          )
        );
    }

    @Test
    void repoAnnotationWithParameters() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.CrudRepository;
              import org.springframework.stereotype.Repository;

              @Repository("myRepoBean")
              public interface MyRepo extends CrudRepository<String, String> {

              }
              """
          )
        );
    }

    @Test
    void noRepoSubclass() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              
              import org.springframework.stereotype.Repository;

              @Repository
              public interface MyRepo extends List<String> {
              }
              """
          )
        );
    }

    @Test
    void noRepoAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.data.repository.CrudRepository;

              public interface MyRepo extends CrudRepository<String, String> {
              }
              """
          )
        );
    }

}
