package org.springframework.ide.vscode.boot.mcp;

import java.io.File;
import java.net.URI;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectChangeNotifier;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.FileChangeNotifier;
import org.springframework.stereotype.Component;

/**
 * MCP tools for notifying the language server about file changes on disk.
 * This bridges the gap when running in MCP-only mode without full LSP file watching.
 */
@Component
//@ConditionalOnBean({FileChangeNotifier.class})
public class FileChangesMcpTools {

	private static final Logger logger = LoggerFactory.getLogger(FileChangesMcpTools.class);

	private final FileChangeNotifier fileChangeNotifier;
	private final Optional<ProjectChangeNotifier> projectChangeNotifier;
	private final SimpleLanguageServer server;

	public FileChangesMcpTools(FileChangeNotifier fileChangeNotifier, Optional<ProjectChangeNotifier> projectChangeNotifier, SimpleLanguageServer server) {

		logger.info("FileChangesMcpTools constructor called");

		this.fileChangeNotifier = fileChangeNotifier;
		this.projectChangeNotifier = projectChangeNotifier;
		this.server = server;
	}

	@Tool(description = "Notifies the MCP Server that a file has been created or modified on disk so it can update its internal index and diagnostics.")
	public void fileChanged(
			@ToolParam(description = "Absolute path to the file") String filePath) {

		if (filePath == null || filePath.startsWith("$")) {
			logger.warn("fileChanged called but could not extract file path. filePath={}", filePath);
			return;
		}

		if (server.getClient() != null) {
			logger.info("LSP client connected, skipping fileChanged MCP notification for: {}", filePath);
			return;
		}

		logger.info("MCP fileChanged called for file: {}", filePath);

		try {
			URI uri = new File(filePath).toURI();
			fileChangeNotifier.notifyFileChanged(uri.toASCIIString());
		} catch (Exception e) {
			logger.error("Failed to notify file changed for: " + filePath, e);
		}
	}

	@Tool(description = "Notifies the MCP Server that a file has been deleted on disk so it can update its internal index and diagnostics.")
	public void fileDeleted(
			@ToolParam(description = "Absolute path to the file") String filePath) {

		if (filePath == null || filePath.startsWith("$")) {
			logger.warn("fileDeleted called but could not extract file path. filePath={}", filePath);
			return;
		}

		if (server.getClient() != null) {
			logger.info("LSP client connected, skipping fileDeleted MCP notification for: {}", filePath);
			return;
		}

		logger.info("MCP fileDeleted called for file: {}", filePath);

		try {
			URI uri = new File(filePath).toURI();
			fileChangeNotifier.notifyFileDeleted(uri.toASCIIString());
		} catch (Exception e) {
			logger.error("Failed to notify file deleted for: " + filePath, e);
		}
	}

	@Tool(description = "Notifies the MCP Server to refresh the entire workspace (e.g., after a git checkout or pull).")
	public void refreshWorkspace() {
		logger.info("MCP refreshWorkspace called");

		if (server.getClient() != null) {
			logger.info("LSP client connected, skipping refreshWorkspace MCP notification.");
			return;
		}

		projectChangeNotifier.ifPresent(notifier -> {
			try {
				notifier.notifyProjectsChanged();
			} catch (Exception e) {
				logger.error("Failed to notify workspace refresh", e);
			}
		});
	}

}
