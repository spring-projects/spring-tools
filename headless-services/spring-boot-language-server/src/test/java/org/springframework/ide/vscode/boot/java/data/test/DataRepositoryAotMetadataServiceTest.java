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
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadata;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataMethod;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser.JLRMethod;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class DataRepositoryAotMetadataServiceTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private CompilationUnitCache cuCache;
	
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
	void testBasicRepositoryAotMetadataLookuo() throws Exception {
		DataRepositoryAotMetadataService service = new DataRepositoryAotMetadataService();

		DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository");
		assertNotNull(metadata);
		assertEquals("example.springdata.aot.UserRepository", metadata.name());
		assertEquals("JPA", metadata.module());
	}

	@Test
	void testRepositoryMethodsIntMetadata() throws Exception {
		DataRepositoryAotMetadataService service = new DataRepositoryAotMetadataService();

		DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository");
		DataRepositoryAotMetadataMethod[] methods = metadata.methods();
		
		assertEquals(32, methods.length);
		
		DataRepositoryAotMetadataMethod methodMetadata = Arrays.stream(methods).filter(method -> method.name().equals("countUsersByLastnameLike")).findFirst().get();
		assertEquals("countUsersByLastnameLike", methodMetadata.name());
		assertEquals("public abstract java.lang.Long example.springdata.aot.UserRepository.countUsersByLastnameLike(java.lang.String)", methodMetadata.signature());
		
		JLRMethod parsedMethodSignature = JLRMethodParser.parse(methodMetadata.signature());
		assertEquals("example.springdata.aot.UserRepository", parsedMethodSignature.getFQClassName());
		assertEquals("java.lang.Long", parsedMethodSignature.getReturnType());
		assertEquals("countUsersByLastnameLike", parsedMethodSignature.getMethodName());
		
		String[] parameters = parsedMethodSignature.getParameters();
		assertEquals("java.lang.String", parameters[0]);
		assertEquals(1, parameters.length);
	}
	
	@Test
	void testRepositoryMethodsMatching() throws Exception {
		DataRepositoryAotMetadataService service = new DataRepositoryAotMetadataService();
		DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository");
		
		URI docUri = testProject.getLocationUri().resolve("src/main/java/example/springdata/aot/UserRepository.java");
		cuCache.withCompilationUnit(testProject, docUri, cu -> {
			cu.accept(new ASTVisitor() {
				public boolean visit(MethodDeclaration node) {
					IMethodBinding binding = node.resolveBinding();
					
					DataRepositoryAotMetadataMethod method = service.findMethod(metadata, binding);
					assertNotNull(method);
					
					if (method.name().equals("findUserByLastnameStartingWith") && binding.getParameterTypes().length == 1) {
						assertEquals("public abstract java.util.List<example.springdata.aot.User> example.springdata.aot.UserRepository.findUserByLastnameStartingWith(java.lang.String)", method.signature());
					}
					else if (method.name().equals("findUserByLastnameStartingWith") && binding.getParameterTypes().length == 2) {
						assertEquals("public abstract org.springframework.data.domain.Page<example.springdata.aot.User> example.springdata.aot.UserRepository.findUserByLastnameStartingWith(java.lang.String,org.springframework.data.domain.Pageable)", method.signature());
					}
					
					return true;
				}
			});
			
			return null;
		});
		
	}

}
