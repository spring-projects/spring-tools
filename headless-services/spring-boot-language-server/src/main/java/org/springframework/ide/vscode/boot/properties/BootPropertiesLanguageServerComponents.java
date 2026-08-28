/*******************************************************************************
 * Copyright (c) 2016, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties;

import java.util.Optional;
import java.util.Set;

import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.java.links.JavaElementLocationProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtilProvider;
import org.springframework.ide.vscode.boot.properties.hover.PropertiesHoverInfoProvider;
import org.springframework.ide.vscode.boot.properties.quickfix.AppPropertiesQuickFixes;
import org.springframework.ide.vscode.boot.properties.quickfix.CommonQuickfixes;
import org.springframework.ide.vscode.boot.properties.reconcile.SpringPropertiesReconcileEngine;
import org.springframework.ide.vscode.boot.yaml.quickfix.AppYamlQuickfixes;
import org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.composable.LanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.hover.HoverInfoProvider;
import org.springframework.ide.vscode.commons.languageserver.hover.VscodeHoverEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.util.HoverHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.hover.YamlHoverInfoProvider;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;

import com.google.common.collect.ImmutableSet;

/**
 * Language Server for Spring Boot Application Properties files
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class BootPropertiesLanguageServerComponents implements LanguageServerComponents {

	public static final String[] YML = {".yml", ".yaml" } ;
	public static final String PROPERTIES = ".properties";

	private static final Set<LanguageId> LANGUAGES = ImmutableSet.of(
			LanguageId.BOOT_PROPERTIES,
			LanguageId.BOOT_PROPERTIES_YAML
	);

	// Shared:
	private final JavaProjectFinder javaProjectFinder;
	private final SpringPropertyIndexProvider indexProvider;
	private final TypeUtilProvider typeUtilProvider;

	// For yaml
	private final YamlStructureProvider yamlStructureProvider;
	private YamlAssistContextProvider yamlAssistContextProvider;
	private final SimpleLanguageServer server;
	private YamlASTProvider parser;

	private SpringPropertiesReconcileEngine propertiesReconciler;
	private ApplicationYamlReconcileEngine ymlReconciler;
	private SourceLinks sourceLinks;

	public BootPropertiesLanguageServerComponents(
			SimpleLanguageServer server,
			BootLanguageServerParams serverParams,
			JavaElementLocationProvider javaElementLocationProvider,
			YamlASTProvider parser,
			YamlStructureProvider yamlStructureProvider,
			YamlAssistContextProvider yamlAssistContextProvider,
			SourceLinks sourceLinks) {
		this.server = server;
		this.parser = parser;
		this.indexProvider = serverParams.indexProvider;
		this.typeUtilProvider = serverParams.typeUtilProvider;
		this.javaProjectFinder = serverParams.projectFinder;
		this.yamlStructureProvider = yamlStructureProvider;
		this.yamlAssistContextProvider = yamlAssistContextProvider;
		this.sourceLinks = sourceLinks;

		server.getClientCapabilities().thenAccept(clientCapabilities -> {
			CommonQuickfixes commonQuickfixes = new CommonQuickfixes(server.getQuickfixRegistry(), javaProjectFinder,
					clientCapabilities);
			this.propertiesReconciler = new SpringPropertiesReconcileEngine(indexProvider,
					typeUtilProvider, new AppPropertiesQuickFixes(server.getQuickfixRegistry(), commonQuickfixes), sourceLinks);
			this.ymlReconciler = new ApplicationYamlReconcileEngine(parser, indexProvider, typeUtilProvider,
					new AppYamlQuickfixes(server.getQuickfixRegistry(), server.getTextDocumentService(),
							yamlStructureProvider, commonQuickfixes), sourceLinks);
		});

		indexProvider.onChange(() -> {
			getReconcileEngine().ifPresent(reconciler -> {
				server.getTextDocumentService().getAll().stream().filter(doc -> getInterestingLanguages().contains(doc.getLanguageId())).forEach(doc -> {
					server.validateWith(doc.getId(), reconciler);
				});
			});
		});
		
		// Register YAML -> Props conversion command
		new YamlToPropertiesCommand(server);
		
		// Register Props -> YAML conversion command
		new PropertiesToYamlCommand(server);
	}

	@Override
	public Set<LanguageId> getInterestingLanguages() {
		return LANGUAGES;
	}

	@Override
	public HoverHandler getHoverProvider() {
		HoverInfoProvider propertiesHovers = new PropertiesHoverInfoProvider(indexProvider, typeUtilProvider, javaProjectFinder, sourceLinks);
		HoverInfoProvider ymlHovers = new YamlHoverInfoProvider(parser, yamlStructureProvider, yamlAssistContextProvider);

		HoverInfoProvider combined = (IDocument document, int offset) -> {
			String uri = document.getUri();
			if (uri != null) {
				if (uri.endsWith(PROPERTIES)) {
					return propertiesHovers.getHoverInfo(document, offset);
				} else {
					for (String yml : YML) {
						if (uri.endsWith(yml)) {
							return ymlHovers.getHoverInfo(document, offset);
						}
					}
				}
			}
			return null;
		};
		return new VscodeHoverEngineAdapter(server, combined);
	}

	@Override
	public Optional<IReconcileEngine> getReconcileEngine() {
		return Optional.of((doc, problemCollector) -> {
			String uri = doc.getUri();
			if (uri != null) {
				if (uri.endsWith(PROPERTIES)) {
					propertiesReconciler.reconcile(doc, problemCollector);
					return;
				} else {
					for (String yml : YML) {
						if (uri.endsWith(yml)) {
							ymlReconciler.reconcile(doc, problemCollector);
							return;
						}
					}
				}
			}
			//No real reconciler is applicable. So tell the problemCollector there are no problems.
			problemCollector.beginCollecting();
			problemCollector.endCollecting();
		});
	}

	public SpringPropertyIndexProvider getPropertiesIndexProvider() {
		return indexProvider;
	}

}
