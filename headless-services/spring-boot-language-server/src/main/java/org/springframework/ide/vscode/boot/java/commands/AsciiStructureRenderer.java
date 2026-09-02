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

import java.util.List;

import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.DiffNode;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.DiffStats;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;

/**
 * Renders a {@link StructureTreeDiff} as an ascii-art tree with +/-/~ markers for added, removed
 * and modified nodes, so it can be read directly out of an MCP tool result.
 *
 * @author Martin Lippert
 */
public class AsciiStructureRenderer {

	private AsciiStructureRenderer() {
	}

	/**
	 * @param includeUnchanged when false (the default for the MCP tools), unchanged subtrees are
	 *                          collapsed to a single "(N unchanged)" line; when true, the full tree
	 *                          is printed
	 */
	public static String render(StructureTreeDiff diff, boolean includeUnchanged) {
		StringBuilder out = new StringBuilder();

		out.append("Structure diff for project '").append(diff.projectName()).append("'\n");
		out.append("  baseline: ").append(diff.baselineAt()).append("    current: ").append(diff.currentAt()).append("\n");

		DiffStats stats = diff.stats();
		out.append("  +").append(stats.added()).append(" added   ")
				.append("-").append(stats.removed()).append(" removed   ")
				.append("~").append(stats.modified()).append(" modified   ")
				.append(stats.unchanged()).append(" unchanged\n\n");

		out.append(diff.root().label()).append("\n");
		renderChildren(diff.root().children(), "", out, includeUnchanged);

		return out.toString();
	}

	private static void renderChildren(List<DiffNode> children, String indent, StringBuilder out, boolean includeUnchanged) {
		for (int i = 0; i < children.size(); i++) {
			DiffNode child = children.get(i);
			boolean last = i == children.size() - 1;
			String branch = last ? "└── " : "├── ";
			String childIndent = indent + (last ? "    " : "│   ");

			if (!includeUnchanged && child.change() == ChangeType.UNCHANGED) {
				out.append(indent).append(branch).append("  ").append(child.label())
						.append("  (").append(child.subtreeSize()).append(" unchanged)\n");
				continue;
			}

			out.append(indent).append(branch).append(markerFor(child.change())).append(" ").append(child.label()).append("\n");
			renderChildren(child.children(), childIndent, out, includeUnchanged);
		}
	}

	private static String markerFor(ChangeType change) {
		return switch (change) {
			case ADDED -> "+";
			case REMOVED -> "-";
			case MODIFIED -> "~";
			case UNCHANGED -> " ";
		};
	}

}
