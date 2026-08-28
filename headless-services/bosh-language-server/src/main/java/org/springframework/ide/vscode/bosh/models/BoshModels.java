/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.bosh.models;

import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlAstCache;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;

public class BoshModels {
	public final YamlAstCache asts = new YamlAstCache();
	public final ASTTypeCache astTypes;
	public final DynamicModelProvider<CloudConfigModel> cloudConfigProvider;
	public final DynamicModelProvider<StemcellsModel> stemcellsProvider;
	public final DynamicModelProvider<ReleasesModel> releasesProvider;

	public BoshModels(DynamicModelProvider<CloudConfigModel> cloudConfigProvider,
			DynamicModelProvider<StemcellsModel> stemcellsProvider,
			DynamicModelProvider<ReleasesModel> releasesProvider,
			ASTTypeCache astTypes
	) {
			this.cloudConfigProvider = cloudConfigProvider;
			this.stemcellsProvider = stemcellsProvider;
			this.releasesProvider = releasesProvider;
			this.astTypes = astTypes;
	}

	public String getTypeTag(DynamicSchemaContext dc) {
		YamlFileAST ast = asts.getSafeAst(dc.getDocument(), true);
		if (ast != null) {
			return NodeUtil.asScalar(dc.getPath().thenValAt("type").traverseToNode(ast));
		}
		return null;
	}

}
