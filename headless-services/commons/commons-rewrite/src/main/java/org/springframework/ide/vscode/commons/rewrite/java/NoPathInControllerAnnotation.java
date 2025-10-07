/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.List;
import java.util.Optional;

import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.Assignment;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.Literal;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Range;

/**
 * Recipe to move path values from @Controller and @RestController annotations to @RequestMapping.
 * 
 * This recipe detects when a @Controller or @RestController annotation has a value parameter
 * containing a string literal with a '/' character, and moves that value to a @RequestMapping
 * annotation. If a @RequestMapping already exists, its value is updated with the moved value.
 */
public class NoPathInControllerAnnotation extends Recipe implements RangeScopedRecipe {

    private static final AnnotationMatcher CONTROLLER_ANNOTATION_MATCHER = new AnnotationMatcher(
            "@org.springframework.stereotype.Controller");
    private static final AnnotationMatcher REST_CONTROLLER_ANNOTATION_MATCHER = new AnnotationMatcher(
            "@org.springframework.web.bind.annotation.RestController");
    private static final AnnotationMatcher REQUEST_MAPPING_ANNOTATION_MATCHER = new AnnotationMatcher(
            "@org.springframework.web.bind.annotation.RequestMapping");

    private Range range;

    @Override
    public void setRange(Range range) {
        this.range = range;
    }

    @Override
    public @DisplayName String getDisplayName() {
        return "Move path from Controller annotations to RequestMapping";
    }

    @Override
    public @Description String getDescription() {
        return "Moves path values from @Controller and @RestController annotations to @RequestMapping annotations. " +
               "If a @RequestMapping already exists, its value is updated with the moved value.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RangeScopedJavaIsoVisitor<ExecutionContext>(range) {
            
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                
                // Check if this class has @Controller or @RestController with path value
                Optional<J.Literal> pathLiteral = extractPathLiteralFromControllerAnnotations(cd.getLeadingAnnotations());
                if (pathLiteral.isPresent()) {
                    cd = processControllerWithPath(cd, pathLiteral.get(), ctx);
                }
                
                return cd;
            }
            
            private Optional<J.Literal> extractPathLiteralFromControllerAnnotations(List<Annotation> annotations) {
                for (Annotation annotation : annotations) {
                    if (CONTROLLER_ANNOTATION_MATCHER.matches(annotation) || 
                        REST_CONTROLLER_ANNOTATION_MATCHER.matches(annotation)) {
                        
                        // Check if annotation has arguments
                        if (annotation.getArguments() != null) {
                            for (Expression arg : annotation.getArguments()) {
                                if (arg instanceof Assignment) {
                                    Assignment assignment = (Assignment) arg;
                                    if (assignment.getVariable() instanceof Identifier) {
                                        Identifier var = (Identifier) assignment.getVariable();
                                        if ("value".equals(var.getSimpleName()) && 
                                            assignment.getAssignment() instanceof Literal) {
                                            Literal literal = (Literal) assignment.getAssignment();
                                            String value = literal.getValue().toString();
                                            if (value.contains("/")) {
                                                return Optional.of(literal);
                                            }
                                        }
                                    }
                                } else if (arg instanceof Literal) {
                                    // Direct value without parameter name
                                    Literal literal = (Literal) arg;
                                    String value = literal.getValue().toString();
                                    if (value.contains("/")) {
                                        return Optional.of(literal);
                                    }
                                }
                            }
                        }
                    }
                }
                return Optional.empty();
            }
            
            private J.ClassDeclaration processControllerWithPath(J.ClassDeclaration classDecl, J.Literal pathLiteral, ExecutionContext ctx) {
                List<Annotation> annotations = classDecl.getLeadingAnnotations();
                
                // Check if @RequestMapping already exists
                Optional<Annotation> existingRequestMapping = annotations.stream()
                    .filter(REQUEST_MAPPING_ANNOTATION_MATCHER::matches)
                    .findFirst();
                
                if (existingRequestMapping.isPresent()) {
                    // Update existing @RequestMapping
                    return updateExistingRequestMapping(classDecl, existingRequestMapping.get(), pathLiteral, ctx);
                } else {
                    // Add new @RequestMapping
                    return addNewRequestMapping(classDecl, pathLiteral, ctx);
                }
            }
            
            private J.ClassDeclaration updateExistingRequestMapping(J.ClassDeclaration classDecl, 
                    Annotation requestMapping, J.Literal pathLiteral, ExecutionContext ctx) {
                
                // Remove path value from Controller/RestController and update RequestMapping in one pass
                List<Annotation> updatedAnnotations = ListUtils.map(classDecl.getLeadingAnnotations(), 
                    annotation -> {
                        if (CONTROLLER_ANNOTATION_MATCHER.matches(annotation) || 
                            REST_CONTROLLER_ANNOTATION_MATCHER.matches(annotation)) {
                            // Remove the path value from Controller/RestController annotation
                            return removeValueFromAnnotation(annotation);
                        } else if (REQUEST_MAPPING_ANNOTATION_MATCHER.matches(annotation)) {
                            // Update the @RequestMapping annotation with the path value
                            return updateRequestMappingValue(annotation, pathLiteral);
                        }
                        return annotation;
                    });
                
                return classDecl.withLeadingAnnotations(updatedAnnotations);
            }
            
            private J.ClassDeclaration addNewRequestMapping(J.ClassDeclaration classDecl, J.Literal pathLiteral, ExecutionContext ctx) {
                // Remove the path value from Controller/RestController annotation
                List<Annotation> updatedAnnotations = ListUtils.map(classDecl.getLeadingAnnotations(), 
                    annotation -> {
                        if (CONTROLLER_ANNOTATION_MATCHER.matches(annotation) || 
                            REST_CONTROLLER_ANNOTATION_MATCHER.matches(annotation)) {
                            return removeValueFromAnnotation(annotation);
                        }
                        return annotation;
                    });
                
                // Add new @RequestMapping annotation manually
                maybeAddImport("org.springframework.web.bind.annotation.RequestMapping");
                
                // Calculate proper spacing for the new annotation
                Space indent = Space.build("\n" + classDecl.getPrefix().getIndent(), List.of());
                boolean noAnnotations = updatedAnnotations.isEmpty();
                
                Annotation newRequestMapping = createRequestMappingAnnotation(pathLiteral, noAnnotations ? Space.EMPTY : indent);
                updatedAnnotations = ListUtils.concat(updatedAnnotations, newRequestMapping);
                
                J.ClassDeclaration cd = classDecl.withLeadingAnnotations(updatedAnnotations);
                
                // If there were no annotations before, adjust the class declaration prefix
                if (noAnnotations) {
                    cd = cd.getPadding().withKind(cd.getPadding().getKind().withPrefix(indent));
                }
                
                return cd;
            }
            
            private Annotation removeValueFromAnnotation(Annotation annotation) {
                if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
                    return annotation;
                }
                
                List<Expression> filteredArgs = ListUtils.map(annotation.getArguments(), arg -> {
                    if (arg instanceof Assignment) {
                        Assignment assignment = (Assignment) arg;
                        if (assignment.getVariable() instanceof Identifier) {
                            Identifier var = (Identifier) assignment.getVariable();
                            if ("value".equals(var.getSimpleName())) {
                                return null; // Remove this argument
                            }
                        }
                    } else if (arg instanceof Literal) {
                        return null; // Remove direct literal value
                    }
                    return arg;
                });
                
                return annotation.withArguments(filteredArgs);
            }
            
            private Annotation updateRequestMappingValue(Annotation requestMapping, J.Literal pathLiteral) {
                if (requestMapping.getArguments() == null || requestMapping.getArguments().isEmpty()) {
                    // No existing arguments, add value
                    return requestMapping.withArguments(List.of(pathLiteral));
                } else {
                    // Update existing arguments
                    List<Expression> updatedArgs = ListUtils.map(requestMapping.getArguments(), arg -> {
                        if (arg instanceof Assignment) {
                            Assignment assignment = (Assignment) arg;
                            if (assignment.getVariable() instanceof Identifier) {
                                Identifier var = (Identifier) assignment.getVariable();
                                if ("value".equals(var.getSimpleName()) || "path".equals(var.getSimpleName())) {
                                    // Update existing value/path parameter
                                	Space prefix = assignment.getAssignment().getPrefix();
                                    return assignment.withAssignment(pathLiteral.withPrefix(prefix));
                                }
                            }
                        } else if (arg instanceof Literal) {
                            // Update direct literal value
                            return pathLiteral;
                        }
                        return arg;
                    });
                    return requestMapping.withArguments(updatedArgs);
                }
            }
            
            private Annotation createRequestMappingAnnotation(J.Literal pathLiteral, Space prefix) {
                JavaType.ShallowClass requestMappingType = JavaType.ShallowClass.build(
                    "org.springframework.web.bind.annotation.RequestMapping"
                );
                
                // Create annotation without arguments first, with proper prefix for spacing
                Annotation annotation = new Annotation(
                    Tree.randomId(),
                    prefix,
                    Markers.EMPTY,
                    new Identifier(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        List.of(),
                        "RequestMapping",
                        requestMappingType,
                        null
                    ),
                    null
                );
                
                // Now add the arguments, ensuring the literal has proper spacing
                return annotation.withArguments(List.of(pathLiteral.withPrefix(Space.EMPTY)));
            }
            
        };
    }
}