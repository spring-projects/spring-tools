/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.quickfix;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.ResourceOperationKind;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.metadata.MetadataManipulator;
import org.springframework.ide.vscode.boot.metadata.MetadataManipulator.ContentStore;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixEdit;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.util.IOUtil;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

/**
 * Common quick fixes for YAML and properties
 *
 * @author Alex Boyko
 *
 */
public class CommonQuickfixes {

	public static final String MISSING_PROPERTY_APP_QF_ID = "MISSING_PROPERTY_APP";

	private static final Logger log = LoggerFactory.getLogger(CommonQuickfixes.class);

	private static final Path METADATA_PATH = Paths.get("META-INF", "additional-spring-configuration-metadata.json");

	private static final QuickfixEdit NULL_FIX = new QuickfixEdit(
			new WorkspaceEdit(ImmutableMap.of()),
			null
	);

	public final QuickfixType MISSING_PROPERTY;

	private final Gson gson = new Gson();

	private static class SimpleContentStore implements ContentStore {

		private String content;

		SimpleContentStore(String content) {
			this.content = content;
		}

		@Override
		public String getContents() throws Exception {
			return content;
		}

		@Override
		public void setContents(String content) throws Exception {
			this.content = content;
		}

	}

	public CommonQuickfixes(QuickfixRegistry r, JavaProjectFinder projectFinder, ClientCapabilities clientCapabilities) {
		if (clientCapabilities == null) {
			throw new IllegalStateException("Client Capabilities have not been received!");
		}
		if (clientCapabilities.getWorkspace().getWorkspaceEdit() != null && clientCapabilities.getWorkspace().getWorkspaceEdit().getResourceOperations() != null && clientCapabilities.getWorkspace().getWorkspaceEdit().getResourceOperations().contains(ResourceOperationKind.Create)
				&& Boolean.TRUE.equals(clientCapabilities.getWorkspace().getWorkspaceEdit().getDocumentChanges())) {
			MISSING_PROPERTY = r.register(MISSING_PROPERTY_APP_QF_ID, (Object _params) -> Mono.fromSupplier(() -> {
				MissingPropertyData params = gson.fromJson((JsonElement)_params, MissingPropertyData.class);
				try {
					Optional<IJavaProject> p = projectFinder.find(params.getDoc());
					if (p.isPresent()) {
						IJavaProject project = p.get();
						List<File> sourceFolders = IClasspathUtil.getSourceFolders(project.getClasspath()).collect(Collectors.toList());
						if (!sourceFolders.isEmpty()) {
							File preferredSourceFolder = getPreferredMetadataSourceFolder(sourceFolders);
							WorkspaceEdit we = new WorkspaceEdit(new ArrayList<Either<TextDocumentEdit, ResourceOperation>>());
							Path metadataFilePath = sourceFolders.stream().map(f -> f.toPath()).map(path -> path.resolve(METADATA_PATH)).filter(path -> Files.exists(path)).findFirst().orElse(null);
							if (metadataFilePath == null) {
								metadataFilePath = preferredSourceFolder.toPath().resolve(METADATA_PATH);
								we.getDocumentChanges().add(Either.forRight(new CreateFile(metadataFilePath.toUri().toASCIIString())));
							}
							if (metadataFilePath != null) {
								String content = Files.exists(metadataFilePath) ? IOUtil.toString(Files.newInputStream(metadataFilePath)) : "";
								MetadataManipulator metadata = new MetadataManipulator(new SimpleContentStore(content));
								if (!metadata.isReliable()) {
									log.error("Failed to add metadata!",
											"'" + metadataFilePath + "' does not appear to contain valid JSON!\n");
								} else {
									metadata.addDefaultInfo(params.getProperty());
									TextDocumentEdit edit = new TextDocumentEdit();
									edit.setTextDocument(new VersionedTextDocumentIdentifier(metadataFilePath.toUri().toASCIIString(), null));
									TextEdit textEdit = new TextEdit();
									textEdit.setNewText(metadata.getTextContent());
									TextDocument doc = new TextDocument(metadataFilePath.toUri().toASCIIString(), null);
									doc.setText(content);
									textEdit.setRange(doc.toRange(new Region(0, content.length())));
									edit.setEdits(ImmutableList.of(Either.forLeft(textEdit)));
									we.getDocumentChanges().add(Either.forLeft(edit));
								}

							}
							return new QuickfixEdit(we, null);
						}
					}
				} catch (Exception e) {
					log.error("", e);
				}
				return NULL_FIX;
			}));
		} else {
			MISSING_PROPERTY = null;
		}
	}

	private File getPreferredMetadataSourceFolder(List<File> sourceFolders) {
		Path mainResources = Paths.get("main", "resources");
		for (File file : sourceFolders) {
			if (file.toPath().endsWith(mainResources)) {
				return file;
			}
		}
		return sourceFolders.get(0);
	}

}
