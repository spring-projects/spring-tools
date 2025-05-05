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
package org.springframework.ide.vscode.boot.java.data;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.IJavaDefinitionProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.BadLocationException;

public class GenAotQueryMethodDefinitionProvider implements IJavaDefinitionProvider {
	
	private static Logger log = LoggerFactory.getLogger(GenAotQueryMethodDefinitionProvider.class);
	
	private final CompilationUnitCache cuCache;
	private final SimpleTextDocumentService docService;
	
	public GenAotQueryMethodDefinitionProvider(CompilationUnitCache cuCache, SimpleTextDocumentService docService) {
		this.cuCache = cuCache;
		this.docService = docService;
	}

	@Override
	public List<LocationLink> getDefinitions(CancelChecker cancelToken, IJavaProject project,
			TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		if (n instanceof SimpleName && n.getParent() instanceof MethodDeclaration md) {
			Version version = SpringProjectUtil.getDependencyVersion(project, "spring-data-jpa");
			if (version != null && version.getMajor() >= 4) {
				IMethodBinding methodBinding = md.resolveBinding();
				if (methodBinding != null && methodBinding.getDeclaringClass() != null
						&& methodBinding.getDeclaringClass().isInterface()
						&& methodBinding.getDeclaringClass() != null
						&& ASTUtils.isAnyTypeInHierarchy(methodBinding.getDeclaringClass(),
								List.of(Constants.REPOSITORY_TYPE))) {
					String genRepoFqn = methodBinding.getDeclaringClass().getQualifiedName() + "Impl__Aot";
					Path relativeGenSourcePath = Paths.get("%s.java".formatted(genRepoFqn.replace('.', '/')));
					List<LocationLink> defs = findInSourceFolder(project, relativeGenSourcePath, docId, md, methodBinding, genRepoFqn);
					return defs.isEmpty() ? findInBuildFolder(project, relativeGenSourcePath, docId, md, methodBinding, genRepoFqn) : defs;
				}
			}
		}
		return List.of();
	}
	
	private List<LocationLink> getLocationInGenFile(IJavaProject project, TextDocumentIdentifier docId, MethodDeclaration md, IMethodBinding methodBinding, Path genRepoSourcePath, String genRepoFqn) {
		if (Files.exists(genRepoSourcePath)) {
			URI genUri = genRepoSourcePath.toUri();
			return cuCache.withCompilationUnit(project, genUri, genCu -> {
				List<LocationLink> defs = new ArrayList<>(1);
				genCu.accept(new ASTVisitor() {

					@Override
					public boolean visit(MethodDeclaration node) {
						IMethodBinding genBinding = node.resolveBinding();
						if (genBinding != null 
								&& genBinding.getName().equals(methodBinding.getName()) 
								&& Arrays.equals(Arrays.stream(genBinding.getParameterTypes()).map(b -> b.getQualifiedName()).toArray(), Arrays.stream(methodBinding.getParameterTypes()).map(b -> b.getQualifiedName()).toArray() )
								&& genRepoFqn.equals(genBinding.getDeclaringClass().getQualifiedName())) {
							LocationLink ll = new LocationLink();
							ll.setTargetUri(genUri.toASCIIString());
							try {
								ll.setOriginSelectionRange(docService.getLatestSnapshot(docId.getUri()).toRange(md.getName().getStartPosition(), md.getName().getLength()));
							} catch (BadLocationException e) {
								log.error("", e);
							}
							SimpleName genName = node.getName();
							int startLine = genCu.getLineNumber(genName.getStartPosition());
							Position targetStartPosition = new Position(startLine, genName.getStartPosition() - genCu.getPosition(startLine, 0));
							int endLine = genCu.getLineNumber(genName.getStartPosition() + genName.getLength());
							Position targetEndPosition = new Position(endLine, genName.getStartPosition() + genName.getLength() - genCu.getPosition(endLine, 0));
							Range targetRange = new Range(targetStartPosition, targetEndPosition);
							ll.setTargetRange(targetRange);
							ll.setTargetSelectionRange(targetRange);
							defs.add(ll);
						}
						return false;
					}
					
				});
				return defs;
			});
		}
		return List.of();
	}
	
	private List<LocationLink> findInSourceFolder(IJavaProject project, Path relativeGenSourcePath, TextDocumentIdentifier docId, MethodDeclaration md, IMethodBinding methodBinding, String genRepoFqn) {
		for (File f : IClasspathUtil.getSourceFolders(project.getClasspath()).collect(Collectors.toSet())) {
			Path genRepoSourcePath = f.toPath().resolve(relativeGenSourcePath);
			return getLocationInGenFile(project, docId, md, methodBinding, genRepoSourcePath, genRepoFqn);
		}
		return List.of();
	}
	
	private List<LocationLink> findInBuildFolder(IJavaProject project, Path relativeGenSourcePath, TextDocumentIdentifier docId, MethodDeclaration md, IMethodBinding methodBinding, String genRepoFqn) {
		Path buildDirRelativePath = null;
		Path projectPath = Paths.get(project.getLocationUri());
		Set<Path> outputFolders = IClasspathUtil.getOutputFolders(project.getClasspath()).map(f -> f.toPath()).collect(Collectors.toSet());
		for (Path f : outputFolders) {
			 Path p = projectPath.relativize(f);
			if (buildDirRelativePath == null) {
				buildDirRelativePath =  p;
			} else {
				 int i = 0;
				 for (; i < buildDirRelativePath.getNameCount() && i < p.getNameCount() && buildDirRelativePath.getName(i).equals(p.getName(i)); i++) {
					 // nothing;
				 }
				 if (i ==  0) {
					 buildDirRelativePath = Paths.get("");
					 break;
				 } else {
					 buildDirRelativePath = buildDirRelativePath.subpath(0, i);
				 }
			}
		}
		AtomicReference<Path> genSourceFilePathRef = new AtomicReference<>();
		try {
			Files.walkFileTree(projectPath.resolve(buildDirRelativePath), new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (genSourceFilePathRef.get() == null && !outputFolders.stream().anyMatch(dir::startsWith)) {
						Path genPath = dir.resolve(relativeGenSourcePath);
						if (Files.exists(genPath)) {
							genSourceFilePathRef.set(genPath);
						} else {
							return FileVisitResult.CONTINUE;
						}
					}
					return FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.getFileName().toString().endsWith(".class")) {
						return FileVisitResult.SKIP_SIBLINGS;
					}
					return super.visitFile(file, attrs);
				}

			});
		} catch (IOException e) {
			log.error("", e);
		}
		return genSourceFilePathRef.get() == null ? List.of() : getLocationInGenFile(project, docId, md, methodBinding, genSourceFilePathRef.get(), genRepoFqn);
	}
	
}