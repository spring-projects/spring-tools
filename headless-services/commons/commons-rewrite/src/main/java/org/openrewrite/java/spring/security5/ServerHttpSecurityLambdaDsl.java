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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;

import java.util.Arrays;
import java.util.Collection;

public final class ServerHttpSecurityLambdaDsl extends Recipe {

    private static final String FQN_SERVER_HTTP_SECURITY = "org.springframework.security.config.web.server.ServerHttpSecurity";

    private static final Collection<String> APPLICABLE_METHOD_NAMES = Arrays.asList(
            "anonymous", "authorizeExchange", "cors", "csrf", "exceptionHandling", "formLogin",
            "headers", "httpBasic", "logout", "oauth2Client", "oauth2Login", "oauth2ResourceServer",
            "redirectToHttps", "requestCache", "x509");

    @Override
    public String getDisplayName() {
        return "Convert `ServerHttpSecurity` chained calls into Lambda DSL";
    }

    @Override
    public String getDescription() {
        return "Converts `ServerHttpSecurity` chained call from Spring Security pre 5.2.x into new lambda DSL style calls and removes `and()` methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(FQN_SERVER_HTTP_SECURITY, true),
                new ConvertToSecurityDslVisitor<>(FQN_SERVER_HTTP_SECURITY, APPLICABLE_METHOD_NAMES)
        );
    }

}
