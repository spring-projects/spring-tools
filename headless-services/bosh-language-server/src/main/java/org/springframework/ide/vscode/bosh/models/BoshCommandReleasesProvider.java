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
import java.util.List;

import org.springframework.ide.vscode.bosh.BoshCliConfig;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.GsonUtil;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlTraversal;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.util.JSONCursor;

public class BoshCommandReleasesProvider  extends BoshCommandBasedModelProvider<ReleasesModel> {

	private static final String[] COMMAND = new String[] { "releases", "--json" };

	private static final YamlTraversal RELEASES = YamlPath.EMPTY
			.thenValAt("Tables")
			.thenAnyChild()
			.thenValAt("Rows")
			.thenAnyChild();

	private static final YamlTraversal RELEASE_NAMES = RELEASES
			.thenValAt("name");

	private static final YamlTraversal RELEASE_VERSIONS = RELEASES
			.thenValAt("version");

	public BoshCommandReleasesProvider(BoshCliConfig config) {
		super(config);
	}

	@Override
	public ReleasesModel getModel(DynamicSchemaContext dc) throws Exception {
		JSONCursor cursor = new JSONCursor(getJsonTree());
		return new ReleasesModel() {
			@Override
			public List<ReleaseData> getReleases() {
				return RELEASES.traverseAmbiguously(cursor)
				.map(c -> new ReleaseData(
						getStringProperty(c, "name"),
						getStringProperty(c, "version")
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
			public Collection<String> getReleaseNames() {
				return getNames(cursor, RELEASE_NAMES);
			}

			@Override
			public Collection<String> getVersions() {
				return getNames(cursor, RELEASE_VERSIONS);
			}
		};
	}

	@Override
	protected String[] getBoshCommand() {
		return COMMAND;
	}

}