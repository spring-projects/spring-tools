package org.springframework.ide.vscode.boot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools for notifying the language server about file changes on disk.
 * This bridges the gap when running in MCP-only mode without full LSP file watching.
 */
@Component
public class FileChangesMcpTools {

	private static final Logger logger = LoggerFactory.getLogger(FileChangesMcpTools.class);

	// TODO: Inject necessary services for indexing/validation, e.g., SpringSymbolIndex, JavaProjectFinder, etc.
	public FileChangesMcpTools() {
	}

	@Tool(description = "Notifies the Language Server that a file has changed on disk so it can update its internal index and diagnostics.")
	public void notifyFileChanged(
			@ToolParam(description = "Absolute path to the file") String filePath,
			@ToolParam(description = "Fallback path to the file") String fallbackPath,
			@ToolParam(description = "Type of change: Write, StrReplace, Delete, etc.") String changeType) {

		// Claude Code tools sometimes use 'path' and sometimes use 'file_path'. 
		// We pass both from the hook and use whichever is populated.
		String actualPath = (filePath != null && !filePath.isEmpty() && !filePath.startsWith("$")) ? filePath : fallbackPath;
		
		if (actualPath == null || actualPath.startsWith("$")) {
			logger.warn("notifyFileChanged called but could not extract file path. filePath={}, fallbackPath={}", filePath, fallbackPath);
			return;
		}

		logger.info("MCP notifyFileChanged called for file: {} with changeType: {}", actualPath, changeType);

		// TODO: Implement the logic to trigger re-indexing or re-validation for this specific file URI.
		// This should act exactly as if we received a file change/create/delete via LSP.
		// 
		// Example pseudo-code:
		// URI uri = new File(actualPath).toURI();
		// if ("Delete".equals(changeType)) {
		//     // handle file deletion
		// } else {
		//     // handle file creation/modification
		// }
	}

}
