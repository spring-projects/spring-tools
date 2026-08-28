/*******************************************************************************
 * Copyright (c) 2014-2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.internal.text.html.HTMLPrinter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.osgi.framework.Bundle;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;

/**
 * Helper class to make it a little easier to create simple html page (for display in
 * {@link DefaultInformationControl} or {@link BrowserInformationControl}
 *
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class HtmlBuffer {

	private StringBuilder buffer = new StringBuilder();
	private boolean epilogAdded = false; //to ensure only added once.
	private static String fgCSSStyles;

	public HtmlBuffer() {
		this.buffer = new StringBuilder();
	}

	/**
	 * Append text, applies escaping to the text as needed.
	 */
	public void text(String text) {
		raw(HTMLPrinter.convertToHTMLContent(text));
	}

	/**
	 * Append 'raw' text. Doesn't apply any escaping.
	 */
	public void raw(String rawText) {
		if (epilogAdded) {
			throw new IllegalStateException("Can not append more text after epilog was added");
		}
		buffer.append(rawText);
	}

	/**
	 * Append text, applies urlencoding to the text.
	 */
	public void url(String string) {
		try {
			raw(URLEncoder.encode(string, "utf8"));
		} catch (UnsupportedEncodingException e) {
			EditorSupportActivator.log(e);
		}
	}


	public String toString() {
		if (!epilogAdded && buffer.length() > 0) {
			epilogAdded = true;

			ColorRegistry registry = JFaceResources.getColorRegistry();
			RGB fgRGB = registry.getRGB("org.eclipse.jdt.ui.Javadoc.foregroundColor"); //$NON-NLS-1$
			RGB bgRGB= registry.getRGB("org.eclipse.jdt.ui.Javadoc.backgroundColor"); //$NON-NLS-1$

			HTMLPrinter.insertPageProlog(buffer, 0, fgRGB, bgRGB, getCSSStyles());
			HTMLPrinter.addPageEpilog(buffer);
		}
		return buffer.toString();
	}



	/**
	 * Note: copied from org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal.getCSSStyles()
	 * Returns the style information for displaying HTML (Javadoc) content.
	 *
	 * @return the CSS styles
	 * @since 3.3
	 */
	public static String getCSSStyles() {
		if (fgCSSStyles == null) {
			Bundle bundle= Platform.getBundle(JavaPlugin.getPluginId());
			URL url= bundle.getEntry("/JavadocHoverStyleSheet.css"); //$NON-NLS-1$
			if (url != null) {
				BufferedReader reader= null;
				try {
					url= FileLocator.toFileURL(url);
					reader= new BufferedReader(new InputStreamReader(url.openStream()));
					StringBuffer buffer= new StringBuffer(200);
					String line= reader.readLine();
					while (line != null) {
						buffer.append(line);
						buffer.append('\n');
						line= reader.readLine();
					}
					fgCSSStyles= buffer.toString();
				} catch (IOException ex) {
					JavaPlugin.log(ex);
				} finally {
					try {
						if (reader != null)
							reader.close();
					} catch (IOException e) {
					}
				}

			}
		}
		String css= fgCSSStyles;
		if (css != null) {
			FontData fontData= JFaceResources.getFontRegistry().getFontData(PreferenceConstants.APPEARANCE_JAVADOC_FONT)[0];
			css= HTMLPrinter.convertTopLevelFont(css, fontData);
		}
		return css;
	}

	public void hline() {
		raw("<hr>");
	}

	public void p(String string) {
		raw("<p>");
		text(string);
		raw("</p>");
	}

	public void snippet(HtmlSnippet snippet) {
		snippet.render(this);
	}

	public void bold(String string) {
		raw("<b>");
		text(string);
		raw("</b>");
	}

	public void href(String url, String displayText) {
		raw("<a href=\"" + url + "\">");
		text(displayText);
		raw("</a>");
	}


}
