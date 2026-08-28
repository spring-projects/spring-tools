/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.hover;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ide.eclipse.editor.support.util.HtmlBuffer;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypedProperty;

/**
 * Nicely formatted hover info for a {@link YTypedProperty}
 *
 * @author Kris De Volder
 */
public class YPropertyHoverInfo extends HoverInfo {

	private String contextProperty;
//	private YType contextType;
	private YTypedProperty prop;

	public YPropertyHoverInfo(String contextProperty, YType contextType, YTypedProperty prop) {
		this.contextProperty = contextProperty;
//		this.contextType = contextType;
		this.prop = prop;
	}

	private String getName() {
		return prop.getName();
	}

	private YType getType() {
		return prop.getType();
	}

	@Override
	protected String renderAsHtml() {
		HtmlBuffer html = new HtmlBuffer();
		if (!StringUtils.isBlank(contextProperty)) {
			html.text(contextProperty);
			html.text(".");
		}
		html.raw("<b>");
		html.text(getName());
		html.raw("</b>");
		html.raw("<br>");

		YType type = getType();
		if (type != null) {
			actionLink(html, type.toString(), null);
		}

		HtmlSnippet description = getDescription();
		if (description != null) {
			html.raw("<br><br>");
			html.snippet(description);
		}

		return html.toString();
	}

	protected HtmlSnippet getDescription() {
		return prop.getDescription();
	}
}
