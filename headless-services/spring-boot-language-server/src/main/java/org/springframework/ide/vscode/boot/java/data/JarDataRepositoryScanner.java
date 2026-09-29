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

import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JdtStyleTypeNames;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * The JAR/bytecode counterpart of {@link DataRepositoryIndexer}: recognizes a Spring Data
 * repository interface and adds its query methods to its bean, as the same
 * {@link QueryMethodIndexElement}s the AST side adds, so their label comes from the same code. See
 * {@code docs/structure-view-dependencies.md}.
 *
 * <p>Whether a class is a repository is answered from the shared {@code supertypes} walk
 * ({@code doesImplement}), not by walking the interface hierarchy a second time; whether it is
 * excluded is answered from the class's <em>own</em> annotations, as
 * {@code DataRepositoryIndexer.findRepositoryDomainType} does - a repository extending a
 * {@code @NoRepositoryBean} base interface is still a repository.
 *
 * <p>Not reconstructed: the resolved query string ({@code @Query}'s value or Spring Data's AOT
 * metadata), which the tree does not render for workspace projects either.
 *
 * @author Martin Lippert
 */
public class JarDataRepositoryScanner {

	// the class file access flag for a varargs method (JVMS 4.6) - Jandex has no accessor for it
	private static final int ACC_VARARGS = 0x0080;

	public static boolean isRepository(JarType type) {
		return type.element().doesImplement(Constants.REPOSITORY_TYPE) && !type.ownAnnotationTypes().contains(Annotations.NO_REPO_BEAN);
	}

	/**
	 * Adds one {@link QueryMethodIndexElement} per non-{@code default} method, in declaration
	 * order - {@code DataRepositoryIndexer.identifyQueryMethods}.
	 */
	public static void addQueryMethods(Bean bean, JarType type) {
		for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(type.classInfo())) {
			if (!method.isDefault() && !method.isConstructor()) {
				QueryMethodIndexElement queryMethod = new QueryMethodIndexElement(methodSignature(method), null, type.placeholderLocation().getRange(), null);
				type.bindingKeys().put(queryMethod, JarBindingKeys.of(method));
				bean.addChild(queryMethod);
			}
		}
	}

	/**
	 * {@code DataRepositoryIndexer.identifyMethodSignature}: the method name, then JDT-style simple
	 * type names - no declaring class, unlike a stereotype-grouped method's label. It renders each
	 * parameter's <em>declared</em> type, which for a varargs parameter is the element type
	 * ({@code String... names} is {@code String}), not the array bytecode has.
	 */
	private static String methodSignature(MethodInfo method) {
		List<Type> parameters = method.parameterTypes();
		List<String> names = new ArrayList<>();
		for (int i = 0; i < parameters.size(); i++) {
			Type parameter = parameters.get(i);
			boolean varargs = (method.flags() & ACC_VARARGS) != 0 && i == parameters.size() - 1 && parameter.kind() == Type.Kind.ARRAY;
			names.add(JdtStyleTypeNames.name(varargs ? parameter.asArrayType().componentType() : parameter));
		}
		return method.name() + "(" + String.join(", ", names) + ") : " + JdtStyleTypeNames.name(method.returnType());
	}

}
