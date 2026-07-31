/*******************************************************************************
 * Copyright (c) 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

/**
 * Tests for exception classification in the compilation unit cache.
 *
 * @author Pavel Zaitsev
 */
class CompilationUnitCacheExceptionTest {

	@Test
	void recognizesCancellationWrappedByCompletionStage() {
		assertTrue(CompilationUnitCache.isCancellation(new CompletionException(new CancellationException())));
	}

	@Test
	void doesNotTreatRealExceptionalCompletionAsCancellation() {
		assertFalse(CompilationUnitCache.isCancellation(new CompletionException(new IllegalStateException())));
	}

}
