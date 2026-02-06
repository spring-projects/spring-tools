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
package org.springframework.ide.vscode.boot.bootiful;

import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.languageserver.testharness.HarnessLanguageServerRunner;

import com.google.gson.GsonBuilder;

@Configuration
public class LsGsonConfig {

	@ConditionalOnMissingBean(LanguageServerRunner.class)
	@Bean
	HarnessLanguageServerRunner harnessLanguageServerRunner(Optional<Consumer<GsonBuilder>> configGsonOpt) {
		return new HarnessLanguageServerRunner(configGsonOpt);
	}

}
