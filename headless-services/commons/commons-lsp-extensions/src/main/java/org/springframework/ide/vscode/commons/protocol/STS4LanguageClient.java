/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.json.ResponseJsonAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.ide.vscode.commons.protocol.java.ClasspathListenerParams;
import org.springframework.ide.vscode.commons.protocol.java.Gav;
import org.springframework.ide.vscode.commons.protocol.java.JavaCodeCompleteData;
import org.springframework.ide.vscode.commons.protocol.java.JavaCodeCompleteParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaDataParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaSearchParams;
import org.springframework.ide.vscode.commons.protocol.java.JavaTypeHierarchyParams;
import org.springframework.ide.vscode.commons.protocol.java.ProjectGavParams;
import org.springframework.ide.vscode.commons.protocol.java.TypeData;
import org.springframework.ide.vscode.commons.protocol.java.TypeDescriptorData;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexLanguageClient;

/**
 * Some 'custom' extensions to standard LSP {@link LanguageClient}.
 *
 * @author Kris De Volder
 */
public interface STS4LanguageClient extends SpringIndexLanguageClient {
	
	@JsonNotification("sts/liveprocess/connected")
	void liveProcessConnected(LiveProcessSummary processKey);

	@JsonNotification("sts/liveprocess/disconnected")
	void liveProcessDisconnected(LiveProcessSummary processKey);

	@JsonNotification("sts/liveprocess/updated")
	void liveProcessDataUpdated(LiveProcessSummary processKey);
	
	@JsonNotification("sts/liveprocess/memory/metrics/updated")
	void liveProcessMemoryMetricsDataUpdated(LiveProcessSummary processKey);
	
	@JsonNotification("sts/liveprocess/gcpauses/metrics/updated")
	void liveProcessGcPausesMetricsDataUpdated(LiveProcessSummary processKey);
	
	@JsonNotification("sts/liveprocess/loglevel/updated")
	void liveProcessLogLevelUpdated(LiveProcessLoggersSummary liveProcessLoggersSummary);

	@JsonNotification("sts/highlight")
	void highlight(HighlightParams highlights);

	@JsonRequest("sts/moveCursor")
	CompletableFuture<Object> moveCursor(CursorMovement cursorMovement);

	@JsonRequest("sts/addClasspathListener")
	CompletableFuture<Object> addClasspathListener(ClasspathListenerParams params);

	@JsonRequest("sts/removeClasspathListener")
	CompletableFuture<Object> removeClasspathListener(ClasspathListenerParams classpathListenerParams);

	@JsonRequest("sts/javadoc")
	CompletableFuture<MarkupContent> javadoc(JavaDataParams params);

	@JsonRequest("sts/javaType")
	CompletableFuture<TypeData> javaType(JavaDataParams params);

	@JsonRequest("sts/javadocHoverLink")
	CompletableFuture<String> javadocHoverLink(JavaDataParams params);

	@JsonRequest("sts/javaLocation")
	CompletableFuture<Location> javaLocation(JavaDataParams params);
	
	@JsonRequest("sts/javaSearchTypes")
	CompletableFuture<List<TypeDescriptorData>> javaSearchTypes(JavaSearchParams params);
	
	@JsonRequest("sts/javaSearchPackages")
	CompletableFuture<List<String>> javaSearchPackages(JavaSearchParams params);
	
	@JsonRequest("sts/javaSubTypes")
	@ResponseJsonAdapter(TypeHierarchyResponseAdapter.class)
	CompletableFuture<List<Either<TypeDescriptorData, TypeData>>> javaSubTypes(JavaTypeHierarchyParams params);
	
	@JsonRequest("sts/javaSuperTypes")
	@ResponseJsonAdapter(TypeHierarchyResponseAdapter.class)
	CompletableFuture<List<Either<TypeDescriptorData, TypeData>>> javaSuperTypes(JavaTypeHierarchyParams params);

	@JsonRequest("sts/javaCodeComplete")
	CompletableFuture<List<JavaCodeCompleteData>> javaCodeComplete(JavaCodeCompleteParams params);
	
	@JsonRequest("sts/project/gav")
	CompletableFuture<List<Gav>> projectGAV(ProjectGavParams params);
	
}
