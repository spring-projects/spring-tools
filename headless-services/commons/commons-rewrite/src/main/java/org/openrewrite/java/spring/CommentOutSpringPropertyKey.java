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

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import com.fasterxml.jackson.annotation.JsonCreator;

public class CommentOutSpringPropertyKey extends Recipe {

    @Override
    public String getDisplayName() {
        return "Comment out Spring properties";
    }

    @Override
    public String getDescription() {
        return "Add comment to specified Spring properties, and comment out the property.";
    }

    @Option(displayName = "Property key",
            description = "The name of the property key to comment out.",
            example = "management.metrics.binders.files.enabled")
    String propertyKey;

    @Option(displayName = "Comment",
            description = "Comment to replace the property key.",
            example = "This property is deprecated and no longer applicable starting from Spring Boot 3.0.x")
    String comment;

    @JsonCreator
    public CommentOutSpringPropertyKey(String propertyKey, String comment) {
		this.propertyKey = propertyKey;
		this.comment = comment;
	}

	@Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Recipe changeProperties = new org.openrewrite.properties.AddPropertyComment(propertyKey, comment, true);
        Recipe changeYaml = new org.openrewrite.yaml.CommentOutProperty(propertyKey, comment, true);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(@NonNull Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof Properties.File) {
                    return changeProperties.getVisitor().visit(tree, ctx);
                } else if (tree instanceof Yaml.Documents) {
                    return changeYaml.getVisitor().visit(tree, ctx);
                }
                return tree;
            }
        };
    }

	public String getPropertyKey() {
		return propertyKey;
	}

	public void setPropertyKey(String propertyKey) {
		this.propertyKey = propertyKey;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(comment, propertyKey);
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
		CommentOutSpringPropertyKey other = (CommentOutSpringPropertyKey) obj;
		return Objects.equals(comment, other.comment) && Objects.equals(propertyKey, other.propertyKey);
	}
	
}
