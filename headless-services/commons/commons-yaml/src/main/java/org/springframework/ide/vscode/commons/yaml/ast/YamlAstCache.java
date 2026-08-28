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
package org.springframework.ide.vscode.commons.yaml.ast;

import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.util.StaleFallbackCache;

public class YamlAstCache {

	private final StaleFallbackCache<String, YamlFileAST> asts = new StaleFallbackCache<>();
	private final YamlParser parser;

	public YamlAstCache() {
		this.parser = new YamlParser();
	}

	public YamlASTProvider getAstProvider(boolean allowStaleAsts) {
		return (IDocument doc) -> {
			String uri = doc.getUri();
			if (uri != null) {
				return asts.get(uri, doc.getVersion(), allowStaleAsts, () -> {
					return parser.getAST(doc);
				});
			}
			return null;
		};
	}

	public YamlFileAST getSafeAst(IDocument doc) {
		return getSafeAst(doc, true);
	}

	public YamlFileAST getAst(IDocument doc, boolean allowStaleAst) throws Exception {
		return getAstProvider(allowStaleAst).getAST(doc);
	}

	public YamlFileAST getSafeAst(IDocument doc, boolean allowStaleAst) {
		if (doc != null) {
			try {
				return getAst(doc, allowStaleAst);
			} catch (Exception e) {
				//ignored
			}
		}
		return null;
	}

}
