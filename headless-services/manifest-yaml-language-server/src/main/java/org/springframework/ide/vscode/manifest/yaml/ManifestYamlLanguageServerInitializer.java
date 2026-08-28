/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.ide.vscode.commons.cloudfoundry.client.ClientTimeouts;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CloudFoundryClientFactory;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CFTargetCache;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CfCliParamsProvider;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CfTargetsInfo;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.CfTargetsInfoProvder;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.ClientParamsProvider;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.NoTargetsException;
import org.springframework.ide.vscode.commons.cloudfoundry.client.v2.DefaultCloudFoundryClientFactoryV2;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.hover.HoverInfoProvider;
import org.springframework.ide.vscode.commons.languageserver.hover.VscodeHoverEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.util.HoverHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.ast.YamlParser;
import org.springframework.ide.vscode.commons.yaml.completion.SchemaBasedYamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.completion.YamlCompletionEngine;
import org.springframework.ide.vscode.commons.yaml.completion.YamlCompletionEngineOptions;
import org.springframework.ide.vscode.commons.yaml.hover.YamlHoverInfoProvider;
import org.springframework.ide.vscode.commons.yaml.quickfix.YamlQuickfixes;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.reconcile.TypeBasedYamlSymbolHandler;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaBasedReconcileEngine;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;
import org.springframework.ide.vscode.commons.yaml.snippet.SchemaBasedSnippetGenerator;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

@Component
public class ManifestYamlLanguageServerInitializer implements InitializingBean {

	private CfJson cfJson = new CfJson();
	private ManifestYmlSchema schema;
	private CFTargetCache cfTargetCache;

	private CloudFoundryClientFactory cfClientFactory;
	ClientParamsProvider defaultClientParamsProvider;

	@Autowired private ApplicationContext appContext;
	@Autowired private SimpleLanguageServer server;
	@Autowired private ASTTypeCache astTypeCache;

	@Override
	public void afterPropertiesSet() throws Exception {
		this.cfTargetCache = new CFTargetCache(ImmutableList.of(this.defaultClientParamsProvider), cfClientFactory, new ClientTimeouts());

		SimpleTextDocumentService documents = server.getTextDocumentService();
		SimpleWorkspaceService workspace = server.getWorkspaceService();

		YamlASTProvider parser = new YamlParser();

		schema = new ManifestYmlSchema(getHintProviders());
		enableSnippets(schema, true);

		YamlStructureProvider structureProvider = YamlStructureProvider.DEFAULT;
		YamlAssistContextProvider contextProvider = new SchemaBasedYamlAssistContextProvider(schema);
		YamlCompletionEngine yamlCompletionEngine = new YamlCompletionEngine(structureProvider, contextProvider, YamlCompletionEngineOptions.DEFAULT);
		VscodeCompletionEngineAdapter completionEngine = server.createCompletionEngineAdapter(yamlCompletionEngine);
		HoverInfoProvider infoProvider = new YamlHoverInfoProvider(parser, structureProvider, contextProvider);
		HoverHandler hoverEngine = new VscodeHoverEngineAdapter(server, infoProvider);
		YamlQuickfixes quickfixes = new YamlQuickfixes(server.getQuickfixRegistry(), server.getTextDocumentService(), structureProvider);
		YamlSchemaBasedReconcileEngine engine = new YamlSchemaBasedReconcileEngine(parser, schema, quickfixes, appContext);

		documents.onDocumentSymbol(new TypeBasedYamlSymbolHandler(documents, astTypeCache, schema.getDefinitionTypes()));

		documents.onDidChangeContent(params -> {
			validateOnDocumentChange(engine, params.getDocument());
		});

		// Workaround for PT 147263283, where error markers in STS are lost on document save.
		// STS 3.9.0 does not use the LSP4E editor for edit manifest.yml, which correctly retains error markers after save.
		// Instead, because the LSP4E editor is missing support for hovers and completions, STS 3.9.0 uses its own manifest editor
		// which extends the YEdit editor. This YEdit editor has a problem, where on save, all error markers are deleted.
		// When STS uses the LSP4E editor and no longer needs its own YEdit-based editor, the issue with error markers disappearing
		// on save should not be a problem anymore, and the workaround below will no longer be needed.
		documents.onDidSave(params -> {
			validateOnDocumentChange(engine, params.getDocument());
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

		documents.onCompletion(completionEngine::getCompletions);
		documents.onCompletionResolve(completionEngine::resolveCompletion);
		documents.onHover(hoverEngine);

		workspace.onDidChangeConfiguraton(settings -> {
			CfTargetsInfo info = cfJson.from(settings);
			if (info != null) {
				applyCfLoginParameterSettings(info);
			}
		});
	}

	private final ImmutableSet<LanguageId> FALLBACK_YML_IDS = ImmutableSet.of(LanguageId.of("yml"), LanguageId.of("yaml"));

	/**
	 * Deprecated: should use the @Autowired constructor instead to inject CF client.
	 */
	@Deprecated
	public ManifestYamlLanguageServerInitializer() {
		this(DefaultCloudFoundryClientFactoryV2.INSTANCE, CfCliParamsProvider.getInstance());
	}

	@Autowired
	public ManifestYamlLanguageServerInitializer(CloudFoundryClientFactory cfClientFactory, ClientParamsProvider defaultClientParamsProvider) {
		this.cfClientFactory = cfClientFactory;
		this.defaultClientParamsProvider = defaultClientParamsProvider;
	}

	private void applyCfLoginParameterSettings(CfTargetsInfo info) {
		// Refresh the list of providers
		CfTargetsInfoProvder cfClientParamsProvider = new CfTargetsInfoProvder(info);
		// set providers in the order that they should be called
		cfTargetCache.setProviders(defaultClientParamsProvider, cfClientParamsProvider);
	}

	private void validateOnDocumentChange(IReconcileEngine engine, TextDocument doc) {
		if (LanguageId.CF_MANIFEST.equals(doc.getLanguageId())
				|| FALLBACK_YML_IDS.contains(doc.getLanguageId())) {
			//
			// this FALLBACK_YML_ID got introduced to workaround a limitation in LSP4E, which sets the file extension as language ID to the document
			//
			server.validateWith(doc.getId(), engine);
		} else {
			server.validateWith(doc.getId(), IReconcileEngine.NULL);
		}
	}

	private ManifestYmlHintProviders getHintProviders() {
		Callable<Collection<YValueHint>> buildPacksProvider = new ManifestYamlCFBuildpacksProvider(getCfTargetCache());
		Callable<Collection<YValueHint>> servicesProvider = new ManifestYamlCFServicesProvider(getCfTargetCache());
		Callable<Collection<YValueHint>> domainsProvider = new ManifestYamlCFDomainsProvider(getCfTargetCache());
		Callable<Collection<YValueHint>> stacksProvider = new ManifestYamlStacksProvider(getCfTargetCache());

		return new ManifestYmlHintProviders() {

			@Override
			public Callable<Collection<YValueHint>> getServicesProvider() {
				return servicesProvider;
			}

			@Override
			public Callable<Collection<YValueHint>> getDomainsProvider() {
				return domainsProvider;
			}

			@Override
			public Callable<Collection<YValueHint>> getBuildpackProviders() {
				return buildPacksProvider;
			}

			@Override
			public Callable<Collection<YValueHint>> getStacksProvider() {
				return stacksProvider;
			}
		};
	}

	public void enableSnippets(ManifestYmlSchema schema, boolean enable) {
		//TODO: move to where schema bean is defined?
		if (enable) {
			schema.f.setSnippetProvider(new SchemaBasedSnippetGenerator(schema.getTypeUtil(), server::createSnippetBuilder));
		} else {
			schema.f.setSnippetProvider(null);
		}
	}

	private CFTargetCache getCfTargetCache() {
		return this.cfTargetCache;
	}

	public List<ClientParamsProvider> getParamsProvider() {
		return cfTargetCache.getParamsProviders();
	}

	/**
	 * Method added for testing purposes. Retuns list of CF targets available to the LS
	 * @return list of CF targets
	 */
	public List<String> getCfTargets() {
		try {
			return getCfTargetCache().getOrCreate()
					.stream()
					.map(target -> target.getName())
					.collect(Collectors.toList());
		} catch (NoTargetsException e) {
			// ignore
		} catch (Exception e) {
			// ignore
		}
		return Collections.emptyList();
	}
}
