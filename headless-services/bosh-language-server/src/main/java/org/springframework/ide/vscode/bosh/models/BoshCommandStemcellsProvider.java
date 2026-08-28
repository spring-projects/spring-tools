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

import java.util.Collection;

import org.springframework.ide.vscode.bosh.BoshCliConfig;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.ExternalCommand;
import org.springframework.ide.vscode.commons.util.GsonUtil;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlTraversal;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.util.JSONCursor;

public class BoshCommandStemcellsProvider extends BoshCommandBasedModelProvider<StemcellsModel> {

	private static final YamlTraversal STEMCELLS = YamlPath.EMPTY
			.thenValAt("Tables")
			.thenAnyChild()
			.thenValAt("Rows")
			.thenAnyChild();

	private static final YamlTraversal STEMCELL_NAMES = STEMCELLS
			.thenValAt("name");

	private static final YamlTraversal STEMCELL_OSS = STEMCELLS
			.thenValAt("os");

	private static final YamlTraversal STEMCELL_VERSIONS = STEMCELLS
			.thenValAt("version");

	public BoshCommandStemcellsProvider(BoshCliConfig config) {
		super(config);
	}

	@Override
	public StemcellsModel getModel(DynamicSchemaContext dc) throws Exception {
		JSONCursor cursor = new JSONCursor(getJsonTree());
		return new StemcellsModel() {

			@Override
			public Collection<String> getStemcellNames() {
				return getNames(cursor, STEMCELL_NAMES);
			}

			@Override
			public Collection<StemcellData> getStemcells() {
				return STEMCELLS.traverseAmbiguously(cursor)
				.map(c -> new StemcellData(
						getStringProperty(c, "name"),
						getStringProperty(c, "version"),
						getStringProperty(c, "os")
				))
				.collect(CollectorUtil.toImmutableList());
			}

			private String getStringProperty(JSONCursor c, String prop) {
				c = YamlPath.EMPTY.thenValAt(prop).traverse(c);
				if (c != null) {
					return GsonUtil.getAsString(c.target);
				}
				return null;
			}

			@Override
			public Collection<String> getStemcellOss() {
				return getNames(cursor, STEMCELL_OSS);
			}

			@Override
			public Collection<String> getVersions() {
				return getNames(cursor, STEMCELL_VERSIONS);
			}
		};
	}

	@Override
	protected String[] getBoshCommand() {
		return new String[] { "stemcells", "--json" };
	}

}
