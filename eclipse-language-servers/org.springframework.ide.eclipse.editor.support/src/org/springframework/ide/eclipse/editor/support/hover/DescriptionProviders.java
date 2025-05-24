/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.hover;

import static org.springframework.ide.eclipse.editor.support.util.HtmlSnippet.italic;
import static org.springframework.ide.eclipse.editor.support.util.HtmlSnippet.raw;
import static org.springframework.ide.eclipse.editor.support.util.HtmlSnippet.text;

import java.io.InputStream;
import java.util.function.Supplier;

import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springsource.ide.eclipse.commons.frameworks.core.util.IOUtil;

/**
 * Static methods and convenience constants for creating some 'description Suppliers'.
 *
 * @author Kris De Volder
 */
public class DescriptionProviders {

	public static final Supplier<HtmlSnippet> NO_DESCRIPTION = snippet(italic(text("no description")));

	public static Supplier<HtmlSnippet> snippet(final HtmlSnippet snippet) {
		return new Supplier<HtmlSnippet>() {
			@Override
			public String toString() {
				return snippet.toString();
			}
			@Override
			public HtmlSnippet get() {
				return snippet;
			}
		};
	}

	public static Supplier<HtmlSnippet> fromClasspath(final Class<?> klass, final String resourcePath) {
		return new Supplier<HtmlSnippet>() {
			@Override
			public String toString() {
				return "HtmlSnippetFromClassPth(class="+klass.getSimpleName()+", "+resourcePath+")";
			}
			@Override
			public HtmlSnippet get() {
				try {
					InputStream stream = klass.getResourceAsStream(resourcePath);
					if (stream!=null) {
						return raw(IOUtil.toString(stream));
					}
				} catch (Exception e) {
					EditorSupportActivator.log(e);
				}
				return NO_DESCRIPTION.get();
			}
		};
	}
}
