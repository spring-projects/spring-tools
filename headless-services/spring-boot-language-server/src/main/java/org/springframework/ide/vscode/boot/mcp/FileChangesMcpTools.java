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
package org.springframework.ide.vscode.boot.mcp;

import java.io.File;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectChangeNotifier;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.FileChangeNotifier;

/**
 * MCP tools for notifying the language server about file changes on disk.
 * This bridges the gap when running in MCP-only mode without full LSP file watching.
 */
public class FileChangesMcpTools {

	private static final Logger logger = LoggerFactory.getLogger(FileChangesMcpTools.class);
	
	private static final String STATUS_OK = "OK";
	private static final String STATUS_FAILED = "Tool call failed: ";

	private final FileChangeNotifier fileChangeNotifier;
	private final ProjectChangeNotifier projectChangeNotifier;
	private final SimpleLanguageServer server;

	public FileChangesMcpTools(FileChangeNotifier fileChangeNotifier, ProjectChangeNotifier projectChangeNotifier, SimpleLanguageServer server) {

		logger.info("FileChangesMcpTools constructor called");

		this.fileChangeNotifier = fileChangeNotifier;
		this.projectChangeNotifier = projectChangeNotifier;
		this.server = server;
	}

	@McpTool(description = "Notifies the MCP Server that a file has been created or modified on disk so it can update its internal index and diagnostics.", annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public String fileChanged(
			@McpToolParam(description = "Absolute path to the file") String filePath) {

		if (filePath == null || filePath.startsWith("$")) {
			logger.warn("fileChanged called but could not extract file path. filePath={}", filePath);
			return STATUS_FAILED + "could not extract file path parameter from " + filePath;
		}

		if (server.getClient() != null) {
			logger.info("LSP client connected, skipping fileChanged MCP notification for: {}", filePath);
			return STATUS_FAILED + "LSP client connected, so don't need to call file changed for " + filePath;
		}

		logger.info("MCP fileChanged called for file: {}", filePath);

		try {

			URI uri = new File(filePath).toURI();
			fileChangeNotifier.notifyFileChanged(uri.toASCIIString());
			return STATUS_OK;

		} catch (Exception e) {
			logger.error("Failed to notify file changed for: " + filePath, e);
			return STATUS_FAILED + "failed to notifiy file changed listeners for " + filePath;
		}
		
	}

	@McpTool(description = "Notifies the MCP Server that a file has been deleted on disk so it can update its internal index and diagnostics.", annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public String fileDeleted(
			@McpToolParam(description = "Absolute path to the file") String filePath) {

		if (filePath == null || filePath.startsWith("$")) {
			logger.warn("fileDeleted called but could not extract file path. filePath={}", filePath);
			return STATUS_FAILED + "could not extract file path parameter from " + filePath;
		}

		if (server.getClient() != null) {
			logger.info("LSP client connected, skipping fileDeleted MCP notification for: {}", filePath);
			return STATUS_FAILED + "LSP client connected, so don't need to call file deleted for " + filePath;
		}

		logger.info("MCP fileDeleted called for file: {}", filePath);

		try {

			URI uri = new File(filePath).toURI();
			fileChangeNotifier.notifyFileDeleted(uri.toASCIIString());
			return STATUS_OK;

		} catch (Exception e) {
			logger.error("Failed to notify file deleted for: " + filePath, e);
			return STATUS_FAILED + "failed to notifiy file delected listeners for " + filePath;
		}

	}

	@McpTool(description = "Notifies the MCP Server to refresh the entire workspace (e.g., after a git checkout or pull).", annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public String refreshWorkspace() {
		logger.info("MCP refreshWorkspace called");

		if (server.getClient() != null) {
			logger.info("LSP client connected, skipping refreshWorkspace MCP notification.");
			return STATUS_FAILED + "LSP client connected, so don't need to refresh workspace";
		}

		projectChangeNotifier.notifyProjectsChanged(true);
		return STATUS_OK;
	}

}
