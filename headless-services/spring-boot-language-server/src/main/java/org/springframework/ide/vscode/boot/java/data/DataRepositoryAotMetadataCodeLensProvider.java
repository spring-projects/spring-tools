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
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
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

	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataCodeLensProvider.class);

	private final DataRepositoryAotMetadataService repositoryMetadataService;
	private final JavaProjectFinder projectFinder;
	private final RewriteRefactorings refactorings;
	private final BootJavaConfig config;

	public DataRepositoryAotMetadataCodeLensProvider(JavaProjectFinder projectFinder, DataRepositoryAotMetadataService repositoryMetadataService,
			RewriteRefactorings refactorings, BootJavaConfig config) {
		this.projectFinder = projectFinder;
		this.repositoryMetadataService = repositoryMetadataService;
		this.refactorings = refactorings;
		this.config = config;
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
		return true;
	}
	
//	static Optional<String> getDataQuery(DataRepositoryAotMetadataService repositoryMetadataService, IJavaProject project, IMethodBinding methodBinding) {
//		final String repositoryClass = methodBinding.getDeclaringClass().getBinaryName().trim();
//		final IMethodBinding method = methodBinding.getMethodDeclaration();
//
//		DataRepositoryAotMetadata metadata = repositoryMetadataService.getRepositoryMetadata(project, repositoryClass);
//
//		if (metadata != null) {
//			return Optional.ofNullable(repositoryMetadataService.getQueryStatement(metadata, method));
//		}
//		
//		return Optional.empty();
//
//	}
	
	static Optional<DataRepositoryAotMetadata> getMetadata(DataRepositoryAotMetadataService dataRepositoryAotMetadataService, IJavaProject project, IMethodBinding methodBinding) {
		final String repositoryClass = methodBinding.getDeclaringClass().getBinaryName().trim();

		return Optional.ofNullable(dataRepositoryAotMetadataService.getRepositoryMetadata(project, repositoryClass));
	}

	static Optional<IDataRepositoryAotMethodMetadata> getMethodMetadata(DataRepositoryAotMetadataService dataRepositoryAotMetadataService, DataRepositoryAotMetadata metadata, IMethodBinding methodBinding) {
		final IMethodBinding method = methodBinding.getMethodDeclaration();

		return Optional.ofNullable(metadata.findMethod(method));
	}

	protected void provideCodeLens(CancelChecker cancelToken, MethodDeclaration node, TextDocument document, List<CodeLens> resultAccumulator) {
		cancelToken.checkCanceled();
		
		IJavaProject project = projectFinder.find(document.getId()).get();
		if (project == null) {
			return;
		}

		IMethodBinding methodBinding = node.resolveBinding();
		
		if (isValidMethodBinding(methodBinding)) {
			cancelToken.checkCanceled();

			getMetadata(repositoryMetadataService, project, methodBinding)
				.map(metadata -> createCodeLenses(node, document, metadata))
				.ifPresent(cls -> cls.forEach(resultAccumulator::add));
		}
	}
	
	private List<CodeLens> createCodeLenses(MethodDeclaration node, TextDocument document, DataRepositoryAotMetadata metadata) {
		List<CodeLens> codeLenses = new ArrayList<>(2);
		
		try {
			IMethodBinding mb = node.resolveBinding();
			Position startPos = document.toPosition(node.getStartPosition());
			Position endPos = document.toPosition(node.getName().getStartPosition() + node.getName().getLength());
			Range range = new Range(startPos, endPos);
			AnnotationHierarchies hierarchyAnnot = AnnotationHierarchies.get(node);

			Optional<IDataRepositoryAotMethodMetadata> methodMetadata = getMethodMetadata(repositoryMetadataService, metadata, mb);

			if (mb != null && hierarchyAnnot != null && methodMetadata.isPresent()) {

				boolean isQueryAnnotated = hierarchyAnnot.isAnnotatedWith(mb, Annotations.DATA_JPA_QUERY)
						|| hierarchyAnnot.isAnnotatedWith(mb, Annotations.DATA_MONGODB_QUERY);
				

				if (!isQueryAnnotated) {
					codeLenses.add(new CodeLens(range, refactorings.createFixCommand(COVERT_TO_QUERY_LABEL, createFixDescriptor(mb, document.getUri(), metadata, methodMetadata.get())), null));
				}
				
				Command impl = new Command("Implementation", GenAotQueryMethodImplProvider.CMD_NAVIGATE_TO_IMPL, List.of(new GenAotQueryMethodImplProvider.GoToImplParams(
						document.getId(),
						mb.getDeclaringClass().getQualifiedName(),
						mb.getName(),
						Arrays.stream(mb.getParameterTypes()).map(p -> p.getQualifiedName()).toArray(String[]::new),
						null
				)));
				codeLenses.add(new CodeLens(range, impl, null));
				
				if (!isQueryAnnotated) {
					String queryStatement = methodMetadata.get().getQueryStatement();
					if (queryStatement != null) {
						Command queryTitle = new Command();
						queryTitle.setTitle(queryStatement);
						codeLenses.add(new CodeLens(range, queryTitle, null));
					}
				}
			}
		} catch (BadLocationException e) {
			log.error("bad location while calculating code lens for data repository query method", e);
		}
		return codeLenses;
	}

	static FixDescriptor createFixDescriptor(IMethodBinding mb, String docUri, DataRepositoryAotMetadata metadata, IDataRepositoryAotMethodMetadata methodMetadata) {
		return new FixDescriptor(AddAnnotationOverMethod.class.getName(), List.of(docUri), "Turn into `@Query`")
				
				.withRecipeScope(RecipeScope.FILE)
				
				.withParameters(Map.of(
						"annotationType", metadata.module() == DataRepositoryModule.JPA ? Annotations.DATA_JPA_QUERY : Annotations.DATA_MONGODB_QUERY,
						"method", "%s %s(%s)".formatted(mb.getDeclaringClass().getQualifiedName(), mb.getName(),
								Arrays.stream(mb.getParameterTypes())
									.map(pt -> pt.getQualifiedName())
									.collect(Collectors.joining(", "))),
						"attributes", createAttributeList(methodMetadata.getAttributesMap())));
	}
	
	private static List<AddAnnotationOverMethod.Attribute> createAttributeList(Map<String, String> attributes) {
		List<AddAnnotationOverMethod.Attribute> result = new ArrayList<>();
		
		Set<String> keys = attributes.keySet();
		for (String key : keys) {
			result.add(new AddAnnotationOverMethod.Attribute(key, "\"%s\"".formatted(StringEscapeUtils.escapeJava(attributes.get(key)))));
		}

		return result;
	}
	
	

}
