/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

public class Stylers implements Disposable {

	public static final Styler NULL = new Styler() {
		public void applyStyles(TextStyle textStyle) {
		}
		public String toString() {
			return "Styler.NULL";
		};
	};
	private Font baseFont; // borrowed
	private Font boldFont = null; //owned (must dispose!)
	private Font italicFont = null; //owner (must dispose!)
	/**
	 * The 'Stylers' requires baseFont to render styles using bold
	 * properly. If baseFont is null, then styler created will try to
	 * render things as well as it can, but it will not do 'bold'.
	 */
	public Stylers(Font baseFont) {
		this.baseFont = baseFont;
	}
	
	public Styler hyperlink() {
		return new Styler() {
			@Override
			public void applyStyles(TextStyle textStyle) {
				Color color = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry()
						.get(JFacePreferences.HYPERLINK_COLOR);
				color(color).applyStyles(textStyle);
				textStyle.underline = true;
			}
		};
	}

	public Styler tag() {
		return new Styler() {
			@Override
			public void applyStyles(TextStyle textStyle) {
				textStyle.foreground = getSystemColor(SWT.COLOR_DARK_CYAN);
				//textStyle.rise = 2; //Why?? it makes mixed text with this style and others togther look really ugly!
				textStyle.underline = true;
				textStyle.font = getBoldFont();
			}
		};
	}

	public Styler tagBrackets() {
		return new Styler() {
			@Override
			public void applyStyles(TextStyle textStyle) {
				textStyle.foreground = getSystemColor(SWT.COLOR_DARK_CYAN);
				//textStyle.rise = 2; //Why?? it makes mixed text with this style and others togther look really ugly!
//				textStyle.underline = true;
//				textStyle.font = getBoldFont();
			}
		};
	}

	private Color getSystemColor(int colorCode) {
		return Display.getDefault().getSystemColor(colorCode);
	}

	public Styler bold() {
		return new Styler() {
			@Override
			public void applyStyles(TextStyle textStyle) {
				textStyle.font = getBoldFont();
			}
		};
	}

	private synchronized Font getBoldFont() {
		//If baseFont is null, this Stylers is a bit 'handicapped' and won't
		// be capable of doing 'bold' styling.
		if (boldFont == null && baseFont != null) {
			FontData[] data= baseFont.getFontData();
			for (int i= 0; i < data.length; i++) {
				data[i].setStyle(SWT.BOLD);
			}
			boldFont = new Font(baseFont.getDevice(), data);
		}
		return boldFont;
	}

	public synchronized Font getItalicFont() {
		//If baseFont is null, this Stylers is a bit 'handicapped' and won't
		// be capable of doing 'bold' styling.
		if (italicFont == null && baseFont != null) {
			FontData[] data= baseFont.getFontData();
			for (int i= 0; i < data.length; i++) {
				data[i].setStyle(SWT.ITALIC);
			}
			italicFont = new Font(baseFont.getDevice(), data);
		}
		return italicFont;
	}

	@Override
	public void dispose() {
		if (boldFont != null) {
			boldFont.dispose();
			boldFont = null;
		}
		if (italicFont != null) {
			italicFont.dispose();
			italicFont = null;
		}
	}

	public Styler darkGrey() {
		return color(SWT.COLOR_DARK_GRAY);
	}

	public Styler darkGreen() {
		return color(SWT.COLOR_DARK_GREEN);
	}

	public Styler darkBlue() {
		return color(SWT.COLOR_DARK_BLUE);
	}

	public Styler grey() {
		return color(SWT.COLOR_GRAY);
	}

	public Styler red() {
		return color(SWT.COLOR_RED);
	}

	/**
	 * Don't make this public. Instead define additional methods. That way it will be easier if we
	 * need to refactor specific color styles later to somehting user-defined.
	 */
	private Styler color(int colorCode) {
		final Color color = getSystemColor(colorCode);
		return color(color);
	}
	
	public Styler color(Color color) {
		return new Styler() {
			public void applyStyles(TextStyle textStyle) {
				textStyle.foreground = color;
			}
		};
	}

	public Styler italic() {
		return new Styler() {
			public void applyStyles(TextStyle textStyle) {
				textStyle.font = getItalicFont();
			}
		};
	}

	public Styler italicColoured(int colorCode) {
		return new Styler() {
			public void applyStyles(TextStyle textStyle) {
				final Color color = getSystemColor(colorCode);
				textStyle.font = getItalicFont();
				textStyle.foreground = color;
			}
		};
	}
	
	public Styler italicColoured(Color color) {
		return new Styler() {
			public void applyStyles(TextStyle textStyle) {
				textStyle.font = getItalicFont();
				textStyle.foreground = color;
			}
		};
	}
	
	public Styler boldColoured(int foregroundColour) {
		final Color foreGround = getSystemColor(foregroundColour);
		return new Styler() {
			public void applyStyles(TextStyle textStyle) {
				textStyle.font = getBoldFont();
				textStyle.foreground = foreGround;
			}
		};
	}

}
