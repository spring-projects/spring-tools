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
package org.springframework.ide.vscode.bosh;

import static org.springframework.ide.vscode.bosh.BoshSchemaProblems.MISSING_SHA1_PROPERTY;
import static org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems.problem;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraint;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

public class BoshConstraints {

	public static final Constraint SHA1_REQUIRED_FOR_HTTP_URL = new Constraint() {

		@Override
		public void verify(DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) {
			NodeTuple urlProp = NodeUtil.getPropertyTuple(node, "url");
			if (urlProp != null) {
				String url = NodeUtil.asScalar(urlProp.getValueNode());
				if (url != null && url.startsWith("http")) {
					Node sha1 = NodeUtil.getProperty(node, "sha1");
					if (sha1 == null) {
						problems.accept(problem(MISSING_SHA1_PROPERTY, "'sha1' is recommended when the 'url' is http(s)", urlProp.getKeyNode()));
					}
				}
			}
		}
	};

}
