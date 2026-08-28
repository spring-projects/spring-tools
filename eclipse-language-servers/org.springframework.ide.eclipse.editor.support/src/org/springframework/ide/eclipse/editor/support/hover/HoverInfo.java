/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.hover;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput;
import org.springframework.ide.eclipse.editor.support.util.HtmlBuffer;
import org.springframework.ide.eclipse.editor.support.util.HtmlUtil;

import com.google.common.collect.ImmutableList;

@SuppressWarnings("restriction")
public abstract class HoverInfo extends BrowserInformationControlInput {

	/**
	 * Fake host name that is used in 'action link' urls so that we can
	 * recognize them as such.
	 */
	private static final String ACTION_HOST = "action";

	/**
	 * Registry for 'action link ids' so that we can map clicked action link
	 * to a Runnable to execute it.
	 */
	private Map<String, Runnable> actions = null; //only created if used

	/**
	 * Cached html representation of this info. So the info is computed only once.
	 */
	private String html;

	public HoverInfo() {
		super(null);
	}

	@Override
	public Object getInputElement() {
		return this;
	}

	@Override
	public String getInputName() {
		return "";
	}

	/**
	 * Fetches an html-formatted represetation of this element. Subclasses should not
	 * implement this. Instead they should override the 'renderAsHtml' method.
	 */
	public final String getHtml() {
		if (html == null) {
			html = renderAsHtml();
		}
		return html;
	}

	/**
	 * Subclass must implement this method to format its info as html. Clients should
	 * not call this method directly, instead they should call 'getHtml' which will return
	 * a cached copy of what this method computes.
	 */
	protected abstract String renderAsHtml();

	/**
	 * IJavaElements associated with the hover target. Used by 'open declaration'
	 * action.
	 */
	public List<IJavaElement> getJavaElements() {
		return ImmutableList.of();
	}

	public static HoverInfo withText(final String plainText) {
		if (plainText != null) {
			return new HoverInfo() {

				@Override
				public String renderAsHtml() {
					return HtmlUtil.text2html(plainText);
				}

			};
		}
		return null;
	}

	/**
	 * Called by the brower information control when a link is about to be followed. This method
	 * can then decide to handle the link (if it corresponds to an actionId registered with the
	 * object. If the link was handled then the linkHanlder should return true to indicate this.
	 */
	public boolean handleActionLink(String link) {
		String actionId = getActionLinkTarget(link);
		if (actionId != null) {
			Runnable action = getAction(actionId);
			if (action != null) {
				action.run();
			}
			return true;
		}
		return false;
	}

	private Runnable getAction(String actionId) {
		if (actions != null) {
			return actions.get(actionId);
		}
		return null;
	}

	/**
	 * Creates an 'action' link and adds it to the html buffer. When the user clicks the given
	 * link then the provided runnable is to be executed.
	 */
	public void actionLink(HtmlBuffer html, String displayString, Runnable runnable) {
		String actionId = registerAction(runnable);
		html.raw("<a href=\"http://"+ACTION_HOST+"/");
		html.url(actionId);
		html.raw("\">");
		html.text(displayString);
		html.raw("</a>");
	}

	private synchronized String registerAction(Runnable runnable) {
		if (actions == null) {
			actions = new HashMap<>();
		}
		String actionId = ""+actions.size();
		actions.put(actionId, runnable);
		return actionId;
	}

	/**
	 * Extract the 'action id' from a url location, if it represents an action link. Otherwise
	 * returns null.
	 */
	private String getActionLinkTarget(String location) {
		try {
			if (location != null) {
				URI uri = new URI(location);
				if (ACTION_HOST.equals(uri.getHost())) {
					String path = URLDecoder.decode(uri.getPath(), "utf8");
					while (path.startsWith("/")) {
						path = path.substring(1);
					}
					return path;
				}
			}
		} catch (Exception e) {
			//ignore
		}
		return null;
	}

}
