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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.AddAnnotationOverMethod;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class DataRepositoryAotMetadataCodeLensProvider implements CodeLensProvider {

	private static final String COVERT_TO_QUERY_LABEL = "Turn into @Query";
	
	private static final Map<DataRepositoryModule, String> moduleToQueryMapping = Map.of(
			DataRepositoryModule.JPA, Annotations.DATA_JPA_QUERY,
			DataRepositoryModule.JDBC, Annotations.DATA_JDBC_QUERY,
			DataRepositoryModule.MONGODB, Annotations.DATA_MONGODB_QUERY
	);

	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataCodeLensProvider.class);

	private final DataRepositoryAotMetadataService repositoryMetadataService;
	private final JavaProjectFinder projectFinder;
	private final RewriteRefactorings refactorings;
	private final BootJavaConfig config;

	public DataRepositoryAotMetadataCodeLensProvider(SimpleLanguageServer server, JavaProjectFinder projectFinder, DataRepositoryAotMetadataService repositoryMetadataService,
			RewriteRefactorings refactorings, BootJavaConfig config) {
		this.projectFinder = projectFinder;
		this.repositoryMetadataService = repositoryMetadataService;
		this.refactorings = refactorings;
		this.config = config;
		
		listenForAotMetadataChanges(server);
	}
	
	private void listenForAotMetadataChanges(SimpleLanguageServer server) {
		repositoryMetadataService.addListener(files -> {
			if (config.isEnabledCodeLensOverDataQueryMethods()) {
				server.getClient().refreshCodeLenses();
			}
		});
	}

	@Override
	public void provideCodeLenses(CancelChecker cancelToken, TextDocument document, CompilationUnit cu, List<CodeLens> resultAccumulator) {
		if (!config.isEnabledCodeLensOverDataQueryMethods()) {
			return;
		}
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				provideCodeLens(cancelToken, node, document, resultAccumulator);
				return super.visit(node);
			}
		});
	}
	
	static boolean isValidMethodBinding(IMethodBinding methodBinding) {
		if (methodBinding == null || methodBinding.getDeclaringClass() == null
				|| methodBinding.getMethodDeclaration() == null
				|| methodBinding.getDeclaringClass().getBinaryName() == null) {
			return false;
		}
		
		if (!methodBinding.getDeclaringClass().isInterface()) {
			return false;
		}
		
		if (ASTUtils.findInTypeHierarchy(methodBinding.getDeclaringClass(), Set.of(Constants.REPOSITORY_TYPE)) == null) {
			return false;
		}
		
		return true;
	}
		
	static Optional<DataRepositoryAotMetadata> getMetadata(DataRepositoryAotMetadataService dataRepositoryAotMetadataService, IJavaProject project, IMethodBinding methodBinding) {
		final String repositoryClass = methodBinding.getDeclaringClass().getBinaryName().trim();
		return dataRepositoryAotMetadataService.getRepositoryMetadata(project, repositoryClass);
	}

	protected void provideCodeLens(CancelChecker cancelToken, MethodDeclaration node, TextDocument document, List<CodeLens> resultAccumulator) {
		cancelToken.checkCanceled();
		
		IJavaProject project = projectFinder.find(document.getId()).get();
		if (project == null || !QueryMethodCodeActionProvider.isValidProject(project)) {
			return;
		}

		IMethodBinding methodBinding = node.resolveBinding();
		
		if (isValidMethodBinding(methodBinding)) {
			cancelToken.checkCanceled();

			resultAccumulator.addAll(createCodeLenses(project, node, document));
		}
	}
	
	private List<CodeLens> createCodeLenses(IJavaProject project, MethodDeclaration node, TextDocument document) {
		List<CodeLens> codeLenses = new ArrayList<>(2);
		
		try {
			IMethodBinding mb = node.resolveBinding();
			Position startPos = document.toPosition(node.getStartPosition());
			Position endPos = document.toPosition(node.getName().getStartPosition() + node.getName().getLength());
			Range range = new Range(startPos, endPos);
			AnnotationHierarchies hierarchyAnnot = AnnotationHierarchies.get(node);

			if (mb != null && hierarchyAnnot != null) {
				
				Optional<DataRepositoryAotMetadata> optMetadata = getMetadata(repositoryMetadataService, project, mb);
				optMetadata.ifPresentOrElse(metadata -> metadata.findMethod(mb).ifPresent(methodMetadata -> {
					boolean isQueryAnnotated = hierarchyAnnot.isAnnotatedWith(mb, Annotations.DATA_JPA_QUERY)
							|| hierarchyAnnot.isAnnotatedWith(mb, Annotations.DATA_MONGODB_QUERY)
							|| hierarchyAnnot.isAnnotatedWith(mb, Annotations.DATA_JDBC_QUERY);
					
					if (!isQueryAnnotated) {
						codeLenses.add(new CodeLens(range, refactorings.createFixCommand(COVERT_TO_QUERY_LABEL, createFixDescriptor(mb, document.getUri(), metadata.module(), methodMetadata, project)), null));
					}
					
					Command impl = new Command("Go To Implementation", GenAotQueryMethodImplProvider.CMD_NAVIGATE_TO_IMPL, List.of(new GenAotQueryMethodImplProvider.GoToImplParams(
							document.getId(),
							mb.getDeclaringClass().getQualifiedName(),
							mb.getName(),
							Arrays.stream(mb.getParameterTypes()).map(p -> p.getQualifiedName()).toArray(String[]::new),
							null
					)));
					codeLenses.add(new CodeLens(range, impl, null));
					
					if (!isQueryAnnotated) {
						String queryStatement = methodMetadata.getQueryStatement();
						if (queryStatement != null) {
							Command queryTitle = new Command();
							queryTitle.setTitle(queryStatement);
							codeLenses.add(new CodeLens(range, queryTitle, null));
						}
					}
					
					createRefreshCodeLens(project, "Refresh AOT Metadata", range).ifPresent(codeLenses::add);
				}), () -> createRefreshCodeLens(project, "Show AOT-generated Implementation, Query, etc...", range)
						.ifPresent(codeLenses::add)
				);
				

			}
		} catch (BadLocationException e) {
			log.error("bad location while calculating code lens for data repository query method", e);
		}
		return codeLenses;
	}
	
	private Optional<CodeLens> createRefreshCodeLens(IJavaProject project, String title, Range range) {
		return repositoryMetadataService.regenerateMetadataCommand(project).map(refreshCmd -> {
			refreshCmd.setTitle(title);
			return new CodeLens(range, refreshCmd, null);
		});
	}

	static FixDescriptor createFixDescriptor(IMethodBinding mb, String docUri, DataRepositoryModule module, IDataRepositoryAotMethodMetadata methodMetadata, IJavaProject project) {
		return new FixDescriptor(AddAnnotationOverMethod.class.getName(), List.of(docUri), "Turn into `@Query`")
				.withRecipeScope(RecipeScope.FILE)
				.withParameters(Map.of(
						"annotationType", moduleToQueryMapping.get(module),
						"method", "%s %s(%s)".formatted(mb.getDeclaringClass().getQualifiedName(), mb.getName(),
								Arrays.stream(mb.getParameterTypes())
										.map(pt -> pt.getQualifiedName())
										.collect(Collectors.joining(", "))),
						"attributes", createAttributeList(methodMetadata.getAttributesMap(), project)));
	}

	private static List<AddAnnotationOverMethod.Attribute> createAttributeList(Map<String, String> attributes, IJavaProject project) {
		List<AddAnnotationOverMethod.Attribute> result = new ArrayList<>();
		int javaVersion = 8;
		try {
			String versionStr = project.getClasspath().getJre().version();
			if (versionStr != null) {
				if (versionStr.startsWith("1.")) {
					javaVersion = Integer.parseInt(versionStr.substring(2, 3));
				} else {
					javaVersion = Integer.parseInt(versionStr.split("\\.")[0]);
				}
			}
		} catch (Exception e) {
			// fallback to 8
		}
		for (Map.Entry<String, String> entry : attributes.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (value == null) continue;
			String escaped = org.apache.commons.text.StringEscapeUtils.escapeJava(value);
			boolean containsQuote = value.contains("\"");
			if (javaVersion >= 15 && containsQuote) {
				// Use text block
				result.add(new AddAnnotationOverMethod.Attribute(key, "\"\"\"\n" + value + "\n\"\"\""));
			} else {
				// Use standard string
				result.add(new AddAnnotationOverMethod.Attribute(key, "\"%s\"".formatted(escaped)));
			}
		}
		return result;
	}
	
}
