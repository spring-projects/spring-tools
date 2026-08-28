/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.hover;

import org.eclipse.jface.internal.text.html.BrowserInformationControlInput;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.EditorsUI;
import org.springframework.ide.eclipse.editor.support.util.HtmlUtil;

/**
 * An implementation of Eclipse's {@link ITextHover} and some of its clunky 'extension'
 * interfaces, that wraps a simple 'HoverInfoProvider' (so that implementing something
 * to provide hover infos based on an editor's contents doesn't have to be horribly
 * complicated).
 *
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class HoverInfoTextHover implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {

	private HoverInfoProvider _hovers;
	private ITextHover delegate;

	/**
	 * Create a {@link HoverInfoTextHover} based on a given {@link HoverInfoProvider}. It is also possible
	 * to provide an optioanl {@link ITextHover} delegate. This 'wrapper' will try to delegate hover info
	 * requests to the delegate and combine its results with our own {@link HoverInfoProvider} in some
	 * sensible way. Note however that for this to work the delegate must produce hover informations
	 * that can be properly displayed by our own {@link HoverInformationControl}, essentially this
	 * means they must be based on {@link BrowserInformationControlInput}. If that is not the
	 * case then our {@link HoverInformationControl} will not be able to display them correctly.
	 */
	public HoverInfoTextHover(ISourceViewer sourceViewer, HoverInfoProvider hoverInfoProvider, ITextHover delegate) {
		this._hovers = hoverInfoProvider;
		this.delegate = delegate;
	}

	public IRegion getHoverRegion(ITextViewer tv, int offset) {
		//Note that we ask the 'delegate' first. This is because it handles info about
		// error markers from annotations model. And this info should 'override'
		// information about property
		if (delegate != null) {
			IRegion r = delegate.getHoverRegion(tv, offset);
			if (r != null) {
				return r;
			}
		}
		return hovers_getHoverRegion(tv.getDocument(), offset);
	}

	public String getHoverInfo(ITextViewer tv, IRegion r) {
		String s = delegate.getHoverInfo(tv, r);
		if (s != null) {
			return HtmlUtil.text2html(s);
		}
		return hovers_getHoverInfo(tv.getDocument(), r).getHtml();
	}

	@Override
	public Object getHoverInfo2(ITextViewer tv, IRegion r) {
		if (delegate instanceof ITextHoverExtension2) {
			Object it = ((ITextHoverExtension2) delegate).getHoverInfo2(tv, r);
			if (it != null) {
				if (it instanceof String) {
					return HtmlUtil.text2html((String)it);
				} else {
					return it;
				}
			}
		} else if (delegate instanceof ITextHover) {
			String it = delegate.getHoverInfo(tv, r);
			if (it != null) {
				return HtmlUtil.text2html(it);
			}
		}
		return hovers_getHoverInfo(tv.getDocument(), r);
	}

	@Override
	public IInformationControlCreator getHoverControlCreator() {
		return new HoverInformationControlCreator(EditorsUI.getTooltipAffordanceString());
	}

	private IRegion hovers_getHoverRegion(IDocument document, int offset) {
		IRegion r = _hovers.getHoverRegion(document, offset);
//		System.out.println("getHRegion("+offset+") => "+r);
		return r;
	}
	private HoverInfo hovers_getHoverInfo(IDocument document, IRegion r) {
		HoverInfo result = _hovers.getHoverInfo(document, r);
//		System.out.println("getHInfo("+r+") => "+result);
		return result;
	}


}
