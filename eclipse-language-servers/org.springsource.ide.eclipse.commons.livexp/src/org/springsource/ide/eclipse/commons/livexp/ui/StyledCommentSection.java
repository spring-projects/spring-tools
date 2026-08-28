/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * An attempt at creating Comment section that allows styling the text
 * in the comment using simple tags like <b> and <i>
 * 
 * At present the following tags are supported:
 *   <b>bold</b>
 * 
 * Other kinds of style tags not yet supported.
 * 
 * @author Kris De Volder
 */
public class StyledCommentSection extends WizardPageSection {

	private String htmlText;
	private Font fBoldFont;
	
	/**
	 * Helper for splitting a String into tokens. By looking for 'html tags'.
	 * This is an extremely dumb implementation which assumes only tags 
	 * are "<b>" and "</b>"
	 */
	class TokenIzer implements Iterator<String> {
		
		String input;
		int pos;
		
		Pattern tagPattern = Pattern.compile("\\<(\\/)?b\\>");
		private Matcher matcher;
		
		
		public TokenIzer(String input) {
			this.input = input;
			this.matcher = tagPattern.matcher(input);
		}
		
		@Override
		public boolean hasNext() {
			return pos < input.length();
		}
		@Override
		public String next() {
			String token;
			Assert.isLegal(hasNext());
			if (matcher.find(pos)) {
				int nextTag = matcher.start();
				if (nextTag == pos) {
					//tag found at current inpit position
					token = input.substring(nextTag, matcher.end());
					pos = matcher.end();
				} else {
					//found a tag but not at current position.
					token = input.substring(pos, nextTag);
					pos = nextTag;
				}
			} else {
				//no tag found until end of input
				token = input.substring(pos);
				pos = input.length();
			}
			return token;
		}
		
		@Override
		public void remove() {
			throw new Error("Not supported");
		}
	}

	public StyledCommentSection(IPageWithSections owner, String htmlText) {
		super(owner);
		this.htmlText = htmlText;
	}
	
	private Styler createBoldStyler(final Font font) {
		return new Styler() {
			@Override
			public void applyStyles(TextStyle textStyle) {
				textStyle.font= getBoldFont(font);
			}
		};
	}
	
	/**
	 * Create the bold variant of the currently used font.
	 * 
	 * @return the bold font
	 * @since 3.5
	 */
	private Font getBoldFont(Font font) {
		if (fBoldFont == null) {
			FontData[] data= font.getFontData();
			for (int i= 0; i < data.length; i++) {
				data[i].setStyle(SWT.BOLD);
			}
			fBoldFont= new Font(font.getDevice(), data);
		}
		return fBoldFont;
	}
	
	@Override
	public void createContents(Composite page) {
		StyledText label = new StyledText(page, SWT.READ_ONLY|SWT.WRAP) {
			/**
			 * Prevents this control for getting keyboard focus because, since it is readonly
			 * it makes no sense to give it keyboard focus.
			 * <p>
			 * By doing this next widget will be given focus instead. That's usually what you want.
			 */
			@Override
			public boolean setFocus() {
				return false;
			};
			@Override
			public boolean forceFocus() {
				return false;
			}
		};
		label.setBackground(label.getParent().getBackground());
		StyledString content = new StyledString();
		parse(htmlText, content, label.getFont());
		label.setText(content.getString());
		label.setStyleRanges(content.getStyleRanges());
		label.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (fBoldFont != null) {
					fBoldFont.dispose();
				}
			}
		});
		GridDataFactory
			.fillDefaults()
			.grab(true, false)
			.hint(UIConstants.DIALOG_WIDTH_HINT, SWT.DEFAULT)
			.applyTo(label);

	}

	private void parse(String htmlText, StyledString content, Font baseFont) {
		TokenIzer input = new TokenIzer(htmlText);
		while (input.hasNext()) {
			String token = input.next();
			if ("<b>".equals(token)) {
				while (input.hasNext()) {
					String boldedThing = input.next();
					if (boldedThing.equals("</b>")) {
						break;
					} else {
						content.append(boldedThing, createBoldStyler(baseFont));
					}
				}
			} else {
				content.append(token);
			}
		}
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return Validator.OK;
	}

}
