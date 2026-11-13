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
package org.springframework.ide.vscode.boot.kotlin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the whole test class once for every Kotlin parser that is registered in
 * {@code KotlinSyntaxReaders}, so every implementation is held to the very same tests. The name
 * of the parser is injected into a field of the test class:
 *
 * <pre>
 * &#64;Parameter String parser;
 * </pre>
 *
 * A parser that is added to the registry is picked up by all the tests that carry this
 * annotation, nothing else has to change.
 *
 * @author Martin Lippert
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedClass(name = "parser: {0}")
@MethodSource("org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers#names")
public @interface AllKotlinParsers {
}
