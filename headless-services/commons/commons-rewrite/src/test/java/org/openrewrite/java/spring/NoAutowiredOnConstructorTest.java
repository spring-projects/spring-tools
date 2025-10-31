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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoAutowiredOnConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoAutowiredOnConstructor())
          .parser(JavaParser.fromJavaVersion().classpath("spring-beans", "spring-boot", "spring-context", "spring-core"));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeLeadingAutowiredAnnotation() {
        //language=java
        rewriteRun(
          java("@org.springframework.stereotype.Component public class TestSourceA {}"),
          java("@org.springframework.stereotype.Component public class TestSourceB {}"),
          java("@org.springframework.stereotype.Component public class TestSourceC {}"),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              
              @Autowired
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  @Autowired
                  public TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Autowired;
              
              @Autowired
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  public TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeLeadingAutowiredAnnotationNoModifiers() {
        //language=java
        rewriteRun(
          java("@org.springframework.stereotype.Component public class TestSourceA {}"),
          java("@org.springframework.stereotype.Component public class TestSourceB {}"),
          java("@org.springframework.stereotype.Component public class TestSourceC {}"),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  @Autowired
                  TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class TestConfiguration {
                  private final TestSourceA testSourceA;
                  private TestSourceB testSourceB;
              
                  @Autowired
                  private TestSourceC testSourceC;
              
                  TestConfiguration(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              
                  @Autowired
                  public void setTestSourceB(TestSourceB testSourceB) {
                      this.testSourceB = testSourceB;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeAutowiredWithMultipleAnnotation() {
        //language=java
        rewriteRun(
          java("@org.springframework.stereotype.Component public class TestSourceA {}"),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Autowired
                  @Deprecated
                  @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated
                  @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """
          ),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated
                  @Autowired
                  @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated
                  @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """
          ),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated
                  @Qualifier
                  @Autowired
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated
                  @Qualifier
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void removeAutowiredWithMultipleInLineAnnotation() {
        //language=java
        rewriteRun(
          java("@org.springframework.stereotype.Component public class TestSourceA {}"),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Autowired @Deprecated @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos1 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated @Qualifier
                  public AnnotationPos1(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """
          ),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated @Autowired @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos2 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated @Qualifier
                  public AnnotationPos2(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """
          ),
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated @Qualifier @Autowired
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Qualifier;
              
              public class AnnotationPos3 {
                  private final TestSourceA testSourceA;
              
                  @Deprecated @Qualifier
                  public AnnotationPos3(TestSourceA testSourceA) {
                      this.testSourceA = testSourceA;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void oneNamePrefixAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @Autowired DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void multipleNamePrefixAnnotationsPos1() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @Autowired @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void multipleNamePrefixAnnotationsPos2() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @SuppressWarnings("") @Autowired @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @SuppressWarnings("") @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void multipleNamePrefixAnnotationsPos3() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
              import org.springframework.beans.factory.annotation.Autowired;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @SuppressWarnings("") @Deprecated @Autowired DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """,
            """
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  public @SuppressWarnings("") @Deprecated DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    void keepAutowiredAnnotationsWhenMultipleConstructorsExist() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.core.io.Resource;
              import java.io.PrintStream;
              
              public class MyAppResourceService {
                  private final Resource someResource;
                  private final PrintStream printStream;
              
                  public MyAppResourceService(Resource someResource) {
                      this.someResource = someResource;
                      this.printStream = System.out;
                  }
              
                  @Autowired
                  public MyAppResourceService(Resource someResource, PrintStream printStream) {
                      this.someResource = someResource;
                      this.printStream = printStream;
                  }
              }
              """
          )
        );
    }

    @Test
    void optionalAutowiredAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;

                  public DatabaseConfiguration(@Autowired(required = false) DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Test
    void noAutowiredAnnotations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Primary;
              import javax.sql.DataSource;
              
              public class DatabaseConfiguration {
                  private final DataSource dataSource;
              
                  @Primary
                  public DatabaseConfiguration(DataSource dataSource) {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreConfigurationProperties() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.core.env.Environment;
              @ConfigurationProperties
              public class ArchivingWorkflowListenerProperties {
                  private final Environment environment;
                  @Autowired
                  public ArchivingWorkflowListenerProperties(Environment environment) {
                      this.environment = environment;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/479")
    void ignoreLombokConstructors() {
        //language=java
        rewriteRun(
          java(
            """
              package lombok;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;
              import java.lang.annotation.Target;

              @Target({ElementType.TYPE})
              @Retention(RetentionPolicy.SOURCE)
              public @interface NoArgsConstructor {
              }
              """
          ),
          java(
            """
              import lombok.NoArgsConstructor;
              import org.springframework.beans.factory.annotation.Autowired;
              import org.springframework.core.env.Environment;
              @NoArgsConstructor
              public class ArchivingWorkflowListenerProperties {
                  private final Environment environment;
                  @Autowired
                  public ArchivingWorkflowListenerProperties(Environment environment) {
                      this.environment = environment;
                  }
              }
              """
          )
        );
    }
}
