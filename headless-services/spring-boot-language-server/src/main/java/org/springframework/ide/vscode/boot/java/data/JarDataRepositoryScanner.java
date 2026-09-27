/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.commands.StructureMember;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;

/**
 * The JAR/bytecode counterpart of {@link DataRepositoryIndexer}: reads the "query methods" a
 * Spring Data repository interface contributes directly from a Jandex {@link ClassInfo}, into the
 * same {@link StructureMember} shape {@code IndexStructureElements.membersOf} builds from the live
 * index. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Deliberately does not re-walk the interface hierarchy to decide whether a class is a
 * repository - that is already exactly what {@code doesImplement} answers, from the same
 * {@code supertypes} set the class's own stereotype grouping already uses
 * ({@code JarStereotypeScanner.supertypesOf}), so there is only one place that walk happens either
 * way. What is genuinely specific to this case, and so unavoidably duplicated across the AST-based
 * and JAR-based sides, is a one-line rule with essentially no room to drift: a query method is any
 * non-{@code default} method of the interface ({@code DataRepositoryIndexer.identifyQueryMethods}'
 * {@code Modifier.DEFAULT} check, {@link MethodInfo#isDefault()} here).
 *
 * <p>The resolved SQL/JPQL query string a method's {@code @Query} annotation or Spring Data's
 * AOT-generated metadata provides is not reconstructed here - the AOT metadata in particular is a
 * build output that does not generally ship inside a plain dependency JAR, and (per investigation)
 * is not actually wired into the Logical Structure tree even for a workspace project today, so
 * there is nothing this is regressing.
 *
 * @author Martin Lippert
 */
public class JarDataRepositoryScanner {

	/**
	 * @param element the same class's already-built element, whose {@code doesImplement} answers
	 *        from the one {@code supertypes} walk shared with class-level stereotype grouping
	 * @return empty when the class is not a Spring Data repository, or is marked
	 *         {@code @NoRepositoryBean}
	 */
	public static List<StructureMember> membersOf(ClassInfo classInfo, StereotypeClassElement element) {
		if (!element.doesImplement(Constants.REPOSITORY_TYPE) || element.isAnnotatedWith(Annotations.NO_REPO_BEAN)) {
			return List.of();
		}

		List<StructureMember> result = new ArrayList<>();

		for (MethodInfo method : classInfo.methods()) {
			if (!method.isDefault()) {
				result.add(new StructureMember(methodSignatureLabel(method), null, null));
			}
		}

		return result;
	}

	/**
	 * Mirrors {@code DataRepositoryIndexer.identifyMethodSignature}'s label shape exactly - no
	 * declaring class prefix, unlike a stereotype-grouped method's label
	 * ({@code JarStereotypeScanner}'s own method label, which does have one, matching
	 * {@code ASTUtils.getMethodSignature(method, false)}). The two are already independently
	 * shaped this way on the AST side too - a query method's label is not the same kind of label a
	 * stereotype-grouped one is.
	 */
	private static String methodSignatureLabel(MethodInfo method) {
		StringBuilder label = new StringBuilder(method.name()).append('(');
		for (int i = 0; i < method.parametersCount(); i++) {
			if (i > 0) {
				label.append(", ");
			}
			label.append(simpleName(method.parameterType(i).name()));
		}
		return label.append(") : ").append(simpleName(method.returnType().name())).toString();
	}

	private static String simpleName(DotName name) {
		return name.local();
	}

}
