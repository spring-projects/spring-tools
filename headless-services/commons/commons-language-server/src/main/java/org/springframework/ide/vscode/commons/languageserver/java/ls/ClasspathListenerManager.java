/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java.ls;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.ClasspathListenerParams;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.AsyncRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public class ClasspathListenerManager {

	private static Logger log = LoggerFactory.getLogger(ClasspathListenerManager.class);

	private static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	private SimpleLanguageServer server;
	private AsyncRunner async;

	private static final Gson gson = new Gson();

	public ClasspathListenerManager(SimpleLanguageServer server) {
		this.server = server;
		this.async = server.getAsync();
	}

	public Mono<Disposable> addClasspathListener(ClasspathListener classpathListener) {
		String callbackCommandId = "sts4.classpath." + RandomStringUtils.secure().nextAlphabetic(8);

		// 1. register callback command handler in SimpleLanguageServer
		Disposable unregisterCommand = server.onCommand(callbackCommandId, (ExecuteCommandParams callbackParams) -> async.invoke(() -> {
			log.debug("callback {} received {}", callbackCommandId, callbackParams);
			List<Object> args = callbackParams.getArguments();
			log.debug("args = {}", args);
			//Args are deserialized as com.google.gson.JsonElements.
			if (((JsonElement) args.get(0)).isJsonArray()) {
				// If events are batched... then they will arrive as a array of arrays.
				for (Object arg : args) {
					JsonArray event = (JsonArray) arg;

					if (event.size() < 6) {
						throw new IllegalStateException("Classpath event must have 6 elements but has " + event.size() + ". JDT LS extension version mismatch?");
					}

					String projectUri = event.get(0).getAsString();
					log.debug("projectUri = {}", projectUri);
					String name = event.get(1).getAsString();
					log.debug("name = {}", name);
					boolean deleted = event.get(2).getAsBoolean();
					log.debug("deleted = {}", deleted);

					Classpath classpath = gson.fromJson((JsonElement)event.get(3), Classpath.class);
					log.debug("classpath = {}", classpath);
					
					ProjectBuild projectBuild = gson.fromJson((JsonElement)event.get(4), ProjectBuild.class);
					log.debug("projectBuild = {}", projectBuild);

					Map<String, String> javaCoreOptions = gson.fromJson((JsonElement)event.get(5), new TypeToken<Map<String, String>>(){}.getType());
					log.debug("javaCoreOptions size = {}", javaCoreOptions != null ? javaCoreOptions.size() : 0);

					classpathListener.changed(new ClasspathListener.Event(projectUri, name, deleted, classpath, projectBuild, javaCoreOptions));
				}
			} else {
				//Still support non-batched events for backwards compatibility with clients
				// that don't provide batched event support (e.g. IDEA client may only adopt this
				// later, or not adopt it at all).
				if (args.size() < 6) {
					throw new IllegalStateException("Classpath event must have 6 arguments but has " + args.size() + ". JDT LS extension version mismatch?");
				}

				String projectUri = ((JsonElement) args.get(0)).getAsString();
				log.debug("projectUri = {}", projectUri);
				String name = ((JsonElement) args.get(1)).getAsString();
				log.debug("name = {}", name);
				boolean deleted = ((JsonElement)args.get(2)).getAsBoolean();
				log.debug("deleted = {}", deleted);

				Classpath classpath = gson.fromJson((JsonElement)args.get(3), Classpath.class);
				log.debug("classpath = {}", classpath);
				
				ProjectBuild projectBuild = gson.fromJson((JsonElement)args.get(4), ProjectBuild.class);
				log.debug("projectBuild = {}", projectBuild);

				Map<String, String> javaCoreOptions = gson.fromJson((JsonElement)args.get(5), new TypeToken<Map<String, String>>(){}.getType());
				log.debug("javaCoreOptions size = {}", javaCoreOptions != null ? javaCoreOptions.size() : 0);
				
				classpathListener.changed(new ClasspathListener.Event(projectUri, name, deleted, classpath, projectBuild, javaCoreOptions));
			}
			return "done";
		}));

		// 2. register the callback command with the client
		String registrationId = UUID.randomUUID().toString();
		RegistrationParams params = new RegistrationParams(ImmutableList.of(
				new Registration(registrationId,
						WORKSPACE_EXECUTE_COMMAND,
						ImmutableMap.of("commands", ImmutableList.of(callbackCommandId))
				)
		));
		// 3. call the client to ask it to call that callback
		Mono<Void> registerCallbackCommand = Mono.defer(() -> Mono.fromFuture(
				server.getClient().registerCapability(params)
		));

		Mono<Object> registerClasspathListener = Mono.defer(() -> Mono.fromFuture(
				server.getClient().addClasspathListener(new ClasspathListenerParams(callbackCommandId, true))
		));

		Disposable cleanups = () -> {
			log.info("Unregistering classpath callback "+callbackCommandId +" ...");
			AsyncRunner.thenLog(log,
					this.server.getClient().removeClasspathListener(new ClasspathListenerParams(callbackCommandId))
			);
			log.info("Unregistering classpath callback "+callbackCommandId +" OK");
			AsyncRunner.thenLog(log,
				this.server.getClient().unregisterCapability(new UnregistrationParams(ImmutableList.of(
						new Unregistration(registrationId, WORKSPACE_EXECUTE_COMMAND)
			    )))
			);
			unregisterCommand.dispose();
		};
		return
			registerCallbackCommand
			.then(registerClasspathListener)
			.thenReturn(cleanups);
	}

}
