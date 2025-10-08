/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.reconcilers.CompositeASTVisitor;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingContext;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava;
import org.springframework.ide.vscode.boot.java.value.test.ValueSpelExpressionValidationTest.TestProblemCollector;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

public abstract class BaseReconcilerTest {
	
	protected IJavaProject project;
	
	abstract protected String getFolder();
	
	abstract protected String getProjectName();
	
	abstract protected JdtAstReconciler getReconciler();
	
	protected Path createFile(String name, String content) throws IOException {
		Path filePath = Paths.get(project.getLocationUri()).resolve("src/main/java").resolve(getFolder()).resolve(name);
		Files.createDirectories(filePath.getParent());
		Files.createFile(filePath);
		Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
		return filePath;
	}
	
	private void clearTestFiles() throws IOException, URISyntaxException {
		Path projectPath = Paths.get(getClass().getResource("/test-projects/" + getProjectName()).toURI());
		Path toRemove = projectPath.resolve("src/main/java").resolve(getFolder());
		if (Files.exists(toRemove)) {
			Files.walk(toRemove).sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
		assertFalse(Files.exists(toRemove));
	}
	
	void setup() throws Exception {
		clearTestFiles();
		project = ProjectsHarness.INSTANCE.mavenProject(getProjectName());
	}
	
	void tearDown() throws Exception {
		clearTestFiles();
	}

	List<ReconcileProblem> reconcile(String fileName, String source, boolean isCompleteAst, Path... additionalSources) throws Exception {
		return reconcile(this::getReconciler, fileName, source, isCompleteAst, true, new ReconcilingIndex(), additionalSources);
	}

	List<ReconcileProblem> reconcile(String fileName, String source, boolean isCompleteAst, boolean isIndexComplete, Path... additionalSources) throws Exception {
		return reconcile(this::getReconciler, fileName, source, isCompleteAst, isIndexComplete, new ReconcilingIndex(), additionalSources);
	}

	List<ReconcileProblem> reconcile(Supplier<JdtAstReconciler> reconcilerFactory, String fileName, String source, boolean isCompleteAst,
			Path... additionalSources) throws Exception {
		return reconcile(reconcilerFactory, fileName, source, isCompleteAst, true, new ReconcilingIndex(), additionalSources);
	}
		
	List<ReconcileProblem> reconcile(Supplier<JdtAstReconciler> reconcilerFactory, String fileName, String source, boolean isCompleteAst,
			ReconcilingIndex reconcilingIndex, Path... additionalSources) throws Exception {
		return reconcile(reconcilerFactory, fileName, source, isCompleteAst, true, reconcilingIndex, additionalSources);
	}
		
	List<ReconcileProblem> reconcile(Supplier<JdtAstReconciler> reconcilerFactory, String fileName, String source, boolean isCompleteAst,
			boolean isIndexComplete, ReconcilingIndex reconcilingIndex, Path... additionalSources) throws Exception {

		Path path = createFile(fileName, source);
		TestProblemCollector problemCollector = new TestProblemCollector();
		AtomicBoolean requiredCompleteAst = new AtomicBoolean(false);
		String[] sources = Stream.concat(Arrays.stream(additionalSources), Stream.of(path)).map(p -> p.toFile().toString()).toArray(String[]::new);
		
		SpringIndexerJava.createParser(project, new AnnotationHierarchies(), !isCompleteAst).createASTs(sources, null, new String[0], new FileASTRequestor() {

			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				try {
					JdtAstReconciler reconciler = reconcilerFactory.get();
					String docURI = path.toUri().toASCIIString();

					ReconcilingContext reconcilingContext = new ReconcilingContext(docURI, problemCollector, isCompleteAst, isIndexComplete, Collections.emptyList(), reconcilingIndex);
					ASTVisitor visitor = reconciler.createVisitor(project,  path.toUri(), cu, reconcilingContext);
					
					if (visitor != null) {
						// use a composite visitor here to make sure that the tests will fail if there is anything missing in the composite
						// visitor for the reconciler AST visitor to work correctly
						CompositeASTVisitor compositeASTVisitor = new CompositeASTVisitor();
						compositeASTVisitor.add(visitor);

						cu.accept(compositeASTVisitor);
					}
				} catch (RequiredCompleteAstException e) {
					requiredCompleteAst.set(true);
				}				
			}	
		}, null);
		
		if (requiredCompleteAst.get()) {
			throw new RequiredCompleteAstException();
		}
		
		return problemCollector.getCollectedProblems();
	}

}
