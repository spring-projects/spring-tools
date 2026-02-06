/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.languageserver.testharness;

import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class HarnessLanguageServerRunner extends LanguageServerRunner {
	
	private Gson gson;

	public HarnessLanguageServerRunner(Optional<Consumer<GsonBuilder>> configureGsonOpt) {
		super(null, null, null, configureGsonOpt);
		GsonBuilder gsonBuilder = new MessageJsonHandler(Collections.emptyMap()).getDefaultGsonBuilder();
		configureGsonOpt.ifPresent(c -> c.accept(gsonBuilder));
		this.gson = gsonBuilder.create();
	}

	@Override
	public void run(String... args) throws Exception {
		// nothing
	}

	@Override
	public void start() throws Exception {
		throw new UnsupportedAddressTypeException();
	}

	@Override
	public void startAsServer() throws Exception {
		throw new UnsupportedAddressTypeException();
	}

	@Override
	protected ExecutorService createServerThreads() {
		throw new UnsupportedAddressTypeException();
	}

	@Override
	public Gson getGson() {
		return gson;
	}
	
}
