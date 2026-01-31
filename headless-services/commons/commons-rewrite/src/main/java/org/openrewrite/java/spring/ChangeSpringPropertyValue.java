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

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import com.fasterxml.jackson.annotation.JsonCreator;

public class ChangeSpringPropertyValue extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change the value of a spring application property";
    }

    @Override
    public String getDescription() {
        return "Change spring application property values existing in either Properties or Yaml files.";
    }

    @Option(displayName = "Property key",
            description = "The name of the property key whose value is to be changed.",
            example = "management.metrics.binders.files.enabled")
    String propertyKey;

    @Option(displayName = "New value",
            description = "The new value to be used for key specified by `propertyKey`.",
            example = "management.metrics.enable.process.files")
    String newValue;

    @Option(displayName = "Old value",
            required = false,
            description = "Only change the property value if it matches the configured `oldValue`.",
            example = "false")
    @Nullable
    String oldValue;

    @Option(displayName = "Regex",
            description = "Default false. If enabled, `oldValue` will be interpreted as a Regular Expression, and capture group contents will be available in `newValue`",
            required = false)
    @Nullable
    Boolean regex;

    @Option(displayName = "Use relaxed binding",
            description = "Whether to match the `propertyKey` using [relaxed binding](https://docs.spring.io/spring-boot/docs/2.5.6/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding) " +
                          "rules. Default is `true`. Set to `false` to use exact matching.",
            required = false)
    @Nullable
    Boolean relaxedBinding;
    
    @JsonCreator
	public ChangeSpringPropertyValue(String propertyKey, String newValue, @Nullable String oldValue,
			@Nullable Boolean regex, @Nullable Boolean relaxedBinding) {
		this.propertyKey = propertyKey;
		this.newValue = newValue;
		this.oldValue = oldValue;
		this.regex = regex;
		this.relaxedBinding = relaxedBinding;
	}

	@Override
    public Validated validate() {
        return super.validate().and(
                Validated.test("oldValue", "is required if `regex` is enabled", oldValue,
                        value -> !(Boolean.TRUE.equals(regex) && StringUtils.isNullOrEmpty(value))));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Recipe changeProperties = new org.openrewrite.properties.ChangePropertyValue(propertyKey, newValue, oldValue, regex, relaxedBinding);
        Recipe changeYaml = new org.openrewrite.yaml.ChangePropertyValue(propertyKey, newValue, oldValue, regex, relaxedBinding, null);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Properties.File) {
                    tree = changeProperties.getVisitor().visit(tree, ctx);
                } else if (tree instanceof Yaml.Documents) {
                    tree = changeYaml.getVisitor().visit(tree, ctx);
                }
                return tree;
            }
        };
    }

	public String getPropertyKey() {
		return propertyKey;
	}

	public String getNewValue() {
		return newValue;
	}

	public String getOldValue() {
		return oldValue;
	}

	public Boolean getRegex() {
		return regex;
	}

	public Boolean getRelaxedBinding() {
		return relaxedBinding;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(newValue, oldValue, propertyKey, regex, relaxedBinding);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ChangeSpringPropertyValue other = (ChangeSpringPropertyValue) obj;
		return Objects.equals(newValue, other.newValue) && Objects.equals(oldValue, other.oldValue)
				&& Objects.equals(propertyKey, other.propertyKey) && Objects.equals(regex, other.regex)
				&& Objects.equals(relaxedBinding, other.relaxedBinding);
	}
    
}
