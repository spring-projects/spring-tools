/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol;

/**
 * Asks the client to set one of its settings - see {@link STS4LanguageClient#setConfiguration}.
 *
 * @param key the full key of the setting, e.g. {@code boot-java.structure.git-baseline-enabled}
 * @param value the value to set it to
 *
 * @author Martin Lippert
 */
public record SetConfigurationParams(String key, Object value) {
}
