/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.Jars;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HttpSecurityLambdaDslTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HttpSecurityLambdaDsl())
          .parser(JavaParser.fromJavaVersion()
            .classpath(Jars.BOOT_2_7.get()));
    }

    @DocumentExample
    @Test
    void simple() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .authorizeRequests()
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .authorizeRequests(requests -> requests
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated());
                  }
              }
              """
          )
        );
    }

    @Test
    void advanced() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .authorizeRequests()
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated()
                                      .and()
                              .formLogin()
                                      .loginPage("/login")
                                      .permitAll()
                                      .and()
                              .rememberMe();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                              .authorizeRequests(requests -> requests
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated())
                              .formLogin(login -> login
                                      .loginPage("/login")
                                      .permitAll())
                              .rememberMe(withDefaults());
                  }
              }
              """
          )
        );
    }

    @Test
    void handleDisableChain() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.csrf().disable();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.csrf(csrf -> csrf.disable());
                  }
              }
              """
          )
        );
    }

    @Test
    void retainComments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      // matcher order matters
                      http.authorizeRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      // matcher order matters
                      http.authorizeRequests(requests -> requests
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated());
                  }
              }
              """
          )
        );
    }

    @Test
    void retainFormatting() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.authorizeRequests()
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated();
              
                      http.csrf().disable();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.authorizeRequests(requests -> requests
                              .antMatchers("/blog/**").permitAll()
                              .anyRequest().authenticated());
              
                      http.csrf(csrf -> csrf.disable());
                  }
              }
              """
          )
        );
    }

    @Test
    void disableIsTerminal() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.csrf().disable()
                              .authorizeRequests()
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.csrf(csrf -> csrf.disable())
                              .authorizeRequests(requests -> requests
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated());
                  }
              }
              """
          )
        );
    }

    @Test
    void disableAfterOptions() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.csrf().ignoringAntMatchers("").disable()
                              .authorizeRequests()
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated();
                  }
              }
              """,
            """
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @EnableWebSecurity
              public class ConventionalSecurityConfig extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http.csrf(csrf -> csrf.ignoringAntMatchers("").disable())
                              .authorizeRequests(requests -> requests
                                      .antMatchers("/blog/**").permitAll()
                                      .anyRequest().authenticated());
                  }
              }
              """
          )
        );
    }
}
