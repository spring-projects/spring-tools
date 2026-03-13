/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.javadoc;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.springframework.tooling.ls.eclipse.commons.LanguageServerCommonsActivator;

import com.google.common.io.CharStreams;
import com.overzealous.remark.Options;
import com.overzealous.remark.Options.Tables;
import com.overzealous.remark.Remark;

/* Copied from JDT LS */
/**
 * Converts JavaDoc tags into Markdown equivalent.
 *
 * @author Fred Bricon
 */
public class JavaDoc2MarkdownConverter extends AbstractJavaDocConverter {

	private static Remark remark;

	static {
		Options options = new Options();
		options.tables = Tables.CONVERT_TO_CODE_BLOCK;
		options.hardwraps = true;
		options.inlineLinks = true;
		options.autoLinks = true;
		options.reverseHtmlSmartPunctuation = true;
		remark = new Remark(options);
		//Stop remark from stripping file and jdt protocols in an href
		try {
			Field cleanerField = Remark.class.getDeclaredField("cleaner");
			cleanerField.setAccessible(true);

			Cleaner c = (Cleaner) cleanerField.get(remark);

			Field whitelistField = Cleaner.class.getDeclaredField("whitelist");
			whitelistField.setAccessible(true);

			Whitelist w = (Whitelist) whitelistField.get(c);

			w.addProtocols("a", "href", "file", "jdt");
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			LanguageServerCommonsActivator.logError(e, "Unable to modify jsoup to include file and jdt protocols");
		}
	}

	public JavaDoc2MarkdownConverter(Reader reader) {
		super(reader);
	}

	public JavaDoc2MarkdownConverter(String javadoc) {
		super(javadoc);
	}

	@Override
	String convert(String rawHtml) {
		return remark.convert(rawHtml);
	}

	public static String getMarkdownContentReader(IJavaElement element) {
		try {
			String rawHtml = JavadocContentAccess2.getHTMLContent(element, true);
			Reader markdownReader = new JavaDoc2MarkdownConverter(rawHtml).getAsReader();
			return markdownReader == null ? null : CharStreams.toString(markdownReader);
		} catch (IOException | CoreException e) {
			// ignore
		}
		return null;
	}


}
