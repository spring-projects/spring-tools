/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.quickfix;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixEdit;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.protocol.CursorMovement;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

/**
 * Boot app Properties file quick fix handlers
 *
 * @author Alex Boyko
 *
 */
public class AppPropertiesQuickFixes {

	private static final Logger log = LoggerFactory.getLogger(AppPropertiesQuickFixes.class);

	private static final QuickfixEdit NULL_FIX = new QuickfixEdit(
			new WorkspaceEdit(ImmutableMap.of()),
			null
	);

	public final QuickfixType DEPRECATED_PROPERTY;
	private final CommonQuickfixes commonFixes;

	private final Gson gson = new Gson();

	public AppPropertiesQuickFixes(QuickfixRegistry r, CommonQuickfixes commonFixes) {
		this.commonFixes = commonFixes;
		DEPRECATED_PROPERTY = r.register("DEPRECATED_PROPERTY", (Object _params) -> Mono.fromSupplier(() -> {
			DeprecatedPropertyData params = gson.fromJson((JsonElement)_params, DeprecatedPropertyData.class);
			try {
				if (params.getRange() != null && params.getReplacement() != null) {
					WorkspaceEdit wsEdits = new WorkspaceEdit();
					wsEdits.setChanges(ImmutableMap.of(
							params.getUri(),
							ImmutableList.of(new TextEdit(params.getRange(), params.getReplacement()))
					));
					Position start = params.getRange().getStart();
					Position cursor = new Position(start.getLine(), start.getCharacter() + params.getReplacement().length());
					return new QuickfixEdit(wsEdits, new CursorMovement(params.getUri(), cursor));
				}
			} catch (Exception e) {
				log.error("", e);
			}
			return NULL_FIX;
		}));
	}

	/** The "create metadata" fix, or {@code null} while the client cannot create files. */
	public QuickfixType getMissingPropertyFix() {
		return commonFixes.getMissingPropertyFix();
	}

}
