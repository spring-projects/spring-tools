/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.springframework.tooling.boot.ls.views.StereotypeNode.Change;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;

/**
 * Label provider for the logical structure tree view.
 * Provides text labels for tree elements.
 * 
 * @author Alex Boyko
 */
public class StructureTreeLabelProvider extends LabelProvider implements IStyledLabelProvider {

	private static final String ADDED_COLOR = "org.springframework.tooling.boot.ls.views.structure.addedColor";
	private static final String MODIFIED_COLOR = "org.springframework.tooling.boot.ls.views.structure.modifiedColor";
	private static final String REMOVED_COLOR = "org.springframework.tooling.boot.ls.views.structure.removedColor";

	private final BooleanSupplier highlightChanges;

	/**
	 * @param highlightChanges whether the nodes that changed since the baseline are highlighted
	 */
	StructureTreeLabelProvider(BooleanSupplier highlightChanges) {
		this.highlightChanges = highlightChanges;
	}

	@Override
	public StyledString getStyledText(Object element) {
		String text = getText(element);
		if (text == null) {
			return new StyledString();
		}
		if (element instanceof StereotypeNode node && highlightChanges.getAsBoolean()) {
			Change change = node.change();
			if (change != null) {
				// the color of the label and a marker behind it: color alone is lost on some themes,
				// and for color blind users
				return new StyledString(text, colorStyler(colorOf(change)))
						.append(" " + markerOf(change), StyledString.DECORATIONS_STYLER);
			}
		}
		return new StyledString(text);
	}

	/**
	 * The tooltip of a node: what changed about it, and for a project, which baseline it is
	 * compared against.
	 */
	String getToolTipText(Object element) {
		if (!(element instanceof StereotypeNode node)) {
			return null;
		}

		List<String> lines = new ArrayList<>();
		Change change = highlightChanges.getAsBoolean() ? node.change() : null;
		if (change != null) {
			lines.add("(" + descriptionOf(change) + " since the captured baseline)");
		}
		// no baseline information at all (as opposed to "no baseline") when dependencies are
		// included - such a tree is never compared against one
		if (node.isProject() && node.hasBaselineInformation()) {
			lines.add(node.hasBaseline()
					? "Comparing against " + BaselineLabels.describe(node.comparedAgainstSha(), node.comparedAgainstMessage(), node.comparedAgainstCapturedAt())
					: "No logical structure baseline captured yet");
		}
		if (lines.isEmpty()) {
			return null;
		}
		lines.add(0, node.text());
		return String.join("\n", lines);
	}

	private static String colorOf(Change change) {
		return switch (change) {
		case ADDED -> ADDED_COLOR;
		case REMOVED -> REMOVED_COLOR;
		case MODIFIED -> MODIFIED_COLOR;
		};
	}

	private static String markerOf(Change change) {
		return switch (change) {
		case ADDED -> "+";
		case REMOVED -> "-";
		case MODIFIED -> "~";
		};
	}

	private static String descriptionOf(Change change) {
		return switch (change) {
		case ADDED -> "added";
		case REMOVED -> "removed";
		case MODIFIED -> "changed";
		};
	}

	private static Styler colorStyler(String colorId) {
		return new Styler() {
			@Override
			public void applyStyles(org.eclipse.swt.graphics.TextStyle textStyle) {
				Color color = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry().get(colorId);
				if (color != null) {
					textStyle.foreground = color;
				}
			}
		};
	}

	@Override
	public String getText(Object element) {
		if (element instanceof StereotypeNode) {
			return ((StereotypeNode) element).text();
		}
		return super.getText(element);
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof StereotypeNode) {
			String descriptor = ((StereotypeNode) element).icon();
			if (descriptor != null && !descriptor.isBlank()) {
				return BootLanguageServerPlugin.getDefault().getStereotypeImage(descriptor);
			}
		}
		return super.getImage(element);
	}
	
	
}

