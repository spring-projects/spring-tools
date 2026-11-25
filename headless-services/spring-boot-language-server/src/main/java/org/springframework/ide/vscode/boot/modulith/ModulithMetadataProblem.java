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
package org.springframework.ide.vscode.boot.modulith;

/**
 * Why the Spring Modulith metadata of a project could not be (re-)created.
 *
 * @param code what went wrong
 * @param label a few words to show next to the project, e.g. in the structure view
 * @param message a sentence describing the problem
 *
 * @author Martin Lippert
 */
public record ModulithMetadataProblem(Code code, String label, String message) {

	public enum Code {
		/** the project has not been built, so there is nothing the exporter could read */
		NO_CLASS_FILES,
		/** no Spring Boot application class was found to start the exporter from */
		NO_APPLICATION_CLASS,
		/** running the exporter or reading its result failed */
		EXPORT_FAILED
	}

	public static ModulithMetadataProblem noClassFiles() {
		return new ModulithMetadataProblem(Code.NO_CLASS_FILES, "project not built",
				"No Spring Modulith metadata available: the project output folder does not contain any '.class' files. Build the project first.");
	}

	public static ModulithMetadataProblem noApplicationClass() {
		return new ModulithMetadataProblem(Code.NO_APPLICATION_CLASS, "no application class",
				"No Spring Modulith metadata available: no @SpringBootApplication class was found in the project.");
	}

	public static ModulithMetadataProblem exportFailed(int exitCode, String errorOutput) {
		String lastLine = lastNonBlankLine(errorOutput);
		return exportFailed("exit code " + exitCode + (lastLine == null ? "" : ": " + lastLine));
	}

	public static ModulithMetadataProblem exportFailed(Throwable t) {
		return exportFailed(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
	}

	private static ModulithMetadataProblem exportFailed(String reason) {
		return new ModulithMetadataProblem(Code.EXPORT_FAILED, "metadata export failed",
				"Spring Modulith metadata export failed (" + reason + ")");
	}

	private static String lastNonBlankLine(String text) {
		if (text == null) {
			return null;
		}
		String[] lines = text.split("\\R");
		for (int i = lines.length - 1; i >= 0; i--) {
			if (!lines[i].isBlank()) {
				return lines[i].strip();
			}
		}
		return null;
	}

}
