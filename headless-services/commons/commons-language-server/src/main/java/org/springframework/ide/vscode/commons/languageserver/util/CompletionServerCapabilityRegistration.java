/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class CompletionServerCapabilityRegistration implements ServerCapabilityInitializer {
	
	private static Logger log = LoggerFactory.getLogger(CompletionServerCapabilityRegistration.class);
	
	private final SimpleLanguageServer server;
	private final LanguageServerProperties props;

	private boolean hasDynamicCompletionRegistration;

	public CompletionServerCapabilityRegistration(SimpleLanguageServer server, LanguageServerProperties props) {
		this.server = server;
		this.props = props;
	}

	private List<String> getMergedTriggerCharacters() {
		Set<String> allTriggerChars = new TreeSet<>();
		Map<String, String> triggersByLanguage = props.getCompletionTriggerCharacters();
		if (triggersByLanguage != null) {
			for (String triggers : triggersByLanguage.values()) {
				for (int i = 0; i < triggers.length(); i++) {
					String c = triggers.substring(i, i+1);
					allTriggerChars.add(c);
				}
			}
		}
		if (!allTriggerChars.isEmpty()) {
			return ImmutableList.copyOf(allTriggerChars);
		}
		return null;
	}

	@Override
	public void initialize(InitializeParams params, ServerCapabilities cap) {
		this.hasDynamicCompletionRegistration = SimpleLanguageServer.safeGet(false, () -> params.getCapabilities().getTextDocument().getCompletion().getDynamicRegistration());
		log.info("hasDynamicCompletionRegistration = "+hasDynamicCompletionRegistration);
		List<String> allTiggerChars = getMergedTriggerCharacters();
		if (!hasDynamicCompletionRegistration || allTiggerChars == null) {
			//Register completion provider and triggerCharacters statically
			CompletionOptions completionProvider = new CompletionOptions();
			completionProvider.setResolveProvider(server.hasLazyCompletionResolver());
			completionProvider.setTriggerCharacters(getMergedTriggerCharacters());
			cap.setCompletionProvider(completionProvider);
			log.info("Registering Completion Capability Statically");
		} else {
			//Register completion provider and triggerCharacters dynamically, one registration per
			//language
			server.onInitialized(Mono.fromCallable(() -> {
				List<Registration> registrations = new ArrayList<>();
				for (Entry<String, String> entry : props.getCompletionTriggerCharacters().entrySet()) {
					String languageId = entry.getKey();
					String triggerCharsString = entry.getValue();
					Registration r = new Registration(UUID.randomUUID().toString(), "textDocument/completion");
					CompletionRegistrationOptions registerOptions = new CompletionRegistrationOptions();
					registerOptions.setResolveProvider(server.hasLazyCompletionResolver());
					registerOptions.setTriggerCharacters(toTriggerChars(triggerCharsString));
					DocumentFilter df = new DocumentFilter();
					df.setLanguage(languageId);
					registerOptions.setDocumentSelector(ImmutableList.of(df));
					r.setRegisterOptions(registerOptions);
					registrations.add(r);
				}
				RegistrationParams regParams = new RegistrationParams(registrations);
				log.info("Registering Dynamic Completion Capabality");
				log.debug("regParams = {}", regParams);
				return Mono.fromFuture(server.getClient().registerCapability(regParams));
			})).toFuture();
		}
	}

	private List<String> toTriggerChars(String triggerCharsString) {
		ImmutableList.Builder<String> builder = ImmutableList.builder();
		for (int i = 0; i < triggerCharsString.length(); i++) {
			builder.add(triggerCharsString.substring(i, i+1));
		}
		ImmutableList<String> list = builder.build();
		if (list.isEmpty()) {
			return null;
		}
		return list;
	}

}
