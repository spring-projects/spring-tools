/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gayan Perera <gayanper@gmail.com> - In project symbol provider implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeansParams;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexLanguageServer;
import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("restriction")
public class InProjectSymbolsProvider implements SymbolsProvider {

	public static InProjectSymbolsProvider createFor(LiveExpression<IProject> project) {
		return new InProjectSymbolsProvider(project::getValue);
	}

	public static InProjectSymbolsProvider createFor(ExecutionEvent event) {
		final IProject project = InWorkspaceSymbolsProvider.projectFor(event);
		if (project != null) {
			return createFor(LiveExpression.constant(project));
		}
		return null;
	}

	private static final Duration TIMEOUT = Duration.ofSeconds(2);
	private static final int MAX_RESULTS = 200;
	
	private Supplier<IProject> project;

	public InProjectSymbolsProvider(Supplier<IProject> project) {
		this.project = project;
	}

	@Override
	public String getName() {
		return "Symbols in Project";
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
		
		IProject project = this.project.get();
		
		if (project != null) {

//			dumpBeansModel(project.getName()); // for debug purposes
			
			String projectLocationPrefix = LSPEclipseUtils.toUri(project).toASCIIString();
			query = "locationPrefix:" + projectLocationPrefix + "?" + query;
			
			WorkspaceSymbolParams params = new WorkspaceSymbolParams(query);
			
			List<CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>> responses = LanguageServers
					.forProject(project)
					.withFilter(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()))
					.excludeInactive()
					.computeAll(ls -> ls.getWorkspaceService().symbol(params));
			

			Flux<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> first = Flux.fromIterable(responses)
					.flatMap(Mono::fromFuture)
					.timeout(TIMEOUT);
			
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
		return ImmutableList.of();
	}
	
	@Override
	public boolean fromFile(SymbolContainer symbol) {
		return false;
	}
	
	private static void log(Throwable e) {
		GotoSymbolPlugin.getInstance().getLog().log(ExceptionUtil.status(e));
	}
	
	private void dumpBeansModel(String projectName) {
		IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		
		BeansParams beansParams = new BeansParams();
		beansParams.setProjectName(projectName);

		List<CompletableFuture<List<Bean>>> executors = LanguageServers
				.forProject(p)
				.withFilter(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()))
				.excludeInactive()
				.computeAll(ls -> {
					if (ls instanceof SpringIndexLanguageServer) {
						return ((SpringIndexLanguageServer) ls).beans(beansParams);
					}
					return CompletableFuture.completedFuture(Collections.emptyList());
				});
		
		for (CompletableFuture<List<Bean>> beansFuture : executors) {
			try {
				List<Bean> beans = beansFuture.get();
					
				for (Bean bean : beans) {
					System.out.println(bean);
				}					
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (java.util.concurrent.ExecutionException e) {
				e.printStackTrace();
			}
		}
		
	}
	
}
