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
package org.openrewrite.java.spring.security5;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.Jars;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * @author Alex Boyko
 */
class WebSecurityConfigurerAdapterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WebSecurityConfigurerAdapter())
          .parser(JavaParser.fromJavaVersion()
            .classpath(Jars.BOOT_2_7.get()));
    }

    @DocumentExample
    @Test
    void configureHttpSecurityMethod() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example.websecuritydemo;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                  }
              
                  void someMethod() {}
              
              }
              """,
            """
              package com.example.websecuritydemo;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              public class SecurityConfiguration {
              
                  @Bean
                  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                      return http.build();
                  }
              
                  void someMethod() {}
              
              }
              """
          )
        );
    }

    @Test
    void noConfigurationAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example.websecuritydemo;
              
              import static org.springframework.security.config.Customizer.withDefaults;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                  }
              
              }
              """
          )
        );
    }

    @Test
    void configureWebSecurityMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.WebSecurity;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  public void configure(WebSecurity web) {
                      web.ignoring().antMatchers("/ignore1", "/ignore2");
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.WebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
              
              @Configuration
              public class SecurityConfiguration {
              
                  @Bean
                  WebSecurityCustomizer webSecurityCustomizer() {
                      return (web) -> {
                          web.ignoring().antMatchers("/ignore1", "/ignore2");
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void configureAuthManagerMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
              import org.springframework.security.ldap.userdetails.PersonContextMapper;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(AuthenticationManagerBuilder auth) {
                      auth
                          .ldapAuthentication()
                          .userDetailsContextMapper(new PersonContextMapper())
                          .userDnPatterns("uid={0},ou=people")
                          .contextSource()
                          .port(0);
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
              import org.springframework.security.ldap.userdetails.PersonContextMapper;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  /*~~(Migrate manually based on https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter)~~>*/@Override
                  protected void configure(AuthenticationManagerBuilder auth) {
                      auth
                          .ldapAuthentication()
                          .userDetailsContextMapper(new PersonContextMapper())
                          .userDnPatterns("uid={0},ou=people")
                          .contextSource()
                          .port(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void overrideUnapplicableMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.springframework.security.config.Customizer.withDefaults;
              
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.authentication.AuthenticationManager;
              import org.springframework.security.core.userdetails.UserDetailsService;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                  }
              
                  @Override
                  public UserDetailsService userDetailsServiceBean() throws Exception  {
                      return null;
                  }
              
                  @Override
                  public AuthenticationManager authenticationManagerBean() throws Exception {
                      return null;
                  }
              }
              """,
            """
              import static org.springframework.security.config.Customizer.withDefaults;
              
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.authentication.AuthenticationManager;
              import org.springframework.security.core.userdetails.UserDetailsService;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                  }
              
                  /*~~(Migrate manually based on https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter)~~>*/@Override
                  public UserDetailsService userDetailsServiceBean() throws Exception  {
                      return null;
                  }
              
                  /*~~(Migrate manually based on https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter)~~>*/@Override
                  public AuthenticationManager authenticationManagerBean() throws Exception {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void unapplicableMethodInvocation() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.springframework.security.config.Customizer.withDefaults;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) {
                      System.out.println(getApplicationContext());
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                  }
              
                  public void someMethod() {}
              }
              """,
            """
              import static org.springframework.security.config.Customizer.withDefaults;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              public class SecurityConfiguration {
              
                  @Bean
                  SecurityFilterChain filterChain(HttpSecurity http) {
                      System.out.println(getApplicationContext());
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                      return http.build();
                  }
              
                  public void someMethod() {}
              }
              """
          )
        );
    }

    @Test
    void configureHttpSecurityMethodWithNotApplicableMethodInNonStaticInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.springframework.security.config.Customizer.withDefaults;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
              
                  @Override
                  protected void configure(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                  }
              
                  @Configuration
                  public class InnerSecurityConfiguration {
                      protected void configure() throws Exception {
                          System.out.println(getApplicationContext());
                      }
                  }
              }
              """,
            """
              import static org.springframework.security.config.Customizer.withDefaults;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.web.SecurityFilterChain;
              
              @Configuration
              public class SecurityConfiguration {
              
                  @Bean
                  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                      http
                          .authorizeHttpRequests((authz) -> authz
                              .anyRequest().authenticated()
                          )
                          .httpBasic(withDefaults());
                      return http.build();
                  }
              
                  @Configuration
                  public class InnerSecurityConfiguration {
                      protected void configure() throws Exception {
                          System.out.println(getApplicationContext());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleClasses() {
        rewriteRun(
          //language=java
          java(
            """
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.core.annotation.Order;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
            import org.springframework.security.core.userdetails.UserDetailsService;
            import org.springframework.security.provisioning.InMemoryUserDetailsManager;
            
            @EnableWebSecurity
            public class MultiHttpSecurityConfig {
                @Bean
                public UserDetailsService userDetailsService() throws Exception {
                    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
                    return manager;
                }
            
                @Configuration
                @Order(1)
                public static class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
                    protected void configure(HttpSecurity http) throws Exception {
                        http
                            .antMatcher("/api/**")
                            .authorizeRequests()
                                .anyRequest().hasRole("ADMIN")
                                .and()
                            .httpBasic();
                    }
                }
            
                @Configuration
                public static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
            
                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        http
                            .authorizeRequests()
                                .anyRequest().authenticated()
                                .and()
                            .formLogin();
                    }
                }
            }
            """,
            """
            import org.springframework.context.annotation.Bean;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.core.userdetails.UserDetailsService;
            import org.springframework.security.provisioning.InMemoryUserDetailsManager;
            import org.springframework.security.web.SecurityFilterChain;
            
            @EnableWebSecurity
            public class MultiHttpSecurityConfig {
                @Bean
                public UserDetailsService userDetailsService() throws Exception {
                    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
                    return manager;
                }
            
                @Bean
                SecurityFilterChain apiWebSecurityConfigurationSecurityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .antMatcher("/api/**")
                            .authorizeRequests()
                            .anyRequest().hasRole("ADMIN")
                            .and()
                            .httpBasic();
                    return http.build();
                }
            
                @Bean
                SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http) throws Exception {
                    http
                            .authorizeRequests()
                            .anyRequest().authenticated()
                            .and()
                            .formLogin();
                    return http.build();
                }
            }
            """
          )
        );
    }

    @Test
    void multipleClassesNoFlattening() {
        rewriteRun(
          //language=java
          java(
              """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.core.annotation.Order;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.provisioning.InMemoryUserDetailsManager;
              
              @EnableWebSecurity
              public class MultiHttpSecurityConfig {
                  private int a;
              
                  @Bean
                  public UserDetailsService userDetailsService() throws Exception {
                      InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
                      return manager;
                  }
              
                  @Configuration
                  @Order(1)
                  public static class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
                      private String a;
                      protected void configure(HttpSecurity http) throws Exception {
                          http
                              .antMatcher("/api/**")
                              .authorizeRequests()
                                  .anyRequest().hasRole("ADMIN")
                                  .and()
                              .httpBasic();
                      }
                  }
              
                  @Configuration
                  public static class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
              
                      @Override
                      protected void configure(HttpSecurity http) throws Exception {
                          http
                              .authorizeRequests()
                                  .anyRequest().authenticated()
                                  .and()
                              .formLogin();
                      }
                  }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.core.annotation.Order;
              import org.springframework.security.config.annotation.web.builders.HttpSecurity;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
              import org.springframework.security.core.userdetails.UserDetailsService;
              import org.springframework.security.provisioning.InMemoryUserDetailsManager;
              import org.springframework.security.web.SecurityFilterChain;
              
              @EnableWebSecurity
              public class MultiHttpSecurityConfig {
                  private int a;
              
                  @Bean
                  public UserDetailsService userDetailsService() throws Exception {
                      InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
                      return manager;
                  }
              
                  @Configuration
                  @Order(1)
                  public static class ApiWebSecurityConfigurationAdapter {
                      private String a;
              
                      @Bean
                      SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                          http
                              .antMatcher("/api/**")
                              .authorizeRequests()
                                  .anyRequest().hasRole("ADMIN")
                                  .and()
                              .httpBasic();
                          return http.build();
                      }
                  }
              
                  @Bean
                  SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http) throws Exception {
                      http
                              .authorizeRequests()
                              .anyRequest().authenticated()
                              .and()
                              .formLogin();
                      return http.build();
                  }
              }
              """
          )
        );
    }

    @Test
    void inMemoryConfig() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.websecuritydemo;
              
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.core.userdetails.User;
              import org.springframework.security.core.userdetails.UserDetails;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
                  @Override
                    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
                      UserDetails user = User.withDefaultPasswordEncoder().username("user").password("password").roles("USER")
                          .build();
                        auth.inMemoryAuthentication().withUser(user);
                  }
              }
              """,
            """
              package com.example.websecuritydemo;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.core.userdetails.User;
              import org.springframework.security.core.userdetails.UserDetails;
              import org.springframework.security.provisioning.InMemoryUserDetailsManager;
              
              @Configuration
              public class SecurityConfiguration {
                  @Bean
                  InMemoryUserDetailsManager inMemoryAuthManager() throws Exception {
                      UserDetails user = User.withDefaultPasswordEncoder().username("user").password("password").roles("USER")
                          .build();
                      return new InMemoryUserDetailsManager(user);
                  }
              }
              """
          )
        );
    }

    @Test
    void inMemoryConfigWithUserBuilder() {
        rewriteRun(
            //language=java
          java(
            """
              package com.example.websecuritydemo;
              
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              import org.springframework.security.core.userdetails.User;
              import org.springframework.security.core.userdetails.User.UserBuilder;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
                  @Override
                    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
                      UserBuilder builder = User.withDefaultPasswordEncoder().username("user").password("password").roles("USER");
                        auth.inMemoryAuthentication().withUser(builder);
                  }
              }
              """,
            """
              package com.example.websecuritydemo;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.core.userdetails.User;
              import org.springframework.security.core.userdetails.User.UserBuilder;
              import org.springframework.security.provisioning.InMemoryUserDetailsManager;
              
              @Configuration
              public class SecurityConfiguration {
                  @Bean
                  InMemoryUserDetailsManager inMemoryAuthManager() throws Exception {
                      UserBuilder builder = User.withDefaultPasswordEncoder().username("user").password("password").roles("USER");
                      return new InMemoryUserDetailsManager(builder.build());
                  }
              }
              """
          )
        );
    }

    @Test
    void inMemoryConfigWithUserString() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.websecuritydemo;
              
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
              import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
              
              @Configuration
              public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
                  @Override
                    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
                        auth.inMemoryAuthentication().withUser("user");
                  }
              }
              """,
            """
              package com.example.websecuritydemo;
              
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.core.userdetails.User;
              import org.springframework.security.provisioning.InMemoryUserDetailsManager;
              
              @Configuration
              public class SecurityConfiguration {
                  @Bean
                  InMemoryUserDetailsManager inMemoryAuthManager() throws Exception {
                      return new InMemoryUserDetailsManager(User.builder().username("user").build());
                  }
              }
              """
          )
        );
    }
}
