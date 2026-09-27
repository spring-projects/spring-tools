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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.commands.StructureMember;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;

/**
 * {@code spring-data-commons} is not a dependency of this module, so
 * {@code org.springframework.data.repository.Repository}/{@code NoRepositoryBean} are compiled
 * here from a throwaway copy of their source (same {@code Repository<T, ID>} shape, no method of
 * their own) - purely so the fixture interfaces type-check - and left out of the packaged JAR, the
 * same technique {@code StructureDependenciesJarTreeTest} already uses for
 * {@code DescribedStereotype}. Jandex reads a supertype's name straight off the bytecode either
 * way, so this is exactly as faithful a test as compiling against the real JAR would be for what
 * this class actually checks - see {@code JarStereotypeScannerTest}'s
 * "a supertype outside the indexed jar still counts by name".
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
	void nonDefaultMethodsOfARepositoryInterfaceBecomeMembers() throws Exception {
		ScannedElement element = scanSingle("com.example.CustomerRepository", """
				package com.example;
				import org.springframework.data.repository.Repository;
				class Customer {}
				public interface CustomerRepository extends Repository<Customer, Long> {
					Customer findByEmail(String email);
					default Customer findAnyDefault() { return null; }
				}
				""");

		List<StructureMember> members = element.members;

		assertEquals(List.of(new StructureMember("findByEmail(String) : Customer", null, null)), members);
	}

	/**
	 * Matches {@code DataRepositoryIndexer.identifyMethodSignature}'s exact label shape - no
	 * declaring class prefix, unlike a stereotype-grouped method's label.
	 */
	@Test
	void theMemberLabelHasNoDeclaringClassPrefixUnlikeAStereotypeGroupedMethod() throws Exception {
		ScannedElement element = scanSingle("com.example.Repo", """
				package com.example;
				import org.springframework.data.repository.Repository;
				public interface Repo extends Repository<Object, Long> {
					java.util.List<Object> findAll(String query);
				}
				""");

		assertEquals("findAll(String) : List", element.members.get(0).label());
	}

	@Test
	void aNoRepositoryBeanInterfaceContributesNoMembers() throws Exception {
		ScannedElement element = scanSingle("com.example.BaseRepository", """
				package com.example;
				import org.springframework.data.repository.NoRepositoryBean;
				import org.springframework.data.repository.Repository;
				@NoRepositoryBean
				public interface BaseRepository extends Repository<Object, Long> {
					Object findSomething();
				}
				""");

		assertEquals(List.of(), element.members);
	}

	@Test
	void aPlainInterfaceThatIsNotARepositoryContributesNoMembers() throws Exception {
		ScannedElement element = scanSingle("com.example.PlainInterface", """
				package com.example;
				public interface PlainInterface {
					Object findSomething();
				}
				""");

		assertEquals(List.of(), element.members);
	}

	private ScannedElement scanSingle(String fqn, String source) throws Exception {
		Path classesDir = JarFixtureBuilder.compileAll(tempDir, Map.of(
				fqn, source,
				"org.springframework.data.repository.Repository", REPOSITORY_STUB,
				"org.springframework.data.repository.NoRepositoryBean", NO_REPOSITORY_BEAN_STUB));

		// only the fixture interface goes into the JAR - the stubs stay out, exactly like
		// DescribedStereotype in StructureDependenciesJarTreeTest
		File jar = JarFixtureBuilder.packageJar(classesDir, tempDir, "fixture", List.of(fqn), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		ClassInfo classInfo = index.getClassByName(DotName.createSimple(fqn));
		StereotypeClassElement element = JarStereotypeScanner.ownClassesOf(ownClasses, index).get(0);

		return new ScannedElement(element, JarDataRepositoryScanner.membersOf(classInfo, element));
	}

	/**
	 * Just a convenience so each test can read {@code element.members} directly.
	 */
	private record ScannedElement(StereotypeClassElement delegate, List<StructureMember> members) {
	}

}
