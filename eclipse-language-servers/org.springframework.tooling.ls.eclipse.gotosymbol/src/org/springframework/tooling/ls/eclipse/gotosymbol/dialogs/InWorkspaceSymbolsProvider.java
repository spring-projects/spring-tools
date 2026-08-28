/*******************************************************************************
 * Copyright (c) 2017, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerProjectExecutor;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("restriction")
public class InWorkspaceSymbolsProvider implements SymbolsProvider {

	private static final Predicate<ServerCapabilities> WS_SYMBOL_CAP = capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider());
	
	public static InWorkspaceSymbolsProvider createFor(Supplier<IProject> _project) {
		LanguageServerProjectExecutor lss = LanguageServers.forProject(_project.get()).withFilter(WS_SYMBOL_CAP).excludeInactive();
		return new InWorkspaceSymbolsProvider(lss);
	}
	
	public static InWorkspaceSymbolsProvider createFor(IProject project) {
		LanguageServerProjectExecutor lss = LanguageServers.forProject(project).withFilter(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider())).excludeInactive();
		return new InWorkspaceSymbolsProvider(lss);
	}


	private static final Duration TIMEOUT = Duration.ofSeconds(2);
	private static final int MAX_RESULTS = 200;
	private LanguageServers<?> languageServers;
	
	public InWorkspaceSymbolsProvider(LanguageServers<?> languageServers) {
		this.languageServers = languageServers;
	}

	@Override
	public String getName() {
		return "Symbols in Workspace";
	}

	@Override
	public List<SymbolContainer> fetchFor(String query) throws Exception {
		//TODO: if we want decent support for multiple language servers...
		// consider changing SymbolsProvider api and turning the stuff in here into something producing a 
		// Flux<Collection<SymbolInformation>>
		// This will help in 
		//  - supporting cancelation
		//  - executing multiple requests to different servers in parallel.
		//  - producing results per server so don't have to wait for one slow server to see the rest.
		//However it will also add complexity to the code that consumes this and at this time we only
		// really use this with a single language server anyways.
		WorkspaceSymbolParams params = new WorkspaceSymbolParams(query);

		List<CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>> responses = languageServers.computeAll(ls -> ls.getWorkspaceService().symbol(params));
		
		Flux<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> first = Flux.fromIterable(responses).flatMap(Mono::fromFuture).timeout(TIMEOUT);
		
		Flux<SymbolContainer> symbols = first
				.doOnError(e -> log(e))
				.onErrorReturn(Either.forLeft(ImmutableList.of()))
				.map(eitherList -> (eitherList.isLeft()
						? SymbolsProvider.toSymbolContainerFromSymbolInformation(eitherList.getLeft())
						: SymbolsProvider.toSymbolContainerFromWorkspaceSymbols(eitherList.getRight())))
				.flatMap(Flux::fromIterable);

		//Consider letting the Flux go out from here instead of blocking and collecting elements.
		return symbols.take(MAX_RESULTS).collect(Collectors.toList()).block();
	}
	
	public static InWorkspaceSymbolsProvider createFor(ExecutionEvent event) {
		final IProject project = projectFor(event);
		if (project != null) {
			return createFor(project);
		}
		return null;
	}

	public static IProject projectFor(ExecutionEvent event) {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		IResource resource = null;
		if (part != null && part.getEditorInput() != null) {
			resource = part.getEditorInput().getAdapter(IResource.class);
		} else {
			IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
			if (selection.isEmpty() || !(selection.getFirstElement() instanceof IAdaptable)) {
				return null;
			}
			IAdaptable adaptable = (IAdaptable) selection.getFirstElement();
			resource = adaptable.getAdapter(IResource.class);
		}
		if (resource != null) {
			return resource.getProject();
		}
		return null;
		
	}
	
	@Override
	public boolean fromFile(SymbolContainer symbol) {
		return false;
	}
	
	private static void log(Throwable e) {
		GotoSymbolPlugin.getInstance().getLog().log(ExceptionUtil.status(e));
	}

}
