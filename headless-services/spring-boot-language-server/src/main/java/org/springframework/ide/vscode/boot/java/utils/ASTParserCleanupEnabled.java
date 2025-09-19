/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;

/**
 * Wrapper around a JDT AST parser to enable cleanup functionality on the lookup environment,
 * which is otherwise hidden and not accessible from the outside
 */
public class ASTParserCleanupEnabled {
	
	private final ASTParser parser;
//	private final INameEnvironmentWithProgress environment;
	private final Map<String, String> options;
//	private final int flags;
//	private final int apiLevel;
	
	private final AnnotationHierarchies annotationHierachies;
	
	public ASTParserCleanupEnabled(String[] classpathEntries, String[] sourceEntries, String complianceVersion, AnnotationHierarchies annotationHierarchies, boolean ignoreMethodBodies) {
		this.annotationHierachies = annotationHierarchies; 
		parser = ASTParser.newParser(AST.JLS24);
		options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(complianceVersion, options);

		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setStatementsRecovery(true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setIgnoreMethodBodies(ignoreMethodBodies);

		parser.setEnvironment(classpathEntries, sourceEntries, null, false);

//		List<Classpath> classpaths = CUResolver.getClasspath(parser);
//		environment = CUResolver.createLookupEnvironment(classpaths.toArray(new Classpath[classpaths.size()]));
//		
//		apiLevel = AST.JLS21;
//
//		int flags = 0;
//		flags |= ICompilationUnit.ENABLE_STATEMENTS_RECOVERY;
//		flags |= ICompilationUnit.ENABLE_BINDINGS_RECOVERY;
//
//		if (ignoreMethodBodies) {
//			flags |= ICompilationUnit.IGNORE_METHOD_BODIES;
//		}
//		
//		this.flags = flags;

	}

//	public ASTParserCleanupEnabled(String[] classpathEntries, String[] sourceEntries, boolean ignoreMethodBodies) {
//		this(classpathEntries, sourceEntries, new AnnotationHierarchies(), ignoreMethodBodies);
//	}

	public void setUnitName(String unitName) {
		this.parser.setUnitName(unitName);
	}

	public void setSource(char[] source) {
		this.parser.setSource(source);
	}
	
	public ASTNode createAST(IProgressMonitor monitor) {
		ASTNode n = this.parser.createAST(monitor);
		AnnotationHierarchies.set(n, annotationHierachies);
		return n;
	}
	
	public void createASTs(String[] sourceFilePaths, String[] encodings, String[] bindingKeys,
			FileASTRequestor requestor, IProgressMonitor monitor) {
		
//		CUResolver.resolve(sourceFilePaths, encodings, bindingKeys, requestor, apiLevel, options, flags, environment);
		this.parser.createASTs(sourceFilePaths, encodings, bindingKeys, new DelegatingFileASTRequestor(requestor,
				(p, cu) -> AnnotationHierarchies.set(cu, annotationHierachies)), monitor);
	}
	
	public void cleanup() {
//		environment.cleanup();
	}

}
