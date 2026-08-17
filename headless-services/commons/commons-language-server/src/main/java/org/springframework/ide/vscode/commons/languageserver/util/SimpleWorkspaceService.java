/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeLensWorkspaceCapabilities;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.SemanticTokensWorkspaceCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.AsyncRunner;
import org.springframework.ide.vscode.commons.util.FileObserver;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;

public class SimpleWorkspaceService implements WorkspaceService {

	private static Logger log = LoggerFactory.getLogger(SimpleWorkspaceService.class);

	private final ExecutorService messageWorkerThreadPool;

	private Set<WorkspaceFolder> workspaceRoots = new HashSet<>();

	private final SettingsStore settingsStore = new SettingsStore();
	private ExecuteCommandHandler executeCommandHandler;
	private WorkspaceSymbolHandler workspaceSymbolHandler;
	private SimpleServerFileObserver fileObserver;
	private AsyncRunner asyncRunner;
	
	private ListenerList<DidChangeWorkspaceFoldersParams> workspaceFolderListeners = new ListenerList<>();

	WorkspaceClientCapabilities clientCapabilities;

	public SimpleWorkspaceService(SimpleLanguageServer server) {
		this.fileObserver = new SimpleServerFileObserver(server);
		this.messageWorkerThreadPool = Executors.newCachedThreadPool();
		this.asyncRunner = server.getAsync();
	}

	@Override
	public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
		log.info("request for workspace symbols arrived: " + params.getQuery());
		
		return CompletableFutures.computeAsync(messageWorkerThreadPool, cancelToken -> {
			WorkspaceSymbolHandler workspaceSymbolHandler = this.workspaceSymbolHandler;

			if (workspaceSymbolHandler == null) {
				return Either.forRight(ImmutableList.of());
			}
			else {
				cancelToken.checkCanceled();
				
				List<? extends WorkspaceSymbol> symbols = workspaceSymbolHandler.handle(params);
				log.info("workspace symbol computation done: " + symbols.size());
				return Either.forRight(symbols == null ? ImmutableList.<WorkspaceSymbol>of() : symbols);
			}
		});
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		asyncRunner.execute(() -> settingsStore.update(new Settings((JsonElement) params.getSettings())));
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		asyncRunner.execute(() -> {
			try {
				Map<FileChangeType, List<FileEvent>> collect =
						params.getChanges().stream().filter(event -> event.getUri() != null).collect(Collectors.groupingBy(FileEvent::getType));
				
				for (FileChangeType type : collect.keySet()) {
					String[] docURIs = collect.get(type).stream().map(event -> event.getUri()).toArray(String[]::new);
		
					switch (type) {
					case Created:
						log.info("file created event: " + (docURIs.length <= 5 ? Arrays.toString(docURIs) : docURIs.length));
						fileObserver.notifyFilesCreated(docURIs);
						break;
					case Changed:
						log.info("file changed event: " + (docURIs.length <= 5 ? Arrays.toString(docURIs) : docURIs.length));
						fileObserver.notifyFilesChanged(docURIs);
						break;
					case Deleted:
						log.info("file deleted event: " + (docURIs.length <= 5 ? Arrays.toString(docURIs) : docURIs.length));
						fileObserver.notifyFilesDeleted(docURIs);
						break;
					default:
						log.warn("Uknown file change type '" + type + "' for files: " + docURIs);
						break;
					}
				}
			} catch (Throwable t) {
				log.warn("problem occurred while dispatching file event", t);
			}
		});
	}

	@Override
	public synchronized void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		WorkspaceFoldersChangeEvent evt = params.getEvent();
		boolean changed = false;
		for (WorkspaceFolder r : evt.getAdded()) {
			workspaceRoots.add(r);
			changed = true;
		}
		for (WorkspaceFolder r : evt.getRemoved()) {
			workspaceRoots.remove(r);
			changed = true;
		}
		if (changed) {
			asyncRunner.execute(() -> workspaceFolderListeners.fire(params));
		}
	}

	public void onDidChangeWorkspaceFolders(Consumer<DidChangeWorkspaceFoldersParams> l) {
		workspaceFolderListeners.add(l);
	}

	@Override
	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
		if (this.executeCommandHandler!=null) {
			return this.executeCommandHandler.handle(params);
		}
		throw new UnsupportedOperationException();
	}

	public void onDidChangeConfiguraton(Consumer<Settings> l) {
		settingsStore.onDidChange(l);
	}

	/**
	 * The settings this service feeds from {@code workspace/didChangeConfiguration}. Prefer
	 * depending on the store directly over depending on this service, so that settings consumers do
	 * not need a running language server session.
	 */
	public SettingsStore getSettingsStore() {
		return settingsStore;
	}

	public void onExecuteCommand(ExecuteCommandHandler handler) {
		Assert.isNull("A executeCommandHandler is already set, multiple handlers not supported yet", this.executeCommandHandler);
		this.executeCommandHandler = handler;
	}

	public synchronized void onWorkspaceSymbol(WorkspaceSymbolHandler h) {
		Assert.isNull("A WorkspaceSymbolHandler is already set, multiple handlers not supported yet", workspaceSymbolHandler);
		this.workspaceSymbolHandler = h;
	}

	public boolean hasWorkspaceSymbolHandler() {
		return this.workspaceSymbolHandler != null;
	}

	public FileObserver getFileObserver() {
		return fileObserver;
	}

	public void dispose() {
		fileObserver.dispose();
	}

	public Collection<WorkspaceFolder> getWorkspaceRoots() {
		return ImmutableList.copyOf(workspaceRoots);
	}

	public synchronized void setWorkspaceFolders(List<WorkspaceFolder> workspaceFolders) {
		workspaceRoots = new HashSet<>();
		workspaceRoots.addAll(workspaceFolders);
		log.debug("workspaceFolders = {}", workspaceFolders);
	}
	
	public boolean supportsSemanticTokensRefresh() {
		if (clientCapabilities != null) {
			SemanticTokensWorkspaceCapabilities semanticTokensCapability = clientCapabilities.getSemanticTokens();
			if (semanticTokensCapability != null) {
				return semanticTokensCapability.getRefreshSupport() != null && semanticTokensCapability.getRefreshSupport().booleanValue();
			}
		}
		return false;
	}

	public boolean supportsCodeLensRefresh() {
		if (clientCapabilities != null) {
			CodeLensWorkspaceCapabilities codeLensCapability = clientCapabilities.getCodeLens();
			if (codeLensCapability != null) {
				return codeLensCapability.getRefreshSupport() != null && codeLensCapability.getRefreshSupport().booleanValue();
			}
		}
		return false;
	}

}
