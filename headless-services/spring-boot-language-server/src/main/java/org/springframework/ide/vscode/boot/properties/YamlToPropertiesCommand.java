/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ChangeAnnotation;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.LazyTextDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.events.CommentEvent;
import org.yaml.snakeyaml.events.Event;

import com.google.gson.JsonElement;

class YamlToPropertiesCommand {
	
	private static final String LABEL = "Convert .yaml to .properties";

	static final String CMD_YAML_TO_PROPS = "sts/boot/yaml-to-props";
	
	private final SimpleLanguageServer server;
	
	YamlToPropertiesCommand(SimpleLanguageServer server) {
		this.server = server;
		server.onCommand(CMD_YAML_TO_PROPS, params -> execute(params.getArguments()));
	}
	
	private CompletableFuture<ShowDocumentResult> execute(List<Object> arguments) {
		String yamlUri = arguments.get(0) instanceof JsonElement ? ((JsonElement) arguments.get(0)).getAsString() : (String) arguments.get(0);
		String propsUri = arguments.get(1) instanceof JsonElement ? ((JsonElement) arguments.get(1)).getAsString() : (String) arguments.get(1);
		Boolean replace = arguments.get(2) instanceof JsonElement ? ((JsonElement) arguments.get(2)).getAsBoolean() : (Boolean) arguments.get(2);
		return CompletableFuture.supplyAsync(() -> {
			try {
				return createWorkspaceEdit(yamlUri, propsUri, replace);
			} catch (IOException | BadLocationException e) {
				throw new CompletionException(e);
			}
		})
		.thenCompose(we -> server.getClient().applyEdit(new ApplyWorkspaceEditParams(we, LABEL)))
		.thenCompose(res -> res.isApplied() ? server.getClient().showDocument(new ShowDocumentParams(propsUri)) : CompletableFuture.completedFuture(new ShowDocumentResult(false)));
	}

	private WorkspaceEdit createWorkspaceEdit(String yamlUri, String propsUri, boolean replace) throws IOException, BadLocationException {
		Path propsFile = Paths.get(URI.create(propsUri));
		if (Files.exists(propsFile)) {
			throw new IOException("File %s already exists!".formatted(propsFile.toString()));
		}
		
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		TextDocument doc = getDocument(server, yamlUri, LanguageId.BOOT_PROPERTIES_YAML);
		String yamlContent = doc.get();
			
		if (hasComments(yamlContent)) {
			warnings.add("The yaml file had comments which are lost in the refactoring!");
		}
			
		StringBuilder propsContent = new StringBuilder();
		for (Object d : new Yaml().loadAll(new StringReader(yamlContent))) {
			if (d instanceof Map) {
				// Add doc divider if not empty
				@SuppressWarnings("unchecked")
				Map<String, ?> o = (Map<String, ?>) d;
				YamlToPropertiesConverter converter = new YamlToPropertiesConverter(o);
				Properties props = converter.getProperties();
				StringWriter write = new StringWriter();
				props.store(write, null);
				if (!propsContent.isEmpty()) {
					propsContent.append("#---\n");
				} else {
					addReportHeaderComment(propsContent, errors, warnings);
				}
				// Skip over the date header. Comments are not present but date header is.
				if (write.getBuffer().charAt(0) == '#') {
					int idx = write.getBuffer().indexOf("\n");
					propsContent.append(idx >= 0 && idx < write.getBuffer().length() ? write.getBuffer().substring(idx + 1) : write.getBuffer().toString());
				} else {
					propsContent.append(write.getBuffer().toString());
				}
			} else if (d == null) {
				if (!propsContent.isEmpty()) {
					propsContent.append("#---\n");
				}
			}
		}
		
		return replace ? createReplaceFileWorkspaceEdit(yamlUri, propsUri, doc, propsContent.toString()) : createNewFileWorkspaceEdit(propsUri, propsContent.toString());
	}

	
	private static boolean hasComments(String yamlContent) {
		LoaderOptions loaderOptions = new LoaderOptions();
		loaderOptions.setProcessComments(true);
		for (Event e : new Yaml(loaderOptions).parse(new StringReader(yamlContent))) {
			if (e instanceof CommentEvent ce) {
				if (ce.getCommentType() == CommentType.BLANK_LINE) {
					// document separator
				} else {
					return true;
				}
			}
		}
		return false;
	}
	
	static WorkspaceEdit createReplaceFileWorkspaceEdit(String sourceUri, String targetUri, TextDocument oldDoc, String newContent) throws BadLocationException {
		String changeAnnotationId = UUID.randomUUID().toString();
		ChangeAnnotation changeAnnotation = new ChangeAnnotation(LABEL);
		changeAnnotation.setNeedsConfirmation(true);
		
		RenameFile renameFile = new RenameFile(sourceUri, targetUri);
		renameFile.setAnnotationId(changeAnnotationId);
		WorkspaceEdit we = new WorkspaceEdit(List.of(
				Either.forLeft(new TextDocumentEdit(
						new VersionedTextDocumentIdentifier(sourceUri, oldDoc.getVersion()),
						List.of(Either.forLeft(new AnnotatedTextEdit(oldDoc.toRange(0, oldDoc.getLength()), newContent, changeAnnotationId)))
				)),
				Either.forRight(renameFile)
		));
		we.setChangeAnnotations(Map.of(changeAnnotationId, changeAnnotation));
		return we;
	}
	
	static WorkspaceEdit createNewFileWorkspaceEdit(String uri, String content) {
		String changeAnnotationId = UUID.randomUUID().toString();
		ChangeAnnotation changeAnnotation = new ChangeAnnotation(LABEL);
		changeAnnotation.setNeedsConfirmation(false); // VSCode refactor preview errors out showing diff for newly added file
		
		CreateFile createFile = new CreateFile(uri);
		createFile.setAnnotationId(changeAnnotationId);
		WorkspaceEdit we = new WorkspaceEdit(List.of(
				Either.forRight(createFile),
				Either.forLeft(new TextDocumentEdit(
						new VersionedTextDocumentIdentifier(uri, null),
						List.of(Either.forLeft(new AnnotatedTextEdit(new Range(new Position(0,0), new Position(0,0)), content, changeAnnotationId)))
				))
		));
		we.setChangeAnnotations(Map.of(changeAnnotationId, changeAnnotation));
		return we;
	}
	
	static TextDocument getDocument(SimpleLanguageServer server, String uri, LanguageId language) {
		TextDocument doc = server.getTextDocumentService().getLatestSnapshot(uri);
		return doc == null ? new LazyTextDocument(uri, language) : doc;
	}
	
	static void addReportHeaderComment(StringBuilder content, List<String> errors, List<String> warnings) {
		if (!errors.isEmpty() || !warnings.isEmpty()) {
			content.append("# Conversion to YAML from Properties formar report\n");
			if (!errors.isEmpty()) {
				content.append("# Errors:\n");
				for (String e : errors) {
					content.append("# - %s\n".formatted(e));
				}
			}
			if (!warnings.isEmpty()) {
				content.append("# Warnings:\n");
				for (String w : warnings) {
					content.append("# - %s\n".formatted(w));
				}
			}
		}
	}

}
