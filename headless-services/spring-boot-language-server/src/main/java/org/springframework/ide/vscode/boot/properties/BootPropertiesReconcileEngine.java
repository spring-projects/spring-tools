/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties;

import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.properties.quickfix.AppPropertiesQuickFixes;
import org.springframework.ide.vscode.boot.properties.quickfix.CommonQuickfixes;
import org.springframework.ide.vscode.boot.properties.reconcile.SpringPropertiesReconcileEngine;
import org.springframework.ide.vscode.boot.yaml.quickfix.AppYamlQuickfixes;
import org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;

/**
 * Reconciles Spring Boot configuration files ({@code application*.properties} and
 * {@code application*.yml}/{@code .yaml}, plus their {@code bootstrap*} variants), producing the
 * {@link SpringProblemCategories#PROPERTIES} and {@link SpringProblemCategories#YAML} diagnostics.
 *
 * <p>Both underlying engines and their quick fixes are created eagerly, so the engine is usable
 * before an LSP client has sent {@code initialize} - or when none ever connects, as in the
 * MCP-only standalone mode. Quick fixes that depend on client capabilities (creating the
 * additional metadata file) are gated inside {@link CommonQuickfixes} instead.
 *
 * @author Martin Lippert
 */
public class BootPropertiesReconcileEngine implements IReconcileEngine {

	public static final String[] YML = {".yml", ".yaml" } ;
	public static final String PROPERTIES = ".properties";

	private final SpringPropertiesReconcileEngine propertiesReconciler;
	private final ApplicationYamlReconcileEngine ymlReconciler;

	public BootPropertiesReconcileEngine(SimpleLanguageServer server, BootLanguageServerParams params,
			YamlASTProvider parser, YamlStructureProvider yamlStructureProvider, SourceLinks sourceLinks) {
		CommonQuickfixes commonQuickfixes = new CommonQuickfixes(server.getQuickfixRegistry(), params.projectFinder,
				server.getClientCapabilities());
		this.propertiesReconciler = new SpringPropertiesReconcileEngine(params.indexProvider, params.typeUtilProvider,
				new AppPropertiesQuickFixes(server.getQuickfixRegistry(), commonQuickfixes), sourceLinks);
		this.ymlReconciler = new ApplicationYamlReconcileEngine(parser, params.indexProvider, params.typeUtilProvider,
				new AppYamlQuickfixes(server.getQuickfixRegistry(), server.getTextDocumentService(), yamlStructureProvider,
						commonQuickfixes), sourceLinks);
	}

	/** Whether the URI or file path has one of the config-file extensions this engine handles. */
	public static boolean isConfigFile(String uriOrPath) {
		if (uriOrPath == null) {
			return false;
		}
		if (uriOrPath.endsWith(PROPERTIES)) {
			return true;
		}
		for (String yml : YML) {
			if (uriOrPath.endsWith(yml)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void reconcile(IDocument doc, IProblemCollector problemCollector) {
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
	}

}
