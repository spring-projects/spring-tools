/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class BeanMethodsNotPublicTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BeanMethodsNotPublic())
          .parser(JavaParser.fromJavaVersion().classpath("spring-context"));
    }

    @Test
    void removePublicModifierFromBeanMethods() {
        //language=java
        rewriteRun(
          java(
            """
              package a.b.c;
              public class DataSource {}
              """),
          java(
            """
              import a.b.c.DataSource;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Primary;

              public class DatabaseConfiguration {

                  // primary comments
                  @Primary
                  @Bean
                  public DataSource dataSource() {
                      return new DataSource();
                  }

                  @Bean // comments
                  public final DataSource dataSource2() {
                      return new DataSource();
                  }

                  @Bean
                  // comments
                  public static DataSource dataSource3() {
                      return new DataSource();
                  }
              }
              """,
            """
              import a.b.c.DataSource;
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Primary;

              public class DatabaseConfiguration {

                  // primary comments
                  @Primary
                  @Bean
                  DataSource dataSource() {
                      return new DataSource();
                  }

                  @Bean // comments
                  final DataSource dataSource2() {
                      return new DataSource();
                  }

                  @Bean
                  // comments
                  static DataSource dataSource3() {
                      return new DataSource();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/70")
    @Test
    void leaveOverridesUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              interface A {
                  void a();
              }
              """
          ),
          java(
            """
              class B {
                  public void b() {}
              }
              """
          ),
          java(
            """
              import org.springframework.context.annotation.Bean;

              public class PublicBeans extends B implements A {
                  @Bean
                  public void a() {}

                  @Bean
                  public void b() {}
              }
              """
          )
        );
    }
}
