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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarTypes;

/**
 * {@code spring-data-commons} is not a dependency of this module, so
 * {@code org.springframework.data.repository.Repository}/{@code NoRepositoryBean} are compiled
 * here from a throwaway copy of their source - purely so the fixture interfaces type-check - and
 * left out of the packaged JAR. Jandex reads a supertype's name straight off the bytecode either
 * way (see {@code JarStereotypeScannerTest}).
 *
 * <p>The labels asserted here come from {@link QueryMethodIndexElement} itself - the element the
 * AST side creates too.
 *
 * @author Martin Lippert
 */
public class JarDataRepositoryScannerTest {

	private static final String REPOSITORY_STUB = """
			package org.springframework.data.repository;
			public interface Repository<T, ID> {}
			""";

	private static final String NO_REPOSITORY_BEAN_STUB = """
			package org.springframework.data.repository;
			import java.lang.annotation.*;
			@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
			public @interface NoRepositoryBean {}
			""";

	@TempDir
	Path tempDir;

	@Test
	void nonDefaultMethodsOfARepositoryInterfaceBecomeMembersInDeclarationOrder() throws Exception {
		assertEquals(List.of("findByEmail(String) : Customer", "countByName(String) : long"), members("com.example.CustomerRepository", """
				package com.example;
				import org.springframework.data.repository.Repository;
				class Customer {}
				public interface CustomerRepository extends Repository<Customer, Long> {
					Customer findByEmail(String email);
					default Customer findAnyDefault() { return null; }
					long countByName(String name);
				}
				"""));
	}

	/**
	 * {@code DataRepositoryIndexer.identifyMethodSignature} renders types with JDT's
	 * {@code getName()}, which keeps type arguments.
	 */
	@Test
	void theLabelKeepsTypeArgumentsLikeJdtDoes() throws Exception {
		assertEquals(List.of("findAll(String) : List<Object>"), members("com.example.Repo", """
				package com.example;
				import org.springframework.data.repository.Repository;
				public interface Repo extends Repository<Object, Long> {
					java.util.List<Object> findAll(String query);
				}
				"""));
	}

	/**
	 * {@code DataRepositoryIndexer.identifyMethodSignature} renders a parameter's declared type,
	 * which for varargs is the element type, not the array.
	 */
	@Test
	void aVarargsParameterIsLabelledWithItsElementType() throws Exception {
		assertEquals(List.of("findByNameIn(String) : List<Object>"), members("com.example.Repo", """
				package com.example;
				import org.springframework.data.repository.Repository;
				public interface Repo extends Repository<Object, Long> {
					java.util.List<Object> findByNameIn(String... names);
				}
				"""));
	}

	@Test
	void aQueryMethodIsReferencedByItsMethod() throws Exception {
		File jar = jar(Map.of("com.example.Repo", """
				package com.example;
				import org.springframework.data.repository.Repository;
				public interface Repo extends Repository<Object, Long> {
					java.util.List<Object> findByNameIn(String... names);
				}
				"""), List.of("com.example.Repo"));

		assertEquals(List.of("Lcom/example/Repo;.findByNameIn([Ljava/lang/String;)Ljava/util/List<Ljava/lang/Object;>;"),
				JarTypes.memberKeys(JarTypes.scan(jar).get("com.example.Repo")));
	}

	@Test
	void aNoRepositoryBeanInterfaceContributesNoMembers() throws Exception {
		assertEquals(List.of(), members("com.example.BaseRepository", """
				package com.example;
				import org.springframework.data.repository.NoRepositoryBean;
				import org.springframework.data.repository.Repository;
				@NoRepositoryBean
				public interface BaseRepository extends Repository<Object, Long> {
					Object findSomething();
				}
				"""));
	}

	/**
	 * The common "shared base repository" pattern: {@code @NoRepositoryBean} on the base interface
	 * does not exclude the repositories extending it - decided from the class's own annotations, as
	 * {@code DataRepositoryIndexer.findRepositoryDomainType} does.
	 */
	@Test
	void aRepositoryExtendingANoRepositoryBeanBaseInterfaceStillContributesItsMembers() throws Exception {
		File jar = jar(Map.of(
				"com.example.BaseRepository", """
						package com.example;
						import org.springframework.data.repository.NoRepositoryBean;
						import org.springframework.data.repository.Repository;
						@NoRepositoryBean
						public interface BaseRepository<T> extends Repository<T, Long> {}
						""",
				"com.example.OrderRepository", """
						package com.example;
						public interface OrderRepository extends BaseRepository<Object> {
							Object findByNumber(String number);
						}
						"""), List.of("com.example.BaseRepository", "com.example.OrderRepository"));

		assertEquals(List.of("findByNumber(String) : Object"), JarTypes.memberLabels(JarTypes.scan(jar).get("com.example.OrderRepository")));
	}

	@Test
	void aPlainInterfaceThatIsNotARepositoryContributesNoMembers() throws Exception {
		assertEquals(List.of(), members("com.example.PlainInterface", """
				package com.example;
				public interface PlainInterface {
					Object findSomething();
				}
				"""));
	}

	private List<String> members(String fqn, String source) throws Exception {
		File jar = jar(Map.of(fqn, source), List.of(fqn));
		return JarTypes.memberLabels(JarTypes.scan(jar).get(fqn));
	}

	/**
	 * Compiles the given sources together with the stubs, and packages only the given types.
	 */
	private File jar(Map<String, String> sources, List<String> packaged) throws Exception {
		Map<String, String> all = new HashMap<>(sources);
		all.put("org.springframework.data.repository.Repository", REPOSITORY_STUB);
		all.put("org.springframework.data.repository.NoRepositoryBean", NO_REPOSITORY_BEAN_STUB);

		Path classesDir = JarFixtureBuilder.compileAll(tempDir, all);
		return JarFixtureBuilder.packageJar(classesDir, tempDir, "fixture", packaged, Map.of());
	}

}
