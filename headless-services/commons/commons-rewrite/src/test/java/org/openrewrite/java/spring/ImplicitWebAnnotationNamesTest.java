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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ImplicitWebAnnotationNamesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ImplicitWebAnnotationNames())
          .parser(JavaParser.fromJavaVersion().classpath("spring-web"));
    }

    @DocumentExample
    @Test
    void removeUnnecessaryAnnotationArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable("id") Long id,
                                                        @PathVariable(required = false) Long p2,
                                                        @PathVariable(value = "p3") Long anotherName) {
                      System.out.println(anotherName);
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable Long id,
                                                        @PathVariable(required = false) Long p2,
                                                        @PathVariable(value = "p3") Long anotherName) {
                      System.out.println(anotherName);
                  }
              }
              """
          )
        );
    }

    @Issue("#4")
    @Test
    void removeUnnecessarySpacingInFollowingAnnotationArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(
                          @RequestParam(name = "count", defaultValue = 3) int count) {
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(
                          @RequestParam(defaultValue = 3) int count) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/340")
    @Test
    void autoFormatAfterRemovingArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable("id")Long id) {
                  }
              }
              """,
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              @RestController
              @RequestMapping("/users")
              public class UsersController {
                  @GetMapping("/{id}")
                  public ResponseEntity<String> getUser(@PathVariable Long id) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveModelAttributeOnMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.web.bind.annotation.*;
              import java.util.*;
              
              public class UsersController {
                  @ModelAttribute("types")
                  public Collection<String> populateUserTypes() {
                      return Arrays.asList("free", "premium");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRenamePathVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.http.ResponseEntity;
              import org.springframework.web.bind.annotation.*;
              
              public class UsersController {
                  public ResponseEntity<String> getUser(@PathVariable("uid") Long id,
                                                        @PathVariable(value = "another_name") Long anotherName) {
                  }
              }
              """
          )
        );
    }
}
