/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.Util;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Link;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Links;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.ui.HTMLPrinter;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springframework.ide.eclipse.boot.util.version.VersionParser;
import org.springframework.ide.eclipse.boot.util.version.VersionRange;

/**
 * Utility class containing static method for creating HTML help/tooltip content for dependency
 *
 * @author Alex Boyko
 *
 */
public class DependencyTooltipContent {

	private static final String UNIT; // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=155993
	static {
		UNIT= Util.isMac() ? "px" : "pt";   //$NON-NLS-1$//$NON-NLS-2$
	}

	/**
	 * Generates HTML documentation for a dependency
	 *
	 * @param dep Dependency model
	 * @param variables Map containing values for variables
	 * @return HTML as a <code>string</code>
	 */
	public static String generateHtmlDocumentation(Dependency dep, Map<String, String> variables) {
		StringBuilder buffer = new StringBuilder();
		HTMLPrinter.insertPageProlog(buffer, 0, String.join("\n", styles()));

		Links links = dep.getLinks();
		// HTML Tooltip initial size calculations can't calculate <br/> height normally, can't tell new line jump from empty line
		// Therefore use <p> if links are present, otherwise just plain text
		if (links == null) {
			buffer.append(HTMLPrinter.convertToHTMLContent(dep.getDescription()));
		} else {
			buffer.append("<p>");
			buffer.append(HTMLPrinter.convertToHTMLContent(dep.getDescription()));
			buffer.append("</p>");
			if (links.getGuides() != null) {
				String bullets = linkBullets(links.getGuides(), variables);
				if (!bullets.isEmpty()) {
					HTMLPrinter.addSmallHeader(buffer, "Guides");
					HTMLPrinter.startBulletList(buffer);
					buffer.append(bullets);
					HTMLPrinter.endBulletList(buffer);
				}
			}
			if (links.getReferences() != null) {
				String bullets = linkBullets(links.getReferences(), variables);
				if (!bullets.isEmpty()) {
					HTMLPrinter.addSmallHeader(buffer, "References");
					HTMLPrinter.startBulletList(buffer);
					buffer.append(bullets);
					HTMLPrinter.endBulletList(buffer);
				}
			}
		}

		HTMLPrinter.addPageEpilog(buffer);

		return buffer.toString();
	}

	private static String linkBullets(Link[] links, Map<String, String> variableValues) {
		StringBuilder bullets = new StringBuilder();
		for (Link link : links) {
			String href = link.getHref();
			if (link.isTemplated()) {
				if (href != null) {
					try {
						href = InitializrServiceSpec.substituteTemplateVariables(href, variableValues);
					} catch (CoreException e) {
						BootWizardActivator.log(e);
						href = null;
					}
				}
			}
			if (href != null && isHttpOrHttps(href)) {
				StringBuilder sb = new StringBuilder();
				sb.append("<a href=\"");
				sb.append(HTMLPrinter.convertToHTMLContent(href));
				sb.append("\">");
				if (link.getTitle() != null) {
					sb.append(HTMLPrinter.convertToHTMLContent(link.getTitle()));
				} else {
					if (href.contains("docs.spring.io")) {
						sb.append(HTMLPrinter.convertToHTMLContent("Spring Boot Reference Doc"));
					} else {
						sb.append(HTMLPrinter.convertToHTMLContent(href));
					}
				}
				sb.append("</a>");
				HTMLPrinter.addBullet(bullets, sb.toString());
			}
		}
		return bullets.toString();
	}

	private static String[] styles() {
		StringBuilder mainStyle = new StringBuilder();
		FontData fontData = JFaceResources.getDialogFontDescriptor().getFontData()[0];
		boolean bold= (fontData.getStyle() & SWT.BOLD) != 0;
		boolean italic= (fontData.getStyle() & SWT.ITALIC) != 0;
		String size= Integer.toString(Math.max(5, fontData.getHeight())) + UNIT;
		String family= "'" + fontData.getName() + "',sans-serif"; //$NON-NLS-1$ //$NON-NLS-2$
		mainStyle.append("font-size:");
		mainStyle.append(size);
		mainStyle.append(';');
		mainStyle.append("font-family:");
		mainStyle.append(family);
		mainStyle.append(';');
		mainStyle.append("font-weight:");
		mainStyle.append(bold ? "bold" : "normal");
		mainStyle.append(';');
		mainStyle.append("font-style:");
		mainStyle.append(italic ? "italic" : "normal");
		mainStyle.append(';');

		return new String[] {
			"html 		{" + mainStyle + "}",
			"body, h4, h5, h6, p, table, td, caption, th, ul, ol, dl, li, dd, dt { font-size: 1em; }",
			"h5         { margin-top: 0px; margin-bottom: 0px; }",
			"p 			{ margin-top: 1em; margin-bottom: 1em; }",
			"ul	        { margin-top: 0px; margin-bottom: 0em; margin-left: 1em; padding-left: 1em; }",
			"li	        { margin-top: 0px; margin-bottom: 0px; }"
		};
	}

	public static String generateRequirements(Dependency dep) {
		if (dep != null) {
			String rangeString = dep.getVersionRange();
			if (StringUtil.hasText(rangeString)) { //check to avoid logging errors on empty string or null
				try {
					VersionRange versionRange = VersionParser.DEFAULT.parseRange(rangeString);
					Version l = versionRange.getLowerVersion();
					Version r = versionRange.getHigherVersion();
					if (l != null && r != null) {
						return "Requires Spring Boot "+
								rangeText(l, getLeftChar(versionRange)) + " and " + rangeText(r, getRightChar(versionRange));
					} else if (l != null) {
						return "Requires Spring Boot "+
								rangeText(l, getLeftChar(versionRange));
					} else if (r != null) {
						return "Requires Spring Boot "+
								rangeText(r, getRightChar(versionRange));
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
		}
		return null;
	}

	private static char getRightChar(VersionRange versionRange) {
		return versionRange.isHigherInclusive() ? ']' : ')';
	}

	private static char getLeftChar(VersionRange versionRange) {
		return versionRange.isLowerInclusive() ? '[' : '(';
	}

	private static String rangeText(Version range, char type) {
		return rangeTypeText(type) + range.toString();
	}

	private static String rangeTypeText(char type) {
		switch (type) {
		case '[':
			return ">=";
		case '(':
			return ">";
		case ']':
			return "<=";
		case ')':
			return "<";
		default:
			//Shouldn't happen... but anyhow.
			return "??";
		}
	}

	private static boolean isHttpOrHttps(String href) {
	    try {
	        String scheme = new URI(href).getScheme();
	        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
	    } catch (URISyntaxException e) {
	        return false;
	    }
	}

}
