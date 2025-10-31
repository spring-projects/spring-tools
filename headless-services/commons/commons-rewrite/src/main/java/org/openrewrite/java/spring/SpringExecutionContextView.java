/*
 * Copyright 2020 the original author or authors.
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

import org.openrewrite.DelegatingExecutionContext;
import org.openrewrite.ExecutionContext;

import java.util.Arrays;
import java.util.List;

public class SpringExecutionContextView extends DelegatingExecutionContext {

    private static final String DEFAULT_APPLICATION_CONFIGURATION_PATHS = "org.openrewrite.java.spring.defaultApplicationConfigurationPaths";

    public SpringExecutionContextView(ExecutionContext delegate) {
        super(delegate);
    }

    public static SpringExecutionContextView view(ExecutionContext ctx) {
        if (ctx instanceof SpringExecutionContextView) {
            return (SpringExecutionContextView) ctx;
        }
        return new SpringExecutionContextView(ctx);
    }

    /**
     * The path expressions used to find a spring boot application's default configuration file(s). The default masks used to
     * find the application's root configuration are "**&#47;application.properties", "**&#47;application.yml", and "**&#47;application.yaml"
     *
     * @param pathExpressions A list of expressions that will be used as masks to find an application's default configuration file(s)
     * @return this
     */
    public SpringExecutionContextView setDefaultApplicationConfigurationPaths(List<String> pathExpressions) {
        putMessage(DEFAULT_APPLICATION_CONFIGURATION_PATHS, pathExpressions);
        return this;
    }

    /**
     * The path expressions used to find a spring boot application's default configuration file. The default masks used to
     * find the application's root configuration are "**&#47;application.properties", "**&#47;application.yml", and "**&#47;application.yaml"
     *
     * @return A list of file paths expression that will be used to find a spring boot application's default configuration file(s)
     */
    public List<String> getDefaultApplicationConfigurationPaths() {
        return getMessage(DEFAULT_APPLICATION_CONFIGURATION_PATHS, Arrays.asList("**/application.yml", "**/application.properties", "**/application.yaml"));
    }
}
