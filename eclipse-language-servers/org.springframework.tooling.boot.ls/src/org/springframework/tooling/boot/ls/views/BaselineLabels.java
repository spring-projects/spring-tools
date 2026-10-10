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
package org.springframework.tooling.boot.ls.views;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.DateTimeParseException;

/**
 * How a captured baseline snapshot is described to the user, shared by the dialog that lists them
 * and the tooltip that names the selected one, so the two cannot drift apart.
 * 
 * @author Martin Lippert
 */
final class BaselineLabels {

	private BaselineLabels() {
	}

	static String shortSha(String sha) {
		return sha.length() > 7 ? sha.substring(0, 7) : sha;
	}

	static String formatCapturedAt(String capturedAt) {
		try {
			Instant captured = capturedAt.endsWith("Z") ? Instant.parse(capturedAt) : OffsetDateTime.parse(capturedAt).toInstant();
			return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(captured);
		} catch (DateTimeParseException e) {
			return capturedAt;
		}
	}

	/**
	 * One line describing a retained snapshot: its commit if it has one, otherwise the fact that it
	 * was captured manually, plus when.
	 * 
	 * @param commitSha the commit the snapshot was captured for - none for a manual one
	 * @param commitMessage the message of that commit
	 * @param capturedAt when the snapshot was captured - for a manual snapshot the only thing
	 *        identifying it
	 */
	static String describe(String commitSha, String commitMessage, String capturedAt) {
		if (commitSha != null && !commitSha.isEmpty()) {
			return shortSha(commitSha) + " - " + (commitMessage == null || commitMessage.isEmpty() ? "(no commit message)" : commitMessage);
		}
		return "manual snapshot from " + (capturedAt == null ? "an unknown time" : formatCapturedAt(capturedAt));
	}

}
