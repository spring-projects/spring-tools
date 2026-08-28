/*******************************************************************************
 * Copyright (c) 2016, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.eclipse.lsp4j.CompletionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.hover.HoverInfoProvider;
import org.springframework.ide.vscode.commons.languageserver.hover.VscodeHoverEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.completion.SchemaBasedYamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.completion.YamlCompletionEngine;
import org.springframework.ide.vscode.commons.yaml.completion.YamlCompletionEngineOptions;
import org.springframework.ide.vscode.commons.yaml.hover.YamlHoverInfoProvider;
import org.springframework.ide.vscode.commons.yaml.quickfix.YamlQuickfixes;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.reconcile.TypeBasedYamlHierarchicalSymbolHandler.HierarchicalDefType;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaBasedReconcileEngine;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YamlSchema;
import org.springframework.ide.vscode.commons.yaml.snippet.SchemaBasedSnippetGenerator;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;
import org.springframework.ide.vscode.concourse.github.GithubInfoProvider;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

@Component
public class ConcourseLanguageServerInitializer {

	private static final Logger log = LoggerFactory.getLogger(ConcourseLanguageServerInitializer.class);

	private final YamlCompletionEngineOptions COMPLETION_OPTIONS = YamlCompletionEngineOptions.DEFAULT;
	private final YamlStructureProvider structureProvider = YamlStructureProvider.DEFAULT;

	private SchemaSpecificPieces forPipelines;
	private SchemaSpecificPieces forTasks;
	private YamlQuickfixes yamlQuickfixes;
	private YamlASTProvider currentAsts;

	@Autowired private SimpleLanguageServer server;
	@Autowired private GithubInfoProvider github;
	@Autowired private ASTTypeCache astTypeCache;
	@Autowired private ApplicationContext appContext;
	@Autowired private ConcourseModel models;
	@Autowired private PipelineYmlSchema pipelineSchema;

	private class SchemaSpecificPieces {

		final VscodeCompletionEngineAdapter completionEngine;
		final VscodeHoverEngineAdapter hoverEngine;
		final YamlSchemaBasedReconcileEngine reconcileEngine;
//		final DocumentSymbolHandler symbolHandler;

		SchemaSpecificPieces(YamlSchema schema, List<YType> definitionTypes, List<HierarchicalDefType> hierarchicalDefinitions) {
			SchemaBasedYamlAssistContextProvider contextProvider = new SchemaBasedYamlAssistContextProvider(schema);
			YamlCompletionEngine yamlCompletionEngine = new YamlCompletionEngine(structureProvider, contextProvider, COMPLETION_OPTIONS);
			this.completionEngine = server.createCompletionEngineAdapter(yamlCompletionEngine);

			HoverInfoProvider infoProvider = new YamlHoverInfoProvider(currentAsts, structureProvider, contextProvider);
			this.hoverEngine = new VscodeHoverEngineAdapter(server, infoProvider);

			this.reconcileEngine = new YamlSchemaBasedReconcileEngine(currentAsts, schema, yamlQuickfixes, appContext);

//			this.symbolHandler = CollectionUtil.hasElements(definitionTypes)
//					? new TypeBasedYamlSymbolHandler(server.getTextDocumentService(), astTypeCache, definitionTypes)
//					: DocumentSymbolHandler.NO_SYMBOLS;
		}

		public void setMaxCompletions(int max) {
			completionEngine.setMaxCompletions(max);
		}
	}

	public void enableSnippets(PipelineYmlSchema schema, boolean enable) {
		//TODO: move to where schema bean is defined?
		if (enable) {
			schema.f.setSnippetProvider(new SchemaBasedSnippetGenerator(schema.getTypeUtil(), server::createSnippetBuilder));
		} else {
			schema.f.setSnippetProvider(null);
		}
	}

	@PostConstruct
	public void afterPropertiesSet() throws Exception {
		this.currentAsts = models.getAstCache().getAstProvider(false);
		enableSnippets(pipelineSchema, true);
		SimpleTextDocumentService documents = server.getTextDocumentService();
		this.yamlQuickfixes = new YamlQuickfixes(server.getQuickfixRegistry(), documents, structureProvider);

		this.forPipelines = new SchemaSpecificPieces(pipelineSchema, pipelineSchema.getDefinitionTypes(), pipelineSchema.getHierarchicalDefinitionTypes());
		this.forTasks = new SchemaSpecificPieces(pipelineSchema.getTaskSchema(), null, null);
		ConcourseDefinitionFinder definitionFinder = new ConcourseDefinitionFinder(server, models, pipelineSchema, astTypeCache);

//		SimpleWorkspaceService workspace = getWorkspaceService();
		documents.onDidChangeContent(params -> {
			TextDocument doc = params.getDocument();
			if (LanguageId.CONCOURSE_PIPELINE.equals(doc.getLanguageId())) {
				server.validateWith(doc.getId(), forPipelines.reconcileEngine);
			} else if (LanguageId.CONCOURSE_TASK.equals(doc.getLanguageId())) {
				server.validateWith(doc.getId(), forTasks.reconcileEngine);
			} else {
				server.validateWith(doc.getId(), IReconcileEngine.NULL);
			}
		});

//		workspace.onDidChangeConfiguraton(settings -> {
//			Integer val = settings.getInt("languageServerExample", "maxNumberOfProblems");
//			if (val != null) {
//				maxProblems = ((Number) val).intValue();
//				for (TextDocument doc : documents.getAll()) {
//					validateDocument(documents, doc);
//				}
//			}
//		});

		documents.onCompletion((cancelToken, params) -> {
			TextDocument doc = documents.getLatestSnapshot(params);
			if (doc != null) {
				if (LanguageId.CONCOURSE_PIPELINE.equals(doc.getLanguageId())) {
					return forPipelines.completionEngine.getCompletions(cancelToken, params);
				} else if (LanguageId.CONCOURSE_TASK.equals(doc.getLanguageId())) {
					return forTasks.completionEngine.getCompletions(cancelToken, params);
				}
			}
			return new CompletionList(false, ImmutableList.of());
		});

		documents.onCompletionResolve((cancelToken, item) -> {
			server.completionResolver.resolveNow(cancelToken, item);
			return item;
		});

		documents.onHover((cancelToken, params) -> {
			log.debug("Concourse hover handler starting");
			try {
				TextDocument doc = documents.getLatestSnapshot(params);
				if (doc != null) {
					LanguageId languageId = doc.getLanguageId();
					if (LanguageId.CONCOURSE_PIPELINE.equals(doc.getLanguageId())) {
						return forPipelines.hoverEngine.handle(cancelToken, params);
					} else if (LanguageId.CONCOURSE_TASK.equals(doc.getLanguageId())) {
						return forTasks.hoverEngine.handle(cancelToken, params);
					} else {
						log.debug("No hovers because language-id = {}", languageId);
					}
				} else {
					log.debug("No hovers because doc is null");
				}
				return SimpleTextDocumentService.NO_HOVER;
			} finally {
				log.debug("Concourse hover handler finished");
			}
		});
		documents.onDefinition(definitionFinder);
//		documents.onDocumentSymbol((params) -> {
//			DocumentSymbolHandler handler = DocumentSymbolHandler.NO_SYMBOLS;
//			TextDocument doc = documents.getDocument(params.getTextDocument().getUri());
//			if (doc != null) {
//				if (LanguageId.CONCOURSE_PIPELINE.equals(doc.getLanguageId())) {
//					handler = forPipelines.symbolHandler;
//				} else if (LanguageId.CONCOURSE_TASK.equals(doc.getLanguageId())) {
//					handler = forTasks.symbolHandler;
//				}
//			}
//			return handler.handle(params);
//		});
	}

//	@Override
//	protected DiagnosticSeverity getDiagnosticSeverity(ReconcileProblem problem) {
//		ProblemType type = problem.getType();
//		if (YamlSchemaProblems.PROPERTY_CONSTRAINT.contains(type)) {
//			return DiagnosticSeverity.Warning;
//		}
//		return server.getDiagnosticSeverity(problem);
//	}

	public void setMaxCompletions(int max) {
		forPipelines.setMaxCompletions(max);
		forTasks.setMaxCompletions(max);
	}

}
