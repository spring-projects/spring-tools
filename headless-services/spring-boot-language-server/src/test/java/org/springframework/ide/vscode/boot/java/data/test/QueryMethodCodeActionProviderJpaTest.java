/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.data.QueryMethodCodeActionProvider;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaParserUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.BasicCollector;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class QueryMethodCodeActionProviderJpaTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private QueryMethodCodeActionProvider codeActionProvider;

	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("aot-data-repositories-jpa");
		harness.useProject(testProject);
		harness.intialize(null);

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void convertToQueryCodeAction() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA,
				new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeAction> codeActions = editor.getCodeActions("findUserByLastnameStartingWith(String lastname)", 1);
		assertEquals(1, codeActions.size());
		CodeAction ca = codeActions.get(0);
		assertEquals("Add `@Query`", ca.getLabel());

		ca.perform();

		assertEquals("""
				/*
				 * Copyright 2025 the original author or authors.
				 *
				 * Licensed under the Apache License, Version 2.0 (the "License");
				 * you may not use this file except in compliance with the License.
				 * You may obtain a copy of the License at
				 *
				 *      https://www.apache.org/licenses/LICENSE-2.0
				 *
				 * Unless required by applicable law or agreed to in writing, software
				 * distributed under the License is distributed on an "AS IS" BASIS,
				 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
				 * See the License for the specific language governing permissions and
				 * limitations under the License.
				 */
				package example.springdata.aot;

				import java.util.List;
				import java.util.Optional;

				import org.springframework.data.domain.Page;
				import org.springframework.data.domain.Pageable;
				import org.springframework.data.domain.Slice;
				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.querydsl.QuerydslPredicateExecutor;
				import org.springframework.data.repository.CrudRepository;

				/**
				 * @author Christoph Strobl
				 * @since 2025/01
				 */
				public interface UserRepository extends CrudRepository<User, String>, QuerydslPredicateExecutor<User> {

				    User findUserByUsername(String username);

				    Optional<User> findOptionalUserByUsername(String username);

				    Long countUsersByLastnameLike(String lastname);

				    Boolean existsByUsername(String username);

				    List<User> findUserByLastnameLike(String lastname);

				    List<User> findUserByLastnameStartingWithOrderByFirstname(String lastname);

				    List<User> findTop2UsersByLastnameStartingWith(String lastname);

				    Slice<User> findUserByUsernameAfter(String username, Pageable pageable);

				    @Query("SELECT u FROM users u WHERE u.lastname LIKE :lastname ESCAPE '\\\\'")
				\tList<User> findUserByLastnameStartingWith(String lastname);

				    Page<User> findUserByLastnameStartingWith(String lastname, Pageable page);

				    @Query("SELECT u FROM example.springdata.aot.User u WHERE u.username LIKE ?1%")
				    List<User> usersWithUsernamesStartingWith(String username);

				}
				""", editor.getRawText().replace("\r", ""));
	}

	/**
	 * The LSP code action handler swallows exceptions and answers an empty list, so this test
	 * drives the provider's AST visitor directly - otherwise an NPE from an unresolvable method
	 * binding would be indistinguishable from "no code action available" (GH-1980).
	 */
	@Test
	void noConvertToQueryCodeActionForUnresolvableMethodBinding() throws Exception {
		// broken source code: declaring the query method twice leaves JDT without a method
		// binding for the second of the two declarations
		String source = """
				package example.springdata.aot;

				import java.util.List;

				import org.springframework.data.repository.CrudRepository;

				public interface DuplicatedQueryMethodRepository extends CrudRepository<User, String> {

				    List<User> findUserByLastnameLike(String lastname);

				    List<User> findUserByLastnameLike(String lastname);

				}
				""";

		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/DuplicatedQueryMethodRepository.java");
		Files.writeString(filePath, source);

		try {
			List<org.eclipse.lsp4j.CodeAction> collected = new ArrayList<>();
			TextDocument doc = new TextDocument(filePath.toUri().toASCIIString(), LanguageId.JAVA, 0, source);

			SpringIndexerJavaParserUtils.createParser(testProject, new AnnotationHierarchies(), true)
				.createASTs(new String[] { filePath.toString() }, null, new String[0], new FileASTRequestor() {
					@Override
					public void acceptAST(String sourceFilePath, CompilationUnit cu) {
						MethodDeclaration[] methods = ((TypeDeclaration) cu.types().get(0)).getMethods();
						assertEquals(2, methods.length);

						// the fixture only makes sense as long as JDT gives up on the second declaration
						assertNotNull(methods[0].resolveBinding());
						assertNull(methods[1].resolveBinding());

						SimpleName brokenMethodName = methods[1].getName();
						IRegion region = new Region(brokenMethodName.getStartPosition(), brokenMethodName.getLength());

						codeActionProvider.createVisitor(() -> {}, testProject, filePath.toUri(), cu, doc, region,
								new BasicCollector<>(collected)).ifPresent(cu::accept);
					}
				}, null);

			assertTrue(collected.isEmpty(), "no quick fix without a method binding");
		}
		finally {
			Files.deleteIfExists(filePath);
		}
	}

	@Test
	void noConvertToQueryCodeAction() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA,
				new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeAction> codeActions = editor.getCodeActions("usersWithUsernamesStartingWith", 1);
		assertEquals(0, codeActions.size());
	}
	
}
