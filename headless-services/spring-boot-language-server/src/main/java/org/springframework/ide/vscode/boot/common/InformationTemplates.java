/*******************************************************************************
 * Copyright (c) 2016, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.common;

import static org.springframework.ide.vscode.commons.util.Renderables.bold;
import static org.springframework.ide.vscode.commons.util.Renderables.concat;
import static org.springframework.ide.vscode.commons.util.Renderables.italic;
import static org.springframework.ide.vscode.commons.util.Renderables.lineBreak;
import static org.springframework.ide.vscode.commons.util.Renderables.link;
import static org.springframework.ide.vscode.commons.util.Renderables.paragraph;
import static org.springframework.ide.vscode.commons.util.Renderables.strikeThrough;
import static org.springframework.ide.vscode.commons.util.Renderables.text;

import java.util.Collection;

import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.StringUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class InformationTemplates {

	private static final String ARROW = "\u2192";

	public static Renderable createHover(PropertyInfo info) {
		Deprecation deprecation = createDeprecation(info);
		Renderable description = info.getDescription() == null ? null : text(info.getDescription());
		return InformationTemplates.createHover(info.getId(), info.getType(), info.getDefaultValue(), description, deprecation);
	}

	public static Renderable createCompletionDocumentation(PropertyInfo info) {
		Deprecation deprecation = createDeprecation(info);
		Renderable description = info.getDescription() == null ? null : text(info.getDescription());
		return InformationTemplates.createCompletionDocumentation(info.getId(), description, info.getDefaultValue(), deprecation);
	}

	public static Renderable createHover(String id, String type, Object defaultValue, Renderable description, Deprecation deprecation) {
		Builder<Renderable> renderableBuilder = ImmutableList.builder();

		renderId(renderableBuilder, id, deprecation);

		if (type == null) {
			type = Object.class.getName();
		}
		renderableBuilder.add(lineBreak());
		actionLink(renderableBuilder, type);

		String deflt = formatDefaultValue(defaultValue);
		if (deflt != null) {
			renderableBuilder.add(lineBreak());
			renderableBuilder.add(lineBreak());
			defaultValueRenderable(renderableBuilder, deflt);
		}

		if (deprecation != null) {
			renderableBuilder.add(lineBreak());
			renderableBuilder.add(lineBreak());
			depreactionRenderable(renderableBuilder, deprecation);
		}

		if (description != null) {
			renderableBuilder.add(lineBreak());
			descriptionRenderable(renderableBuilder, description);
		}

		return concat(renderableBuilder.build());
	}

	public static Renderable createCompletionDocumentation(String id, Renderable description, Object defaultValue, Deprecation deprecation) {
		Builder<Renderable> renderableBuilder = ImmutableList.builder();

		boolean idInserted = false;
		if (deprecation != null && deprecation.getReplacement() != null) {
			renderId(renderableBuilder, id, deprecation);
			idInserted = true;
		}

		if (description != null) {
			if (idInserted) {
				renderableBuilder.add(lineBreak());
			}
			descriptionRenderable(renderableBuilder, description);
		}

		String deflt = formatDefaultValue(defaultValue);
		if (deflt != null) {
			if (idInserted || description != null) {
				renderableBuilder.add(lineBreak());
			}
			defaultValueRenderable(renderableBuilder, deflt);
		}

		if (deprecation != null) {
			if (idInserted || description != null) {
				renderableBuilder.add(lineBreak());
			}
			depreactionRenderable(renderableBuilder, deprecation);
		}


		ImmutableList<Renderable> pieces = renderableBuilder.build();

		// Special case when there is no description, default value and deprecation data -> return `null` documentation.
		if (pieces.size() == 1 && pieces.get(0) == Renderables.NO_DESCRIPTION) {
			pieces = ImmutableList.of();
		}
		return pieces.isEmpty() ? null : concat(pieces);

	}

	private static Deprecation createDeprecation(PropertyInfo info) {
		Deprecation deprecation = null;
		if (info.isDeprecated()) {
			deprecation = new Deprecation();
			deprecation.setReason(info.getDeprecationReason());
			deprecation.setReplacement(info.getDeprecationReplacement());
		}
		return deprecation;
	}

	private static void renderId(Builder<Renderable> renderableBuilder, String id, Deprecation deprecation) {
		if (deprecation == null) {
			renderableBuilder.add(bold(text(id)));
		} else {
			renderableBuilder.add(strikeThrough(text(id)));
			String replacement = deprecation.getReplacement();
			if (StringUtil.hasText(replacement)) {
				renderableBuilder.add(text(" " + ARROW + " " + replacement));
			}
		}
	}

	private static String formatDefaultValue(Object defaultValue) {
		if (defaultValue != null) {
			if (defaultValue instanceof String) {
				return (String) defaultValue;
			} else if (defaultValue instanceof Number) {
				return ((Number)defaultValue).toString();
			} else if (defaultValue instanceof Boolean) {
				return Boolean.toString((Boolean) defaultValue);
			} else if (defaultValue instanceof Object[]) {
				return StringUtil.arrayToCommaDelimitedString((Object[]) defaultValue);
			} else if (defaultValue instanceof Collection<?>) {
				return StringUtil.collectionToCommaDelimitedString((Collection<?>) defaultValue);
			} else {
				//no idea what it is but try 'toString' and hope for the best
				return defaultValue.toString();
			}
		}
		return null;
	}

	/**
	 * Creates an 'action' link and adds it to the html buffer. When the user clicks the given
	 * link then the provided runnable is to be executed.
	 */
	private static void actionLink(Builder<Renderable> renderableBuilder, String displayString) {
		renderableBuilder.add(link(displayString, "null"));
	}

	private static void defaultValueRenderable(Builder<Renderable> renderableBuilder, String defaultValue) {
		renderableBuilder.add(text("Default: "));
		renderableBuilder.add(italic(text(defaultValue)));
	}

	private static void depreactionRenderable(Builder<Renderable> renderableBuilder, Deprecation deprecation) {
		String reason = deprecation.getReason();
		if (StringUtil.hasText(reason)) {
			renderableBuilder.add(bold(text("Deprecated:")));
			renderableBuilder.add(text(" " + reason));
		} else {
			renderableBuilder.add(bold(text("Deprecated!")));
		}
	}

	private static void descriptionRenderable(Builder<Renderable> renderableBuilder, Renderable description) {
		renderableBuilder.add(paragraph(description));
	}

}
