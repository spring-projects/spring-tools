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
package org.springsource.ide.eclipse.commons.ui;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.internal.text.html.HTML2TextReader;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.util.Util;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.OpenWindowListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.springsource.ide.eclipse.commons.internal.ui.UiPlugin;

/**
 * HTML Tooltip. Can be applied to any control. Limitations to the HTML styles
 * are all due to tooltip's initial size calculations which is done with a help
 * of {@link TextLayout}. Margins, paddings fond size should have whole
 * <code>em</code> numbers. Different family and height fonts on the same
 * tooltip also increase probability of initial size miscalculations
 *
 * @author Alex Boyko
 *
 */
@SuppressWarnings("restriction")
public class HtmlTooltip extends ToolTip {

	static {
		//This funky code forces HTMLPrinter to initialize and then waits for some async stuff it does
		// on the ui thread (i.e. loading colors for tooltips). If we don't do this,
		// it gets used before being fully initialized causing the very first tooltip to have the
		// wrong color.
		//
		// See bug: https://www.pivotaltracker.com/story/show/142483141
		HTMLPrinter.insertPageProlog(new StringBuilder(), 0);
		Display.getDefault().syncExec(() -> {});
	}

	private static final int MIN_WIDTH= 50;

	private static final int MIN_HEIGHT= 10;

	private static final Pattern FONT_SIZE_PATTERN = Pattern.compile("html\\s*\\{.*(?:\\s|;)?font-size:\\s*(\\d+)(pt|px|em)?\\;?.*\\}");
	private static final Pattern FONT_STYLE_PATTERN = Pattern.compile("html\\s*\\{.*(?:\\s|;)?font-style:\\s*(\\w+)\\;?.*\\}");
	private static final Pattern FONT_FAMILY_PATTERN = Pattern.compile("html\\s*\\{.*(?:\\s|;)?font-family:\\s*(.+?);.*\\}");
	private static final Pattern FONT_WEIGHT_PATTERN = Pattern.compile("html\\s*\\{.*(?:\\s|;)?font-weight:\\s*(\\w+)\\;?.*\\}");

	private Supplier<String> html;
	private Point maxSizeConstraints = new Point(SWT.DEFAULT, SWT.DEFAULT);

	public HtmlTooltip(Control control) {
		super(control, ToolTip.NO_RECREATE, false);
		setHideOnMouseDown(false);
		setShift(new Point(1,1));
	}

	@Override
	protected Composite createToolTipContentArea(Event event, Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());
		String htmlContent = html.get();

		Browser browser;
		try {
			browser = new Browser(composite, SWT.NONE);

			Color foreground = getInformationViewerForegroundColor(parent.getDisplay());
			Color background = getInformationViewerBackgroundColor(parent.getDisplay());
			browser.setForeground(foreground);
			browser.setBackground(background);
			browser.setJavascriptEnabled(false);

			browser.addOpenWindowListener(new OpenWindowListener() {
				@Override
				public void open(WindowEvent event) {
					event.required= true; // Cancel opening of new windows
				}
			});

			// Replace browser's built-in context menu with none
			browser.setMenu(new Menu(browser.getShell(), SWT.NONE));

			Point size = computeSizeHint(browser, htmlContent);
			browser.setLayoutData(GridDataFactory.swtDefaults().hint(size.x, size.y).create());

			browser.setText(htmlContent);

			// Add after HTML content is set to avoid event fired after the content is set
			browser.addLocationListener(new LocationAdapter() {
				@Override
				public void changing(LocationEvent event) {
					super.changing(event);
					try {
						if (event.location!=null) {
							if (event.location.startsWith("about:")) { //about:blank url
								//ignore
							} else {
								event.doit = false;
								openUrl(event.location);
							}
						}
					} catch (Exception e) {
						UiPlugin.log(e);
					}
				}
			});
		} catch (SWTError e) {
			System.out.println("Could not instantiate Browser: " + e.getMessage());
			throw new RuntimeException(e);
		}
		return composite;
	}

	private void openUrl(String url) {
		//Careful calling this in response to a location change event directly makes SWT browser
		//on Linux deadlock and permanently breaks embedded browser widget until workbench restart.
		//This probably a bug in SWT browser widget.
		new UIJob("open url") {

			@Override
			public IStatus runInUIThread(IProgressMonitor arg0) {
				try {
					hide();
					PlatformUI.getWorkbench().getBrowserSupport().createBrowser(null).openURL(new URL(url));
				}
				catch (Exception e) {
					UiPlugin.log(e);
				}
				return Status.OK_STATUS;
			}
		}
		.schedule();
	}

	@Override
	protected Object getToolTipArea(Event event) {
		Object r = super.getToolTipArea(event);
		return r;
	}

	public void setHtml(Supplier<String> html) {
		this.html = html;
	}

	public void setMaxSize(int maxWidth, int maxHeight) {
		this.maxSizeConstraints = maxWidth > 0 && maxHeight > 0 ? new Point(maxWidth, maxHeight) : new Point(SWT.DEFAULT, SWT.DEFAULT);
	}

	private Point computeSizeHint(Browser browser, String html) {
		TextLayout fTextLayout= new TextLayout(browser.getDisplay());

		// Initialize fonts
		Font defaultFont= JFaceResources.getFont(JFaceResources.DIALOG_FONT);
		FontData fd = defaultFont.getFontData()[0];

		// Try getting font from the HTML style tag
		String family = fd.getName();
		int size = fd.getHeight();
		int style = SWT.NONE;

		Matcher matcher = FONT_FAMILY_PATTERN.matcher(html);
		if (matcher.find()) {
			family = matcher.group(1);
		}
		matcher = FONT_SIZE_PATTERN.matcher(html);
		if (matcher.find()) {
			try {
				size = Integer.valueOf(matcher.group(1));
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		matcher = FONT_STYLE_PATTERN.matcher(html);
		if (matcher.find()) {
			if ("italic".equalsIgnoreCase(matcher.group(1))) {
				style |= SWT.ITALIC;
			}
		}
		matcher = FONT_WEIGHT_PATTERN.matcher(html);
		if (matcher.find()) {
			if ("bold".equalsIgnoreCase(matcher.group(1))) {
				style |= SWT.BOLD;
			}
		}

		Font font = null;
		Font boldFont = null;

		try {
			font = new Font(defaultFont.getDevice(), new FontData(family, size, style));

			fTextLayout.setFont(font);
			fTextLayout.setWidth(-1);

			boldFont = new Font(font.getDevice(), new FontData(family, size, style | SWT.BOLD));
			TextStyle fBoldStyle= new TextStyle(boldFont, null, null);

			// Compute and set tab width
			fTextLayout.setText("    "); //$NON-NLS-1$
			int tabWidth= fTextLayout.getBounds().width;
			fTextLayout.setTabs(new int[] { tabWidth });
			fTextLayout.setText(""); //$NON-NLS-1$

			Rectangle trim= /*browser.getParent().computeTrim(0, 0, 0, 0)*/new Rectangle(0,0,35,20);
			int height= trim.height;

			//FIXME: The HTML2TextReader does not render <p> like a browser.
			// Instead of inserting an empty line, it just adds a single line break.
			// Furthermore, the indentation of <dl><dd> elements is too small (e.g with a long @see line)
			TextPresentation presentation= new TextPresentation();
			String text;
			try (HTML2TextReader reader= new HTML2TextReader(new StringReader(html), presentation)) {
				text= reader.getString();
			} catch (IOException e) {
				text= ""; //$NON-NLS-1$
			}

			fTextLayout.setText(text);
			fTextLayout.setWidth(maxSizeConstraints == null || maxSizeConstraints.x < trim.width? SWT.DEFAULT : maxSizeConstraints.x - trim.width);
			Iterator<StyleRange> iter= presentation.getAllStyleRangeIterator();
			while (iter.hasNext()) {
				StyleRange sr= iter.next();
				if (sr.fontStyle == SWT.BOLD) {
					fTextLayout.setStyle(fBoldStyle, sr.start, sr.start + sr.length - 1);
				}
			}

			Rectangle bounds= fTextLayout.getBounds(); // does not return minimum width, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=217446
			int lineCount= fTextLayout.getLineCount();
			int textWidth= 0;
			for (int i= 0; i < lineCount; i++) {
				Rectangle rect= fTextLayout.getLineBounds(i);
				int lineWidth= rect.x + rect.width;
//				if (i == 0)
//					lineWidth+= fInput.getLeadingImageWidth();
				textWidth= Math.max(textWidth, lineWidth);
			}
			bounds.width= textWidth;
			fTextLayout.setText(""); //$NON-NLS-1$

			int minWidth= bounds.width;
			height= height + bounds.height;

			// Add some air to accommodate for different browser renderings
//			minWidth+= 15;
//			height+= 20;


			// Apply max size constraints
			if (maxSizeConstraints != null) {
				if (maxSizeConstraints.x != SWT.DEFAULT) {
					minWidth= Math.min(maxSizeConstraints.x, minWidth + trim.width);
				}
				if (maxSizeConstraints.y != SWT.DEFAULT) {
					height= Math.min(maxSizeConstraints.y, height);
				}
			}

			// Ensure minimal size
			int width= Math.max(MIN_WIDTH, minWidth);
			height= Math.max(MIN_HEIGHT, height);

			fTextLayout.dispose();
			font.dispose();
			boldFont.dispose();

			return new Point(width, height);
		} finally {
			if (font != null) {
				font.dispose();
			}
			if (boldFont != null) {
				boldFont.dispose();
			}
		}
	}

	///////////////////////////////////////
	// The two methods below copied from JFaceColors class. This is because they don't exist in Eclipse 4.6
	// causing compilation problems if referred to directly.

	public static Color getInformationViewerBackgroundColor(Display display) {
		if (Util.isWin32() || Util.isCocoa()) {
			// Technically COLOR_INFO_* should only be used for tooltips. But on
			// Windows/Cocoa COLOR_INFO_* gives info viewers/hovers a
			// yellow background which is very suitable for information
			// presentation.
			return display.getSystemColor(SWT.COLOR_INFO_BACKGROUND);
		}

		// Technically, COLOR_LIST_* is not the best system color for this
		// because it is only supposed to be used for Tree/List controls. But at
		// the moment COLOR_TEXT_* is not implemented, so this should work for
		// now. See Bug 508612.
		return display.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
	}

	public static Color getInformationViewerForegroundColor(Display display) {
		if (Util.isWin32() || Util.isCocoa()) {
			// Technically COLOR_INFO_* should only be used for tooltips. But on
			// Windows/Cocoa COLOR_INFO_* gives info viewers/hovers a
			// yellow background which is very suitable for information
			// presentation.
			return display.getSystemColor(SWT.COLOR_INFO_FOREGROUND);
		}

		// Technically, COLOR_LIST_* is not the best system color for this
		// because it is only supposed to be used for Tree/List controls. But at
		// the moment COLOR_TEXT_* is not implemented, so this should work for
		// now. See Bug 508612.
		return display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
	}


}
