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
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.utils.DocumentUtils;
import org.springframework.ide.vscode.boot.java.utils.QualifiedTypeName;
import org.springframework.ide.vscode.boot.java.utils.SourceJavaFile;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaAstScanner;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaScanResult;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link SpringIndexerJavaAstScanner}.
 *
 * @author Martin Lippert
 */
public class SpringIndexerJavaAstScannerTest {

	private static final String FILE = "/workspace/src/p/A.java";
	private static final String DOC_URI = "file:///workspace/src/p/A.java";

	private final IProblemCollector noopProblems = new IProblemCollector() {
		@Override
		public void beginCollecting() {
		}

		@Override
		public void accept(ReconcileProblem problem) {
		}

		@Override
		public void endCollecting() {
		}
	};

	private SpringIndexerJavaDependencyTracker tracker;
	private IJavaProject project;

	@BeforeEach
	public void setUp() {
		tracker = new SpringIndexerJavaDependencyTracker();
		project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("test-project");
	}

	@Test
	public void scanAST_withoutDependencyTracking_doesNotOverwriteTracker() {
		tracker.update(project, SourceJavaFile.of(FILE), Set.of(QualifiedTypeName.of("com.example.StaleDep")));

		SpringIndexerJavaAstScanner scanner = new SpringIndexerJavaAstScanner(new SpringComponentIndexer[0], tracker,
				(ctx, idx) -> {
				});

		SpringIndexerJavaContext context = minimalContext();
		scanner.scanAST(context, false, new ReconcilingIndex(), false);

		assertTrue(tracker.getDependenciesForFile(project, FILE).contains(QualifiedTypeName.of("com.example.StaleDep")));
	}

	@Test
	public void scanAST_withDependencyTracking_replacesTrackerEntry() {
		tracker.update(project, SourceJavaFile.of(FILE), Set.of(QualifiedTypeName.of("com.example.StaleDep")));

		SpringIndexerJavaAstScanner scanner = new SpringIndexerJavaAstScanner(new SpringComponentIndexer[0], tracker,
				(ctx, idx) -> {
				});

		SpringIndexerJavaContext context = minimalContext();
		scanner.scanAST(context, false, new ReconcilingIndex(), true);

		assertFalse(tracker.getDependenciesForFile(project, FILE).contains(QualifiedTypeName.of("com.example.StaleDep")));
	}

	private SpringIndexerJavaContext minimalContext() {
		CompilationUnit cu = parse("package p; public class A {}");
		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] { FILE });
		return new SpringIndexerJavaContext(project, cu, DOC_URI, FILE, 0L,
				DocumentUtils.createTempTextDocument(DOC_URI, "package p; public class A {}"), null, noopProblems,
				new ArrayList<>(), true, true, result);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
}
