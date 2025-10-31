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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ChangeSpringPropertyValueTest implements RewriteTest {
    @DocumentExample
    @Test
    void propFile() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null,  null)),
          properties("server.port=8080", "server.port=8081")
        );
    }

    @Test
    void yamlDotSeparated() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null,  null)),
          yaml("server.port: 8080", "server.port: 8081")
        );
    }

    @Test
    void yamlIndented() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null,  null)),
          yaml("server:\n  port: 8080", "server:\n  port: 8081")
        );
    }

    @Test
    void regex() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "80$1", "^([0-9]{2})$", true,  null)),
          properties("server.port=53", "server.port=8053"),
          yaml("server.port: 53", "server.port: 8053")
        );
    }
}
