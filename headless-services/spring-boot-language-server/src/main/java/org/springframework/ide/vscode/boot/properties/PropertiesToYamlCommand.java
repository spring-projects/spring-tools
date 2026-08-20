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
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.LazyTextDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.JsonElement;
import com.google.gson.internal.LinkedTreeMap;

public class PropertiesToYamlCommand {

	static final String CMD_PROPS_TO_YAML = "sts/boot/props-to-yaml";
	
	private static final Pattern COMMENT = Pattern.compile("(?m)^\\s*(\\#|\\!)");
	
	private final SimpleLanguageServer server;
	
	PropertiesToYamlCommand(SimpleLanguageServer server) {
		this.server = server;
		server.onCommand(CMD_PROPS_TO_YAML, params -> execute(params.getArguments()));
	}

	private CompletableFuture<ShowDocumentResult> execute(List<Object> arguments) {
		if (arguments == null || arguments.size() < 3) {
			return CompletableFuture
					.failedFuture(new IllegalArgumentException("Command requires 3 arguments: propsUri, yamlUri, replace"));
		}
		String propsUri = arguments.get(0) instanceof JsonElement ? ((JsonElement) arguments.get(0)).getAsString() : (String) arguments.get(0);
		String yamlUri = arguments.get(1) instanceof JsonElement ? ((JsonElement) arguments.get(1)).getAsString() : (String) arguments.get(1);
		final boolean doReplace;
		Object replaceArg = arguments.get(2);
		if (replaceArg == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Argument 'replace' must not be null"));
		} else if (replaceArg instanceof JsonElement) {
			doReplace = ((JsonElement) replaceArg).getAsBoolean();
		} else if (replaceArg instanceof Boolean) {
			doReplace = (Boolean) replaceArg;
		} else {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Argument 'replace' must be a boolean"));
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				return createWorkspaceEdit(propsUri, yamlUri, doReplace);
			} catch (IOException | BadLocationException e) {
				throw new CompletionException(e);
			}
		})
		.thenCompose(we -> server.getClient().applyEdit(new ApplyWorkspaceEditParams(we, "Convert .properties to .yaml")))
		.thenCompose(res -> res.isApplied() ? server.getClient().showDocument(new ShowDocumentParams(yamlUri)) : CompletableFuture.completedFuture(new ShowDocumentResult(false)));
	}

	private WorkspaceEdit createWorkspaceEdit(String propsUri, String yamlUri, Boolean replace) throws IOException, BadLocationException {
		Path yamlFile = Paths.get(URI.create(yamlUri));
		if (Files.exists(yamlFile)) {
			throw new IOException("File %s already exists!".formatted(yamlFile.toString()));
		}
		
		TextDocument doc = getDocument(propsUri, LanguageId.BOOT_PROPERTIES);
		String propsContent = doc.get();
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
			
		if (hasComments(propsContent)) {
			warnings.add("The yaml file had comments which are lost in the refactoring!");
		}
		
		Multimap<String, String> properties = load(new StringReader(propsContent));
		PropertiesToYamlConverter converter = new PropertiesToYamlConverter(properties);
		
		StringBuilder yamlContent = new StringBuilder();
		errors.addAll(converter.getErrors());
		warnings.addAll(converter.getWarnings());
		YamlToPropertiesCommand.addReportHeaderComment(yamlContent, errors, warnings);
		yamlContent.append(converter.getYaml());
		
		return replace ? YamlToPropertiesCommand.createReplaceFileWorkspaceEdit(propsUri, yamlUri, doc, yamlContent.toString()) : YamlToPropertiesCommand.createNewFileWorkspaceEdit(yamlUri, yamlContent.toString());
	}
	
	private Multimap<String, String> load(Reader content) throws IOException {
		Multimap<String, String> map = Multimaps.newMultimap(
				new LinkedTreeMap<String, Collection<String>>(), 
				LinkedHashSet::new
		);
		Properties loader = new Properties() {
			private static final long serialVersionUID = 1L;
			public synchronized Object put(Object key, Object value) {
				map.put((String)key, (String)value);
				return super.put(key, value);
			}
		};
		loader.load(content);
		return map;
	}
	
	private static boolean hasComments(String propsContent) {
		return COMMENT.matcher(propsContent).find();
	}
	
	private TextDocument getDocument(String uri, LanguageId language) {
		TextDocument doc = server.getTextDocumentService().getLatestSnapshot(uri);
		return doc == null ? new LazyTextDocument(uri, language) : doc;
	}

}
