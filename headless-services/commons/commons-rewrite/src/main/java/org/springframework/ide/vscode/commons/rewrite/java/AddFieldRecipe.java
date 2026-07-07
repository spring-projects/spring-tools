/*******************************************************************************
 * Copyright (c) 2017, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Udayani V
 */
public class AddFieldRecipe extends Recipe {

	@Override
	public String getDisplayName() {
		return "Add field";
	}

	@Override
	public String getDescription() {
		return "Add field desccription.";
	}

	@Option(description = "Fully qualified name for the field type")
	String fullyQualifiedName;
	
	@Option(description = "Class fully qualified name", required = true)
	@NonNull
	String classFqName;
	
	@Option(description = "Name of the field")
	String fieldName;
	
	transient JavaType.FullyQualified fullyQualifiedType;

	@JsonCreator
	public AddFieldRecipe(
			@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedName,
			@NonNull @JsonProperty("classFqName") String classFqName,
			@Nullable @JsonProperty("fieldName") String fieldName) {
		this.fullyQualifiedName = fullyQualifiedName;
		fullyQualifiedType = JavaType.ShallowClass.build(fullyQualifiedName);
		this.classFqName = classFqName;
		this.fieldName = fieldName == null ? getFieldName(fullyQualifiedType) : fieldName;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {

		return new JavaIsoVisitor<ExecutionContext>() {

			String fieldType = getFieldType(fullyQualifiedType);
			
			@Override
			public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
				if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {

					// Check if the class already has the field
					boolean hasOwnerRepoField = classDecl.getBody().getStatements().stream()
							.filter(J.VariableDeclarations.class::isInstance).map(J.VariableDeclarations.class::cast)
							.anyMatch(varDecl -> varDecl.getTypeExpression() != null
									&& varDecl.getTypeExpression().toString().equals(fieldType));

					if (!hasOwnerRepoField) {
						J.VariableDeclarations newFieldDecl = new J.VariableDeclarations(
								Tree.randomId(),
								Space.build("\n\n", Collections.emptyList()),
								Markers.EMPTY,
								Collections.emptyList(),
								List.of(
										new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, "private", J.Modifier.Type.Private, Collections.emptyList()),
										new J.Modifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, "final", J.Modifier.Type.Final, Collections.emptyList())
								),
								TypeTree.build(fieldType),
								null,
								Collections.emptyList(),
								List.of(JRightPadded.build(new J.VariableDeclarations.NamedVariable(
										Tree.randomId(), 
										Space.EMPTY, 
										Markers.EMPTY,
										new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), fieldName, fullyQualifiedType, null),
										Collections.emptyList(),
										null,
										null
								)))
						);
						Statement formattedNewFieldDecl = autoFormat(classDecl.getBody().withStatements(List.of(newFieldDecl)), ctx, getCursor()).getStatements().get(0);
						List<Statement> newStatements = new ArrayList<>(classDecl.getBody().getStatements().size() + 1);
						newStatements.add(formattedNewFieldDecl);
						newStatements.addAll(classDecl.getBody().getStatements());
						classDecl = classDecl.withBody(classDecl.getBody().withStatements(newStatements));
						

						maybeAddImport(fullyQualifiedType.getFullyQualifiedName(), false);
					}
					return classDecl;
				}
				classDecl = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
				return classDecl;
			}
		};
	}
	
	private static String getFieldName(JavaType.FullyQualified fullyQualifiedType) {
		return Character.toLowerCase(fullyQualifiedType.getClassName().charAt(0)) + fullyQualifiedType.getClassName().substring(1);
	}
	
	private static String getFieldType(JavaType.FullyQualified fullyQualifiedType) {
		if(fullyQualifiedType.getOwningClass() != null) {
			String[] parts = fullyQualifiedType.getFullyQualifiedName().split("\\.");
	        if (parts.length < 2) {
	            return fullyQualifiedType.getClassName();
	        }
	        return parts[parts.length - 2] + "." + parts[parts.length - 1];
		}
			
        return fullyQualifiedType.getClassName();
    }
}
