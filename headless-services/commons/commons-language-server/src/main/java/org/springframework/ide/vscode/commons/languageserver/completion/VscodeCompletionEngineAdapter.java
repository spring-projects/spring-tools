/*******************************************************************************
 * Copyright (c) 2016, 2025 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.completion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits.TextReplace;
import org.springframework.ide.vscode.commons.languageserver.util.Lsp4jUtils;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.languageserver.util.SortKeys;
import org.springframework.ide.vscode.commons.protocol.CursorMovement;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Adapts a {@link ICompletionEngine}, wrapping it, to implement {@link VscodeCompletionEngine}
 */
public class VscodeCompletionEngineAdapter implements VscodeCompletionEngine {

	private static final Logger log = LoggerFactory.getLogger(VscodeCompletionEngineAdapter.class);
	
	private static final Gson GSON = new Gson();


	public static class LazyCompletionResolver {
		private int nextId = 0; //Used to assign unique id to completion items.

		private Map<String, Consumer<CompletionItem>> resolvers = new HashMap<>();

		private String nextId() {
			//Warning: it's tempting to return 'int' and use a Integer object as id but...
			// Looks like that breaks things because the Integer becomes a Double after being
			// serialized and deserialized to json.
			return ""+(nextId++);
		}

		public synchronized String resolveLater(ICompletionProposal completion, TextDocument doc) {
			String id = nextId();
			resolvers.put(id, (unresolved) -> {
				try {
					resolveItem(doc, completion, unresolved);
				} catch (Exception e) {
					log.error("", e);
				}
			});
			return id;
		}

		public synchronized String resolveLater(Consumer<CompletionItem> resolver) {
			String id = nextId();
			resolvers.put(id, resolver);
			return id;
		}
		
		public synchronized void resolveNow(CancelChecker cancelToken, CompletionItem unresolved) {
			Object id = unresolved.getData();
			if (id != null) {
				Consumer<CompletionItem> resolver = resolvers.get(id instanceof JsonPrimitive ? ((JsonPrimitive)id).getAsString() : id);
				if (resolver != null) {
					resolver.accept(unresolved);
					unresolved.setData(null); //No longer needed after item is resolved.
				} else {
					log.warn("Couldn't resolve completion item. Did it already get flushed from the resolver's cache? "+unresolved.getLabel());
				}
			}
		}

		public synchronized void clear() {
			resolvers.clear();
		}
	}

	private final static int DEFAULT_MAX_COMPLETIONS = 50;
	private int maxCompletions = DEFAULT_MAX_COMPLETIONS; //TODO: move this to CompletionEngineOptions.
	
	private final String RESOLVE_EDIT_COMMAND;

	private SimpleLanguageServer server;
	private ICompletionEngine engine;
	private final LazyCompletionResolver resolver;
	private Optional<CompletionFilter> filter;

	/**
	 * By setting a non-null {@link LazyCompletionResolver} you can enable lazy completion resolution.
	 * By default lazy resolution is not implemented.
	 * <p>
	 * The resolver is injected rather than created locally to allow sharing it between multiple
	 * engines.
	 */
	public VscodeCompletionEngineAdapter(SimpleLanguageServer server, ICompletionEngine engine, LazyCompletionResolver resolver, Optional<CompletionFilter> filter) {
		this.server = server;
		this.engine = engine;
		this.resolver = resolver;
		this.filter = filter;
		
		// Command must be unique for each instance due to completion engine since it can be for different schemas under the same LS umbrella
		this.RESOLVE_EDIT_COMMAND = "sts." + server.EXTENSION_ID + ".resolve.completion.edit." + UUID.randomUUID();
		
		server.onCommand(RESOLVE_EDIT_COMMAND, params -> {
			String uri = params.getArguments().get(0) instanceof String ? (String) params.getArguments().get(0) : ((JsonElement) params.getArguments().get(0)).getAsString();
			String resolveId = params.getArguments().get(1) instanceof String ? (String) params.getArguments().get(1) : ((JsonElement) params.getArguments().get(1)).getAsString();
			JsonElement editJson = params.getArguments().get(2) instanceof JsonElement ? (JsonElement) params.getArguments().get(2) : GSON.toJsonTree(params.getArguments().get(2));
			TextEdit mainEdit = GSON.fromJson(editJson, TextEdit.class);
//			if (isMagicIndentingClient()) {
//				// Reverse sync edit magic client indentation. This indentation only works during completion application not command execution
//				// The reversed edit text is needed to properly determine text to replace
//				mainEdit.setNewText(revertVscodeIndentFix(server.getTextDocumentService().getLatestSnapshot(uri), mainEdit.getRange().getStart(), mainEdit.getNewText()));
//			}
			
			return CompletableFuture.supplyAsync(() -> {
				CompletionItem unresolved = new CompletionItem(RESOLVE_EDIT_COMMAND); 
				unresolved.setTextEdit(Either.forLeft(mainEdit));
				unresolved.setData(resolveId);
				resolver.resolveNow(null, unresolved);
				return unresolved.getTextEdit().getLeft();
			}).thenCompose(newEdit -> {
				Position pos = Lsp4jUtils.getPositionAtEndOfEdit(mainEdit);
				Position cursorPos = Lsp4jUtils.getPositionAtEndOfEdit(newEdit);
				String newText = newEdit.getNewText();
				return server.getClient().applyEdit(new ApplyWorkspaceEditParams(new WorkspaceEdit(Map.of(
						uri, List.of(
								new TextEdit(new Range(mainEdit.getRange().getStart(), pos), newText)
						)
				)))).thenCompose(res -> {
					if (res.isApplied()) {
						return server.getClient().moveCursor(new CursorMovement(uri, cursorPos));
					} else {
						return CompletableFuture.failedStage(new IllegalStateException("Failed to apply edit previously, aborting moving the cursor"));
					}
				});
			});
		});
	}

	public void setMaxCompletions(int maxCompletions) {
		this.maxCompletions = maxCompletions;
	}

	@Override
	public CompletionList getCompletions(CancelChecker cancelToken, TextDocumentPositionParams params) {
		long start = System.currentTimeMillis();

		SimpleTextDocumentService documents = server.getTextDocumentService();
		
		log.info("completion handling - retrieve lastest snapshot for: " + params.getTextDocument().getUri());

		TextDocument doc = documents.getLatestSnapshot(params);
		if (doc != null) {
			
			CompletionList list = new CompletionList();

			try {
				log.info("Starting completion handling for: " + params.getTextDocument().getUri());

				if (resolver != null) {
					//Assumes we don't have more than one completion request in flight from the client.
					// So when a new request arrives we can forget about the old unresolved items:
					resolver.clear();
				}
				
				cancelToken.checkCanceled();

				//TODO: This callable is a 'big lump of work' so can't be canceled in pieces.
				// Should we push using of reactive streams down further and compose this all
				// using reactive style? If not then this is overkill could just as well use
				// only standard Java API such as Executor and CompletableFuture directly.
				int offset = doc.toOffset(params.getPosition());

				// get completions
				InternalCompletionList rawCompletionList = engine.getCompletions(doc, offset);
				
				cancelToken.checkCanceled();
				
				List<ICompletionProposal> completions = filter(rawCompletionList.completionItems());
				Collections.sort(completions, AbstractScoreableProposal.COMPARATOR);
				
				cancelToken.checkCanceled();
	
				boolean isIncomplete = rawCompletionList.isIncomplete();
				
				List<CompletionItem> items = new ArrayList<>(completions.size());
				Optional<SortKeys> sortkeysOpt = engine.keepCompletionsOrder(doc) ? Optional.of(new SortKeys()) : Optional.empty();
				int count = 0;

				for (ICompletionProposal c : completions) {
					count++;

					if (maxCompletions > 0 && count > maxCompletions) {
						// override whatever completion engines said about being incomplete
						isIncomplete = true;
						break;
					}
					try {
						items.add(adaptItem(doc, c, sortkeysOpt));
					} catch (Exception e) {
						log.error("error computing completion", e);
					}
				}
				
				cancelToken.checkCanceled();
				
				list.setItems(items);
				list.setIsIncomplete(isIncomplete);
				
				//This is a hack is no  longer  needed but keeping it as  a reference:
				// See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=535823
				// Reason  hack is not needed is because of the fix in: https://www.pivotaltracker.com/story/show/159667257
	
				//				if (LspClient.currentClient() == Client.ECLIPSE) {
				//					list.setIsIncomplete(true); 
				//				}
				return list;
			}
			catch (CancellationException e) {
				log.info("compututing completions cancellled", e);
				throw e;
			}
			catch (Exception e) {
				log.info("error while compututing completions", e);
			}
			finally {
				long end = System.currentTimeMillis();
				log.info("Got {} completions in " + (end - start) + "ms", list.getItems().size());
			}
		}

		log.info("no completions computed due to missing document snapshot for: ", params.getTextDocument().getUri());
		return SimpleTextDocumentService.NO_COMPLETIONS;
	}

	private CompletionItem adaptItem(TextDocument doc, ICompletionProposal completion, Optional<SortKeys> sortkeysOpt) throws Exception {
		CompletionItem item = new CompletionItem();
		item.setLabel(completion.getLabel());
		item.setKind(completion.getKind());
		sortkeysOpt.ifPresent(sortkeys -> item.setSortText(sortkeys.next()));
		item.setFilterText(completion.getFilterText());
		item.setInsertTextMode(InsertTextMode.AsIs);
		item.setLabelDetails(completion.getLabelDetails());
		if (completion.isDeprecated()) {
			item.setTags(List.of(CompletionItemTag.Deprecated));
		}
		
		resolveMainEdit(doc, completion, item);
		
		if (resolver != null) {
			item.setData(resolver.resolveLater(completionItem -> {
				try {
					resolveCompletionItem(completionItem, completion, doc);
				} catch (Exception e) {
					log.error("Error resolving completion", e);
				}
			}));
		} else {
			resolveCompletionItem(item, completion, doc);
		}
		
		List<Object> commands = new ArrayList<>(2);
		completion.getCommand().ifPresent(commands::add);
		if (LspClient.currentClient() != LspClient.Client.ECLIPSE) {
			/*
			 *  Eclipse client always send completionItem resolve request before applying completion. 
			 *  However, LSP doesn't guarantee this in general and addtionalEdits must be on lines different from the main edit.
			 *  Due to LSP limitation it is best to execute extra edits modifying main edit via the command
			 */
			if (!completion.getTextEdit().isResolved() && item.getTextEdit().isLeft()) {
				commands.add(new Command("Resolve edit", RESOLVE_EDIT_COMMAND, List.of(doc.getUri(), item.getData(), item.getTextEdit().getLeft())));
			}
		}
		if (completion.isTriggeringNextCompletionRequest()) {
			commands.add(new Command("Completion Proposal Request", "editor.action.triggerSuggest"));
		}
		if (!commands.isEmpty()) {
			Command command = (Command)commands.get(0);
			if (commands.size() == 1 && command.getCommand().equals("editor.action.triggerSuggest")) {
				item.setCommand(command);
			} else {
				item.setCommand(new Command("Commands", server.COMMAND_LIST_COMMAND_ID, commands));
			}
		}
		return item;
	}
	
	private void resolveCompletionItem(CompletionItem item, ICompletionProposal completion, TextDocument doc) throws Exception {		
		item.setDetail(completion.getDetail());
		if (completion.getDocumentation() != null) {
			resolveItem(doc, completion, item);
		}
		
		if (!completion.getTextEdit().isResolved()) {
			completion.getTextEdit().resolve();
		}
		// Keep main edit resolution outside of the if block above. If resolve completion item and command are executed in parallel command would need to generate the new edit
		resolveMainEdit(doc, completion, item);
		
		resolveAdditionalEdits(doc, completion, item);
		
		// Remove the Resolve Edit Command if present since everything is resolved already (Not expected to be around for Eclipse client)
		if (item.getCommand() != null) {
			if (server.COMMAND_LIST_COMMAND_ID.equals(item.getCommand().getCommand())) {
				List<Object> subCommands = item.getCommand().getArguments();
				for (ListIterator<Object> itr = subCommands.listIterator(); itr.hasNext();) {
					Object o = itr.next();
					Command subCommand = o instanceof Command ? (Command) o : GSON.fromJson(o instanceof JsonElement ? (JsonElement) o : GSON.toJsonTree(o), Command.class);
					if (RESOLVE_EDIT_COMMAND.equals(subCommand.getCommand())) {
						itr.remove();
						// Only one such command expected
						break;
					}
				}
				if (subCommands.size() == 1) {
					Object o = subCommands.get(0);
					Command subCommand = o instanceof Command ? (Command) o : GSON.fromJson(o instanceof JsonElement ? (JsonElement) o : GSON.toJsonTree(o), Command.class);
					item.setCommand(subCommand);
				} else if (subCommands.isEmpty()) {
					item.setCommand(null);
				}
			} else if (RESOLVE_EDIT_COMMAND.equals(item.getCommand().getCommand())) {
				item.setCommand(null);
			}
		}
		if (item.getCommand() != null && server.COMMAND_LIST_COMMAND_ID.equals(item.getCommand().getCommand())) {
			List<Object> subCommands = item.getCommand().getArguments();
			for (ListIterator<Object> itr = subCommands.listIterator(); itr.hasNext();) {
				Object o = itr.next();
				Command subCommand = o instanceof Command ? (Command) o : GSON.fromJson(o instanceof JsonElement ? (JsonElement) o : GSON.toJsonTree(o), Command.class);
				if (RESOLVE_EDIT_COMMAND.equals(subCommand.getCommand())) {
					itr.remove();
					// Only one such command expected
					break;
				}
			}
			if (subCommands.isEmpty()) {
				item.setCommand(null);
			}
		}
	}

	private List<ICompletionProposal> filter(Collection<ICompletionProposal> completions) {
		if (filter.isPresent()) {
			List<ICompletionProposal> filtered = new ArrayList<>(completions.size());
			CompletionFilter filterVal = filter.get();
			for (ICompletionProposal proposal : completions) {
				if (filterVal.include(proposal)) {
					filtered.add(proposal);
				}
			}
			return filtered;
		}
		else {
			return new ArrayList<>(completions);
		}
	}

	private static void resolveItem(TextDocument doc, ICompletionProposal completion, CompletionItem item) throws Exception {
		MarkupContent content = new MarkupContent();
		content.setKind(MarkupKind.MARKDOWN);
		content.setValue(toMarkdown(completion.getDocumentation()));
		item.setDocumentation(content);
	}

	private void resolveMainEdit(TextDocument doc, ICompletionProposal completion, CompletionItem item) {
		AtomicBoolean usedSnippets = new AtomicBoolean();
		Optional<TextEdit> mainEdit = adaptEdits(doc, completion.getTextEdit(), usedSnippets);
		if (mainEdit.isPresent()) {
			item.setTextEdit(Either.forLeft(mainEdit.get()));
			if (server.hasCompletionSnippetSupport()) {
				item.setInsertTextFormat(usedSnippets.get() ? InsertTextFormat.Snippet : InsertTextFormat.PlainText);
			} else {
				item.setInsertTextFormat(InsertTextFormat.PlainText);
			}
		} else {
			item.setInsertText("");
		}
	}
	
	private void resolveAdditionalEdits(TextDocument doc, ICompletionProposal completion, CompletionItem item) {
		completion.getAdditionalEdit().ifPresent(editSupplier -> {
			DocumentEdits edit = editSupplier.get();
			if (edit != null) {
				if (!edit.isResolved()) {
					edit.resolve();
				}
				adaptEdits(doc, edit, null).ifPresent(extraEdit -> {
					item.setAdditionalTextEdits(ImmutableList.of(extraEdit));
				});
			} else {
				item.setAdditionalTextEdits(null);
			}
		});
	}

	private static String toMarkdown(Renderable r) {
		if (r != null) {
			return r.toMarkdown();
		}
		return null;
	}

	private Optional<TextEdit> adaptEdits(TextDocument doc, DocumentEdits edits, AtomicBoolean usedSnippets) {
		try {
			TextReplace replaceEdit = edits == null ? null : edits.asReplacement(doc);
			if (usedSnippets != null && edits != null) {
				usedSnippets.set(edits.hasSnippets());
			}
			if (replaceEdit == null) {
				//The original edit does nothing.
				return Optional.empty();
			} else {
				TextDocument newDoc = doc.copy();
				edits.apply(newDoc);
				TextEdit vscodeEdit = new TextEdit();
				vscodeEdit.setRange(doc.toRange(replaceEdit.start, replaceEdit.end - replaceEdit.start));
				String newText = replaceEdit.newText;
				IRegion selection = edits.getSelection();
				
				if (selection != null && usedSnippets != null) {
					//Special handling for the case where cursor is *not* just at the end of the newText
					int cursor = selection.getOffset() + selection.getLength();
					cursor = cursor - replaceEdit.start;
					if (cursor < newText.length() && !edits.hasSnippets()) {
						newText = server.createSnippetBuilder()
								.text(newText.substring(0, cursor))
								.finalTabStop()
								.text(newText.substring(cursor))
								.build()
								.toString();
						usedSnippets.set(true);
					}
				}

				vscodeEdit.setNewText(newText);
				return Optional.of(vscodeEdit);
			}
		}  catch (Exception e) {
			log.error("{}", e);
			return Optional.empty();
		}
	}

	@Override
	public CompletionItem resolveCompletion(CancelChecker cancelToken, CompletionItem unresolved) {
		resolver.resolveNow(cancelToken, unresolved);
		return unresolved;
	}

	@FunctionalInterface
	public interface CompletionFilter {

		/**
		 *
		 * @param proposal
		 * @return true if proposal should be included from completion list. False
		 *         otherwise
		 */
		boolean include(ICompletionProposal proposal);

	}
	
}
