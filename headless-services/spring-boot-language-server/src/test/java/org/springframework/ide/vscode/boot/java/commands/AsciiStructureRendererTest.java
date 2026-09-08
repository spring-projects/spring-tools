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
package org.springframework.ide.vscode.boot.java.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.DiffNode;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.DiffStats;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;

/**
 * @author Martin Lippert
 */
public class AsciiStructureRendererTest {

	private static final Instant BASELINE_AT = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant CURRENT_AT = Instant.parse("2026-01-02T00:00:00Z");

	@Test
	void collapsesUnchangedSubtreesByDefault() {
		StructureTreeDiff diff = sampleDiff();

		String rendered = AsciiStructureRenderer.render(diff, false);

		assertThat(rendered).isEqualTo("""
				Structure diff for project 'spring-petclinic'
				  baseline: 2026-01-01T00:00:00Z    current: 2026-01-02T00:00:00Z
				  +2 added   -0 removed   ~2 modified   4 unchanged

				spring-petclinic
				├── ~ Web Layer
				│   ├── + OwnerRestController
				│   │   └── + findAll()
				│   └──   OwnerController  (3 unchanged)
				└──   Spring Data  (1 unchanged)
				""");
	}

	@Test
	void expandsUnchangedSubtreesWhenRequested() {
		StructureTreeDiff diff = sampleDiff();

		String rendered = AsciiStructureRenderer.render(diff, true);

		assertThat(rendered).isEqualTo("""
				Structure diff for project 'spring-petclinic'
				  baseline: 2026-01-01T00:00:00Z    current: 2026-01-02T00:00:00Z
				  +2 added   -0 removed   ~2 modified   4 unchanged

				spring-petclinic
				├── ~ Web Layer
				│   ├── + OwnerRestController
				│   │   └── + findAll()
				│   └──   OwnerController
				│       ├──   m1
				│       └──   m2
				└──   Spring Data
				""");
	}

	private static StructureTreeDiff sampleDiff() {
		DiffNode findAll = new DiffNode("id-findAll", "findAll()", "method", ChangeType.ADDED, List.of());
		DiffNode ownerRestController = new DiffNode("id-OwnerRestController", "OwnerRestController", "type", ChangeType.ADDED, List.of(findAll));

		DiffNode m1 = new DiffNode("id-m1", "m1", "method", ChangeType.UNCHANGED, List.of());
		DiffNode m2 = new DiffNode("id-m2", "m2", "method", ChangeType.UNCHANGED, List.of());
		DiffNode ownerController = new DiffNode("id-OwnerController", "OwnerController", "type", ChangeType.UNCHANGED, List.of(m1, m2));

		DiffNode webLayer = new DiffNode("id-WebLayer", "Web Layer", "stereotype", ChangeType.MODIFIED, List.of(ownerRestController, ownerController));
		DiffNode springData = new DiffNode("id-SpringData", "Spring Data", "stereotype", ChangeType.UNCHANGED, List.of());

		DiffNode root = new DiffNode("id-root", "spring-petclinic", "application", ChangeType.MODIFIED, List.of(webLayer, springData));
		DiffStats stats = new DiffStats(2, 0, 2, 4);

		return new StructureTreeDiff("spring-petclinic", BASELINE_AT, CURRENT_AT, root, stats);
	}

}
