/*******************************************************************************
 * Copyright (c) 2016, 2024 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.util;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams;
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.NotebookDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.ide.vscode.commons.languageserver.DiagnosticService;
import org.springframework.ide.vscode.commons.languageserver.MessageService;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.Sts4LanguageServer;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter.CompletionFilter;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter.LazyCompletionResolver;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties.ReconcileStrategy;
import org.springframework.ide.vscode.commons.languageserver.java.ls.ClasspathListener;
import org.springframework.ide.vscode.commons.languageserver.java.ls.ClasspathListenerManager;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixEdit;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixResolveParams;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeansParams;
import org.springframework.ide.vscode.commons.protocol.spring.MatchingBeansParams;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndex;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexLanguageServer;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.AsyncRunner;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.text.LazyTextDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Scaffolding to implement a LanguageServer. Provides a complete 'language server' implementation.
 * with apis to register various callbacks so that language server implementor
 * can attach their own 'handlers' for whatever the functionality they want to implement.
 */
public final class SimpleLanguageServer implements Sts4LanguageServer, SpringIndexLanguageServer, LanguageClientAware, ServiceNotificationsClient, SimpleLanguageServerWrapper {

	private static Logger log = LoggerFactory.getLogger(SimpleLanguageServer.class);

	private static final String WORKSPACE_FOLDERS_CAPABILITY_ID = UUID.randomUUID().toString();
	public static final String WORKSPACE_FOLDERS_CAPABILITY_NAME = "workspace/didChangeWorkspaceFolders";

	private static final Scheduler RECONCILER_SCHEDULER = Schedulers.newSingle("Reconciler");

	private static final int FORCED_EXIT_CODE = 1;
	private static final int FORCED_EXIT_DELAY_IN_SECONDS = 3;

	public final String EXTENSION_ID;
	public final String CODE_ACTION_COMMAND_ID;
	public final String COMMAND_LIST_COMMAND_ID;

	public final LazyCompletionResolver completionResolver = createCompletionResolver();

	private SimpleTextDocumentService tds;
	private NotebookDocumentService nts;
	private SimpleWorkspaceService workspace;
	private STS4LanguageClient client;
	private final LanguageServerProperties props;
	
	private Integer parentProcessId;

	private ProgressService progressService = new ProgressService()  {
		
		private ConcurrentHashMap<String, Boolean> activeTaskIDs = new ConcurrentHashMap<>();
		
		public void progressBegin(String taskId, WorkDoneProgressBegin report) {
			STS4LanguageClient client = SimpleLanguageServer.this.client;
			if (client != null) {
				boolean isNew = activeTaskIDs.put(taskId, true) == null;
				if (!isNew) {
					log.error("Progress for task id '{}' already exists", taskId);
				}
				WorkDoneProgressCreateParams params = new WorkDoneProgressCreateParams();
				params.setToken(taskId);
				client.createProgress(params).thenAcceptAsync((p) -> {
					ProgressParams progressParams = new ProgressParams();
					progressParams.setToken(taskId);
					progressParams.setValue(Either.forLeft(report));
					client.notifyProgress(progressParams);
				});
			}
		}
		
		public void progressEvent(String taskId, WorkDoneProgressReport report) {
			STS4LanguageClient client = SimpleLanguageServer.this.client;
			if (client != null) {
				if (!activeTaskIDs.containsKey(taskId)) {
					log.error("Progress for task id '{}' does NOT exist!", taskId);
					return;
				}
				ProgressParams progressParams = new ProgressParams();
				progressParams.setToken(taskId);
				progressParams.setValue(Either.forLeft(report));
				client.notifyProgress(progressParams);
			}
		}

		@Override
		public void progressDone(String taskId) {
			STS4LanguageClient client = SimpleLanguageServer.this.client;
			if (client != null && activeTaskIDs.remove(taskId) != null) {
				ProgressParams progressParams = new ProgressParams();
				progressParams.setToken(taskId);
				WorkDoneProgressEnd report = new WorkDoneProgressEnd();
				progressParams.setValue(Either.forLeft(report));
				client.notifyProgress(progressParams);
			}
		}
	};
	
	private MessageService messageService = new MessageService() {
		
		@Override
		public void warning(String message) {
			message(MessageType.Warning, message);
		}
		
		@Override
		public void log(String message) {
			message(MessageType.Log, message);
		}
		
		@Override
		public void info(String message) {
			message(MessageType.Info, message);
		}
		
		@Override
		public void error(String message) {
			message(MessageType.Error, message);
		}
		
		private void message(MessageType messageType, String message) {
			getClient().showMessage(new MessageParams(messageType, message));
		}
	};

	private DiagnosticService diagnosticService = message -> onError(null, message);

	private CompletableFuture<Void> busyReconcile = CompletableFuture.completedFuture(null);

	private QuickfixRegistry quickfixRegistry;

	private LanguageServerTestListener testListener;

	private boolean hasCompletionSnippetSupport;
	private boolean hasExecuteCommandSupport;
	private boolean hasFileWatcherRegistrationSupport;
	private boolean hasHierarchicalDocumentSymbolSupport;

	private Consumer<InitializeParams> initializeHandler;
	private CompletableFuture<Void> initialized = new CompletableFuture<Void>();
	private CompletableFuture<ClientCapabilities> clientCapabilities = new CompletableFuture<>();

	private Runnable shutdownHandler;

	private Map<String, ExecuteCommandHandler> commands = new HashMap<>();

	private AsyncRunner async = new AsyncRunner(Schedulers.newSingle(runable -> {
		Thread t = new Thread(runable, "Simple-Language-Server main thread");
		t.setDaemon(true);
		return t;
	}));
	private ClasspathListenerManager classpathListenerManager;

	private Optional<CompletionFilter> completionFilter = Optional.empty();

	private String completionTriggerCharacters = null;

	final private ApplicationContext appContext;

	@Override
	public void connect(LanguageClient _client) {
		this.client = (STS4LanguageClient) _client;
	}

	public VscodeCompletionEngineAdapter createCompletionEngineAdapter(ICompletionEngine engine) {
		return new VscodeCompletionEngineAdapter(this, engine, completionResolver, completionFilter);
	}

	protected LazyCompletionResolver createCompletionResolver() {
		if (!Boolean.getBoolean("lsp.lazy.completions.disable")) {
			return new LazyCompletionResolver();
		}
		return null;
	}

	public synchronized QuickfixRegistry getQuickfixRegistry() {
		if (quickfixRegistry==null) {
			quickfixRegistry = new QuickfixRegistry();
		}
		return quickfixRegistry;
	}

	public SnippetBuilder createSnippetBuilder() {
		if (hasCompletionSnippetSupport) {
			return new SnippetBuilder();
		} else {
			return SnippetBuilder.gimped();
		}
	}

	public SimpleLanguageServer(String extensionId, ApplicationContext appContext, LanguageServerProperties props) {
		this.appContext = appContext;
		this.props = props;
		Assert.isNotNull(extensionId);
		this.EXTENSION_ID = extensionId;
		this.CODE_ACTION_COMMAND_ID = "sts."+EXTENSION_ID+".codeAction";
		this.COMMAND_LIST_COMMAND_ID = "sts." + EXTENSION_ID + ".commandList";
	}

	protected CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
		ExecuteCommandHandler handler = commands.get(params.getCommand());
		if (handler!=null) {
			return handler.handle(params);
		}
		if (CODE_ACTION_COMMAND_ID.equals(params.getCommand())) {
			Assert.isLegal(params.getArguments().size()==2);
			QuickfixResolveParams quickfixParams = new QuickfixResolveParams(
					params.getArguments().get(0) instanceof JsonPrimitive ? ((JsonPrimitive)params.getArguments().get(0)).getAsString() : params.getArguments().get(0).toString() , params.getArguments().get(1)
			);
			return quickfixResolve(quickfixParams)
			.flatMap((QuickfixEdit edit) -> {
				Mono<ApplyWorkspaceEditResponse> applyEdit = Mono.fromFuture(client.applyEdit(new ApplyWorkspaceEditParams(edit.workspaceEdit, quickfixParams.getType())));
				return applyEdit.flatMap(r -> {
					if (r.isApplied()) {
						if (edit.cursorMovement!=null) {
							return Mono.fromFuture(client.moveCursor(edit.cursorMovement));
						}
					}
					return Mono.just(r);
				});
			})
			.toFuture();
		} else if (COMMAND_LIST_COMMAND_ID.equals(params.getCommand())) {
			Gson gson = new Gson();
			CompletableFuture<Object> execution = CompletableFuture.completedFuture(null);
			for (Object json : params.getArguments()) {
				Command cmd = json instanceof Command ? (Command) json : gson.fromJson(json instanceof JsonElement ? (JsonElement) json : gson.toJsonTree(json), Command.class);
				execution = execution.thenCompose(r -> getWorkspaceService().executeCommand(new ExecuteCommandParams(cmd.getCommand(), cmd.getArguments())));
			}
			return execution;
		}
		log.warn("Unknown command ignored: "+params.getCommand());
		return CompletableFuture.completedFuture(false);
	}

	public Mono<QuickfixEdit> quickfixResolve(QuickfixResolveParams params) {
		QuickfixRegistry quickfixes = getQuickfixRegistry();
		return quickfixes.handle(params);
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		log.info("Initializing");
		parentProcessId = params.getProcessId();
		clientCapabilities.complete(params.getCapabilities());

		// multi-root workspace handling
		List<WorkspaceFolder> workspaceFolders = getWorkspaceFolders(params);
		if (!workspaceFolders.isEmpty()) {
			this.getWorkspaceService().setWorkspaceFolders(workspaceFolders);
		}
		else {
			String rootUri = params.getRootUri();
			if (rootUri==null) {
				log.debug("workspaceRoot NOT SET");
			} else {
				List<WorkspaceFolder> singleRootFolder = new ArrayList<>();
				String name;
				try {
					name = Paths.get(new URI(rootUri).getPath()).getFileName().toString();
				} catch (Exception e) {
					name = "";
				}
				WorkspaceFolder folder = new WorkspaceFolder();
				folder.setName(name);
				folder.setUri(rootUri);
				singleRootFolder.add(folder);
				this.getWorkspaceService().setWorkspaceFolders(singleRootFolder);
			}
		}

		this.hasCompletionSnippetSupport = safeGet(false, () -> params.getCapabilities().getTextDocument().getCompletion().getCompletionItem().getSnippetSupport());
		this.hasExecuteCommandSupport = safeGet(false, () -> params.getCapabilities().getWorkspace().getExecuteCommand()!=null);
		this.hasFileWatcherRegistrationSupport = safeGet(false, () -> params.getCapabilities().getWorkspace().getDidChangeWatchedFiles().getDynamicRegistration());
		this.hasHierarchicalDocumentSymbolSupport = safeGet(false, () -> params.getCapabilities().getTextDocument().getDocumentSymbol().getHierarchicalDocumentSymbolSupport());
		log.debug("workspaceRoots = "+getWorkspaceService().getWorkspaceRoots());
		log.debug("hasCompletionSnippetSupport = "+hasCompletionSnippetSupport);
		log.debug("hasExecuteCommandSupport = "+hasExecuteCommandSupport);
		
		getTextDocumentService().clientCapabilities = params.getCapabilities().getTextDocument();
		getWorkspaceService().clientCapabilities = params.getCapabilities().getWorkspace();
		
		InitializeResult result = new InitializeResult();

		if (hasExecuteCommandSupport) {
			getWorkspaceService().onExecuteCommand(this::executeCommand);
		}

		ServerCapabilities cap = getServerCapabilities();
		if (appContext!=null) {
			Map<String, ServerCapabilityInitializer> extraCaps = appContext.getBeansOfType(ServerCapabilityInitializer.class);
			for (ServerCapabilityInitializer capIniter : extraCaps.values()) {
				capIniter.initialize(params, cap);
			}
		}
		result.setCapabilities(cap);
		Consumer<InitializeParams> ih = this.initializeHandler;
		if (ih!=null){
			ih.accept(params);
		}
		log.info("Returning server capabilities to client");
		log.debug("Capabilities: {}", result.getCapabilities());
		return CompletableFuture.completedFuture(result);
	}

	@Override
	public void initialized() {
	  async.withLog(log, () -> {
		Registration registration = new Registration(WORKSPACE_FOLDERS_CAPABILITY_ID, WORKSPACE_FOLDERS_CAPABILITY_NAME, null);
		RegistrationParams registrationParams = new RegistrationParams(Collections.singletonList(registration));
		getClient().registerCapability(registrationParams);
		this.initialized.complete(null); // triggers onInitialized handlers.
		log.info("Initialization completed after {} ms", ManagementFactory.getRuntimeMXBean().getUptime());
	  });
	}

	private List<WorkspaceFolder> getWorkspaceFolders(InitializeParams params) {
		List<WorkspaceFolder> initialFolders = new ArrayList<>();

		Object initOptions = params.getInitializationOptions();
		if (initOptions != null && initOptions instanceof JsonObject) {
			JsonObject initializationOptions = (JsonObject) initOptions;
			JsonElement folders = initializationOptions.get("workspaceFolders");
			if (folders != null && folders instanceof JsonArray) {
				JsonArray workspaceFolders = (JsonArray) folders;
				for (JsonElement object : workspaceFolders) {
					String folderUri = object.getAsString();
					String folderName = null;

					int folderNameStart = folderUri.lastIndexOf("/");
					if (folderNameStart > 0) {
						folderName = folderUri.substring(folderUri.lastIndexOf("/") + 1);
					}

					WorkspaceFolder folder = new WorkspaceFolder();
					folder.setName(folderName);
					folder.setUri(folderUri);

					initialFolders.add(folder);
				}
			}
		}

		return initialFolders;
	}

	/**
	 * Get some info safely. If there's any kind of exception, ignore it
	 * and retutn default value instead.
	 */
	public static <T> T safeGet(T deflt, Callable<T> getter) {
		try {
			T x = getter.call();
			if (x!=null) {
				return x;
			}
		} catch (Exception e) {
		}
		return deflt;
	}

	public Disposable onCommand(String id, ExecuteCommandHandler commandHandler) {
		synchronized (commands) {
			Assert.isLegal(!commands.containsKey(id), "Command '" + id + "' is already registered");
			commands.put(id, commandHandler);
		}
		return () -> {
			synchronized (commands) {
				commands.remove(id);
			}
		};
	}

	public void onError(String message, Throwable error) {
		LanguageClient cl = this.client;
		if (cl != null) {
			if (error instanceof ShowMessageException)
				client.showMessage(((ShowMessageException) error).message);
			else {
				log.error(message, error);

				MessageParams m = new MessageParams();

				m.setMessage(message);
				m.setType(MessageType.Error);
				client.showMessage(m);
			}
		}
	}

	protected final ServerCapabilities getServerCapabilities() {
		ServerCapabilities c = new ServerCapabilities();

		c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
		c.setHoverProvider(true);

		if (hasQuickFixes()) {
			CodeActionOptions codeActionOptions = new CodeActionOptions();
			codeActionOptions.setCodeActionKinds(List.of(CodeActionKind.QuickFix));
			codeActionOptions.setWorkDoneProgress(true);
			c.setCodeActionProvider(codeActionOptions);
		}
		if (hasDefinitionHandler()) {
			c.setDefinitionProvider(true);
		}
		if (hasImplementationHandler()) {
			c.setImplementationProvider(true);
		}
		if (hasReferencesHandler()) {
			c.setReferencesProvider(true);
		}
		if (hasDocumentSymbolHandler()) {
			c.setDocumentSymbolProvider(true);
		}
		if (hasDocumentHighlightHandler()) {
			c.setDocumentHighlightProvider(true);
		}
		if (hasCodeLensHandler()) {
			CodeLensOptions codeLensOptions = new CodeLensOptions();
			codeLensOptions.setResolveProvider(hasCodeLensResolveProvider());
			c.setCodeLensProvider(codeLensOptions );
		}
		if (hasInlayHintHandler()) {
			c.setInlayHintProvider(true);
		}
		if (hasExecuteCommandSupport && (
				hasQuickFixes() || 
				!commands.isEmpty()
		)) {
			List<String> supportedCommands = new ArrayList<>();
			if (hasQuickFixes()) {
				supportedCommands.add(CODE_ACTION_COMMAND_ID);
				supportedCommands.add(COMMAND_LIST_COMMAND_ID);
			}
			supportedCommands.addAll(commands.keySet());
			ExecuteCommandOptions executeCommandOptions = new ExecuteCommandOptions(supportedCommands);
			executeCommandOptions.setWorkDoneProgress(true);
			c.setExecuteCommandProvider(executeCommandOptions);
		}
		if (hasWorkspaceSymbolHandler()) {
			c.setWorkspaceSymbolProvider(true);
		}
		// TODO: Check if server supports all token types from the legend
		// Eclipse LSP4E client gives `null` semantic tokens client capability yet it works up to dynamic registration
		SemanticTokensCapabilities clientSemanticTokensCapability = getTextDocumentService().clientCapabilities.getSemanticTokens();
		if (clientSemanticTokensCapability == null || clientSemanticTokensCapability.getDynamicRegistration() == null || !clientSemanticTokensCapability.getDynamicRegistration().booleanValue()) {
			SemanticTokensWithRegistrationOptions semanticTokensCapability = getTextDocumentService().getSemanticTokensWithRegistrationOptions();
			c.setSemanticTokensProvider(semanticTokensCapability);
		}

		WorkspaceFoldersOptions workspaceFoldersOptions = new WorkspaceFoldersOptions();
		workspaceFoldersOptions.setSupported(true);
		workspaceFoldersOptions.setChangeNotifications(WORKSPACE_FOLDERS_CAPABILITY_ID);

		WorkspaceServerCapabilities workspaceServerCapabilities = new WorkspaceServerCapabilities();
		workspaceServerCapabilities.setWorkspaceFolders(workspaceFoldersOptions);

		c.setWorkspace(workspaceServerCapabilities);

		return c;
	}

	public final boolean hasLazyCompletionResolver() {
		return completionResolver!=null;
	}

	private boolean hasDocumentSymbolHandler() {
		return getTextDocumentService().hasDocumentSymbolHandler();
	}

	private boolean hasDocumentHighlightHandler() {
		return getTextDocumentService().hasDocumentHighlightHandler();
	}

	private boolean hasCodeLensHandler() {
		return getTextDocumentService().hasCodeLensHandler();
	}
	
	private boolean hasInlayHintHandler() {
		return getTextDocumentService().hasInlayHintHandler();
	}

	private boolean hasCodeLensResolveProvider() {
		return getTextDocumentService().hasCodeLensResolveProvider();
	}

	private boolean hasReferencesHandler() {
		return getTextDocumentService().hasReferencesHandler();
	}

	private boolean hasDefinitionHandler() {
		return getTextDocumentService().hasDefinitionHandler();
	}
	
	private boolean hasImplementationHandler() {
		return getTextDocumentService().hasImplementationHandler();
	}

	private boolean hasQuickFixes() {
		return quickfixRegistry!=null && quickfixRegistry.hasFixes();
	}

	private boolean hasWorkspaceSymbolHandler() {
		return getWorkspaceService().hasWorkspaceSymbolHandler();
	}
	
	@Override
	public void cancelProgress(WorkDoneProgressCancelParams params) {
		// TODO: Implement cancel progress message handling! 
	}

	@Override
	public void setTrace(SetTraceParams params) {
		// TODO: Implement set trace message for LS. Should be something about changing log level probably
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		log.info("shutdown: request arrived");
		
		// make sure the JVM gets the exit call after some time
		Executors.newSingleThreadScheduledExecutor().schedule(() -> {
			log.info("Forcing exit after 3 sec.");
			System.exit(FORCED_EXIT_CODE);
		}, FORCED_EXIT_DELAY_IN_SECONDS, TimeUnit.SECONDS);

		// proceed with regular shutdown activities
		try {
			Runnable h = shutdownHandler;
			if (h != null) {
				log.info("shutdown: call shutdown handler");
				h.run();
			}

			getWorkspaceService().dispose();
		}
		catch (Exception e) {
			log.info("problem calling shutdown handlers: ", e);
		}
			
		log.info("shutdown: complete");

		return CompletableFuture.completedFuture("OK");
	}

	@Override
	public void exit() {
		log.info("exit: notification received");
		System.exit(0);
	}

	public Collection<WorkspaceFolder> getWorkspaceRoots() {
		return getWorkspaceService().getWorkspaceRoots();
	}

	@Override
	public synchronized SimpleTextDocumentService getTextDocumentService() {
		if (tds == null) {
			tds = createTextDocumentService();
		}
		return tds;
	}

	@Override
	public NotebookDocumentService getNotebookDocumentService() {
		if (nts == null) {
			nts = createNotebookDocumentService();
		}
		return nts;
	}

	private NotebookDocumentService createNotebookDocumentService() {
		return new NotebookDocumentService() {
			
			@Override
			public void didSave(DidSaveNotebookDocumentParams params) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void didOpen(DidOpenNotebookDocumentParams params) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void didClose(DidCloseNotebookDocumentParams params) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public void didChange(DidChangeNotebookDocumentParams params) {
				throw new UnsupportedOperationException();
			}
		};
	}

	protected SimpleTextDocumentService createTextDocumentService() {
		return new SimpleTextDocumentService(this, props, appContext);
	}

	public SimpleWorkspaceService createWorkspaceService() {
		return new SimpleWorkspaceService(this);
	}

	@Override
	public synchronized SimpleWorkspaceService getWorkspaceService() {
		if (workspace == null) {
			workspace = createWorkspaceService();
		}
		return workspace;
	}

	/**
	 * Keeps track of reconcile requests that have been requested but not yet started.
	 * This is used to more efficiently deal with situation where many requests are fired
	 * in a burst. Rather than execute the same request repeatedly we can avoid queuing
	 * up more requests if the previous request has not yet been started.
	 */
	private ConcurrentHashMap<URI, Disposable> reconcileRequests = new ConcurrentHashMap<>();
	
	/**
	 * Aux structure to quickly check if doc is queued for reconcile. Handles case that `reconcileRequests` alone cannot:
	 * Doc URI came in not queued then go on and below add it `reconcileRequests`, but if the same URI comes in again and reaches the
	 * "isQueued" check while the first URI didn't reach the code adding to `reconcileRequests` then there is a race condition :-\
	 */
	private Set<URI> reconcileDocUris = Collections.synchronizedSet(new HashSet<>());

	/**
	 * Convenience method. Subclasses can call this to use a {@link IReconcileEngine} ported
	 * from old STS codebase to validate a given {@link TextDocument} and publish Diagnostics.
	 */
	public void validateWith(TextDocumentIdentifier docId, IReconcileEngine engine) {
		SimpleTextDocumentService documents = getTextDocumentService();

		if (documents.getLatestSnapshot(docId.getUri()) == null) {
			log.debug("Reconcile skipped due to document doesn't exist anymore {}", docId.getUri());
			return;
		}
		
		final URI uri = URI.create(docId.getUri());
		
		log.debug("Validate doc {}", uri);
		
		if (props.getReconcileStrategy() != ReconcileStrategy.DEBOUNCE || props.getReconcileDelay() == 0) {
			if (!reconcileDocUris.add(uri)) {
				log.debug("Reconcile skipped {}", uri);
				return;
			}
		}
		
		CompletableFuture<Void> currentSession = this.busyReconcile = new CompletableFuture<>();	
		
		Mono<Object> doReconcile = Mono.fromRunnable(() -> {
			reconcileRequests.remove(uri);
			reconcileDocUris.remove(uri);
			log.debug("Starting reconcile for {}", uri);

			TextDocument doc = documents.getLatestSnapshot(docId.getUri());
			
			if (doc == null) {
				// If document doesn't exist anymore it likely got closed in the meantime. Still needs validation.
				LanguageComputer languageDetector = appContext.getBean(LanguageComputer.class);
				if (languageDetector != null) {
					doc = new LazyTextDocument(uri.toASCIIString(), languageDetector.computeLanguage(uri));
				} else {
					// Cannot determine the language? Give up.
					log.warn("Cannot determine the language for document: " + uri);
					return;
				}
			}

			if (testListener != null) {
				testListener.reconcileStarted(docId.getUri(), doc.getVersion());
			}

			IProblemCollector problems = createProblemCollector(new AtomicReference<TextDocument>(doc), null);

			engine.reconcile(doc, problems);
		})
		.onErrorResume(error -> {
			log.error("", error);
			return Mono.empty();
		})
		.doFinally(ignore -> {
			currentSession.complete(null);
//			Log.debug("Reconciler DONE : "+this.busyReconcile.isDone());
		});
		
		// Use RECONCILER_SCHEDULER to avoid running in the same thread as lsp4j as it can result
		// in long "hangs" for slow reconcile providers
		if (props.getReconcileDelay() > 0 && props.getReconcileStrategy() != ReconcileStrategy.NONE) {
			Disposable old = reconcileRequests.put(uri, Mono.delay(Duration.ofMillis(props.getReconcileDelay()))
					.publishOn(RECONCILER_SCHEDULER)
					.then(doReconcile)
					.subscribe()
			);
			if (old != null) {
				old.dispose();
				log.debug("Re-scheduled requested reconcile for {}", uri);
				// Should reach this point only for the case of Debounce strategy
				Assert.isTrue(props.getReconcileStrategy() == ReconcileStrategy.DEBOUNCE);
			} else {
				log.debug("Requested reconcile for {}", uri);
			}
		} else {
			// No debounce or throttling case. Either delay is <= 0 or strategy is none. 
			Disposable old = reconcileRequests.put(uri, doReconcile.subscribeOn(RECONCILER_SCHEDULER).subscribe());
			if (old != null) {
				old.dispose();
				log.debug("Re-scheduled requested reconcile for {}", uri);
				// Should reach this point only for the case of Debounce strategy
				Assert.isTrue(props.getReconcileStrategy() == ReconcileStrategy.DEBOUNCE);
			} else {
				log.debug("Requested reconcile for {}", uri);
			}
		}
	}
	
	public IProblemCollector createProblemCollector(AtomicReference<TextDocument> docRef, BiConsumer<String, Diagnostic> diagnosticsCollector) {

		SimpleTextDocumentService documentsService = getTextDocumentService();
		
		return new IProblemCollector() {

			private LinkedHashSet<Diagnostic> diagnostics = new LinkedHashSet<>();
			private List<Quickfix<?>> quickfixes = new ArrayList<>();

			@Override
			public void endCollecting() {
				documentsService.setQuickfixes(docRef.get().getId(), quickfixes);
				documentsService.publishDiagnostics(docRef.get().getId(), diagnostics);
				log.debug("Reconcile done sent {} diagnostics", diagnostics.size());
			}

			@Override
			public void beginCollecting() {
				diagnostics.clear();
			}

			@Override
			public void checkPointCollecting() {
				// publish what has been collected so far
				documentsService.setQuickfixes(docRef.get().getId(), quickfixes);
				documentsService.publishDiagnostics(docRef.get().getId(), diagnostics);
				log.debug("Reconcile checkpoint sent {} diagnostics", diagnostics.size());
			}

			@Override
			public void accept(ReconcileProblem problem) {
				DiagnosticSeverityProvider severityProvider = getDiagnosticSeverityProvider();
				try {
					DiagnosticSeverity severity = severityProvider.getDiagnosticSeverity(problem);
					
					if (severity != null) {
						Diagnostic d = new Diagnostic();
						d.setCode(problem.getCode());
						d.setMessage(problem.getMessage());

						Range rng = docRef.get().toRange(problem.getOffset(), problem.getLength());
						d.setRange(rng);

						d.setSeverity(severity);
						d.setSource(getServer().EXTENSION_ID);
						d.setTags(problem.getType().getTags());
						
						List<QuickfixData<?>> fixes = problem.getQuickfixes();

						// Copy original diagnsotic without the data field to avoid stackoverflow is hashCode() method call
						Diagnostic refDiagnostic = new Diagnostic(d.getRange(), d.getMessage(), d.getSeverity(), d.getSource()); 
						if (CollectionUtil.hasElements(fixes)) {
							d.setData(fixes.stream().map(fix -> {
								CodeAction ca = new CodeAction();
								ca.setKind(CodeActionKind.QuickFix);
								ca.setTitle(fix.title);
								ca.setIsPreferred(fix.preferred);
								ca.setDiagnostics(List.of(refDiagnostic));
								ca.setCommand(new Command(
										fix.title,
										CODE_ACTION_COMMAND_ID,
										ImmutableList.of(fix.type.getId(), fix.params)
								));
								return ca;
							}).collect(Collectors.toList()));
						}
						diagnostics.add(d);
						
						if (diagnosticsCollector != null) {
							diagnosticsCollector.accept(docRef.get().getId().getUri(), d);
						}
						
					}
				} catch (BadLocationException e) {
					log.warn("Invalid reconcile problem ignored: " + docRef.get().getId().getUri() + " - problem position: " + problem.getOffset() + "/" + problem.getLength(), e);
				}
			}
		};
	}
	
	/**
	 * If reconciling is in progress, waits until reconciling has caught up to
	 * all the document changes.
	 */
	public void waitForReconcile() throws Exception {
		while (!this.busyReconcile.isDone()) {
			this.busyReconcile.get();
		}
	}

	public STS4LanguageClient getClient() {
		return client;
	}

	@Override
	public ProgressService getProgressService() {
		return progressService;
	}
	
	@Override
	public MessageService getMessageService() {
		return messageService;
	}

	public void setTestListener(LanguageServerTestListener languageServerTestListener) {
		Assert.isLegal(this.testListener==null);
		testListener = languageServerTestListener;
	}

	public boolean canRegisterFileWatchersDynamically() {
		return hasFileWatcherRegistrationSupport;
	}

	@Override
	public DiagnosticService getDiagnosticService() {
		return diagnosticService;
	}
	
	public DiagnosticSeverityProvider getDiagnosticSeverityProvider() {
		return appContext.getBean(DiagnosticSeverityProvider.class);
	}

	@Override
	public SimpleLanguageServer getServer() {
		return this;
	}

	public CompletableFuture<ClientCapabilities> getClientCapabilities() {
		return clientCapabilities;
	}

	public synchronized void onInitialize(Consumer<InitializeParams> handler) {
		Assert.isNull("Multiple initialize handlers not supported yet", this.initializeHandler);
		this.initializeHandler = handler;
	}

	public <T> Mono<T> onInitialized(Mono<T> handler) {
		return Mono.fromFuture(this.initialized).then(handler)
				.doOnError(error -> log.error("", error));
	}


	public void doOnInitialized(Runnable action) {
		onInitialized(Mono.fromRunnable(action)).toFuture();
	}

	public synchronized void onShutdown(Runnable handler) {
		if (shutdownHandler==null) {
			this.shutdownHandler = handler;
		} else {
			Runnable oldHandler = this.shutdownHandler;
			this.shutdownHandler = () -> {
				oldHandler.run();
				handler.run();
			};
		}
	}

	public AsyncRunner getAsync() {
		return this.async;
	}

	public synchronized Mono<Disposable> addClasspathListener(ClasspathListener classpathListener) {
		if (classpathListenerManager == null) {
			classpathListenerManager = new ClasspathListenerManager(this);
		}
		return classpathListenerManager.addClasspathListener(classpathListener);
	}

	public void setCompletionFilter(Optional<CompletionFilter> completionFilter) {
		this.completionFilter = completionFilter;
	}

	public String getCompletionTriggerCharacters() {
		return completionTriggerCharacters;
	}

	public void setCompletionTriggerCharacters(String completionTriggerCharacters) {
		this.completionTriggerCharacters = completionTriggerCharacters;
	}

	public boolean hasHierarchicalDocumentSymbolSupport() {
		return hasHierarchicalDocumentSymbolSupport;
	}

	final public boolean hasCompletionSnippetSupport() {
		return hasCompletionSnippetSupport;
	}
	
	final public Integer getParentProcessId() {
		return parentProcessId;
	}

	@Override
	public CompletableFuture<List<Bean>> beans(BeansParams params) {
		SpringIndex springIndex = appContext.getBean(SpringIndex.class);
		return springIndex.beans(params);
	}

	@Override
	public CompletableFuture<List<Bean>> matchingBeans(MatchingBeansParams params) {
		SpringIndex springIndex = appContext.getBean(SpringIndex.class);
		return springIndex.matchingBeans(params);
	}

}
