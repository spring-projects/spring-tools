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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PreciseBeanTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreciseBeanType())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-context", "spring-boot"));
    }

    @DocumentExample
    @Test
    void simplestCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import java.util.List;
              import java.util.ArrayList;
                            
              class A {
                  @Bean
                  List bean1() {
                      return new ArrayList();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import java.util.ArrayList;
                            
              class A {
                  @Bean
                  ArrayList bean1() {
                      return new ArrayList();
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import java.util.List;
              import java.util.ArrayList;
              import java.util.Stack;
              import java.util.concurrent.Callable;
              
              class A {
                  @Bean
                  List bean1() {
                      Callable<List> callable = () -> {
                          return new ArrayList();
                      };
                      return new Stack();
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import java.util.List;
              import java.util.ArrayList;
              import java.util.Stack;
              import java.util.concurrent.Callable;
              
              class A {
                  @Bean
                  Stack bean1() {
                      Callable<List> callable = () -> {
                          return new ArrayList();
                      };
                      return new Stack();
                  }
              }
              """
          )
        );
    }

    @Test
    void notApplicableCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import java.util.ArrayList;
              
              class A {
                  @Bean
                  ArrayList bean1() {
                      return new ArrayList();
                  }
              }
              """
          )
        );
    }

    @Test
    void notApplicablePrimitiveTypeCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              
              class A {
                  @Bean
                  String bean1() {
                      return "hello";
                  }
              }
              """
          )
        );
    }
}
