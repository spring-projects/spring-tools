/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.cron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.cron.CronExpressionsInlayHintsProvider;
import org.springframework.ide.vscode.boot.properties.cron.CronProperties.CronValue;
import org.springframework.ide.vscode.commons.languageserver.util.InlayHintHandler;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;

/**
 * Explains the CRON expressions in <code>application.properties</code> and <code>application.yml</code> files
 * the same way as it is done for <code>@Scheduled</code> annotations in Java code.
 *
 * @author Alex Boyko
 */
public class CronPropertiesInlayHintsHandler implements InlayHintHandler {

	private static final Logger log = LoggerFactory.getLogger(CronPropertiesInlayHintsHandler.class);

	private final BooleanSupplier enabled;
	private final YamlASTProvider yamlParser;

	public CronPropertiesInlayHintsHandler(BooleanSupplier enabled, YamlASTProvider yamlParser) {
		this.enabled = enabled;
		this.yamlParser = yamlParser;
	}

	@Override
	public List<InlayHint> handle(TextDocument doc, Range range, CancelChecker cancelChecker) {
		if (doc != null && enabled.getAsBoolean()) {
			try {
				List<InlayHint> hints = new ArrayList<>();
				for (CronValue cron : CronProperties.findValues(doc, yamlParser)) {
					CronExpressionsInlayHintsProvider.describe(cron.text()).ifPresent(description -> {
						try {
							Position position = doc.toPosition(cron.end());
							if (isInRange(range, position)) {
								hints.add(CronExpressionsInlayHintsProvider.createHint(description, position));
							}
						} catch (Exception e) {
							log.error("", e);
						}
					});
				}
				return hints;
			} catch (Exception e) {
				log.error("", e);
			}
		}
		return Collections.emptyList();
	}

	private static boolean isInRange(Range range, Position p) {
		return range == null || ((range.getStart() == null || compare(range.getStart(), p) <= 0)
				&& (range.getEnd() == null || compare(p, range.getEnd()) <= 0));
	}

	private static int compare(Position p1, Position p2) {
		return p1.getLine() != p2.getLine() ? Integer.compare(p1.getLine(), p2.getLine())
				: Integer.compare(p1.getCharacter(), p2.getCharacter());
	}

}
