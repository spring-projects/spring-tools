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
package org.springframework.ide.vscode.boot.kotlin.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author Martin Lippert
 */
public class KotlinSyntaxReadersTest {

	@Test
	void theDefaultParserComesFirstAndAllParsersAreThere() {
		assertThat(KotlinSyntaxReaders.names()).startsWith(KotlinSyntaxReaders.DEFAULT);
		assertThat(KotlinSyntaxReaders.names()).containsExactly(KotlinSyntaxReaders.PSI, KotlinSyntaxReaders.TREE_SITTER);
	}

	@Test
	void everyNameIsKnownAndGivesItsOwnReader() {
		for (String name : KotlinSyntaxReaders.names()) {
			assertThat(KotlinSyntaxReaders.isKnown(name)).isTrue();

			KotlinSyntaxReader reader = KotlinSyntaxReaders.create(name);
			try {
				assertThat(reader).isNotSameAs(KotlinSyntaxReaders.create(name));
				assertThat(reader.read("A.kt", "class A").classes()).hasSize(1);
			}
			finally {
				reader.dispose();
			}
		}
	}

	@Test
	void unknownNamesFallBackToTheDefault() {
		assertThat(KotlinSyntaxReaders.isKnown("nonsense")).isFalse();
		assertThat(KotlinSyntaxReaders.isKnown(null)).isFalse();

		KotlinSyntaxReader reader = KotlinSyntaxReaders.create("nonsense");
		try {
			assertThat(reader).isInstanceOf(KotlinPsiReader.class);
		}
		finally {
			reader.dispose();
		}
		KotlinSyntaxReader forNull = KotlinSyntaxReaders.create(null);
		try {
			assertThat(forNull).isInstanceOf(KotlinPsiReader.class);
		}
		finally {
			forNull.dispose();
		}
	}

}
