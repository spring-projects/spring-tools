/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.test.util;

import junit.framework.Assert;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.swt.SWT;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.keyboard.Keyboard;
import org.eclipse.swtbot.swt.finder.keyboard.KeyboardFactory;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;


/**
 * Dealing with the keyboard seems to be one of the most hairy parts of SWTBot. So I'm going to
 * channel all keyboard interaction through this class. With the exception of typing simple
 * text (no special characters) which seems pretty safe.
 * <p>
 * For any other type of keystroke we will add method here that can be tweaked and then re-used.
 * Should not directly use keyboard or send keystrokes to any widgets because it is just too
 * impredictable what will work in on different machines (even if they are both Unix!)
 * @author Kris De Volder
 * @author Nieraj Singh
 * @created 2010-06-26
 */
public class TestKeyboard {
	
	private static final KeyStroke[] ENTER_KEY = { Keystrokes.CR, Keystrokes.LF };
	   //Notes:
	   //   - with SWTKeyboard must send CR+LF for some components?
	   //   - With AWT keyboard just sending LF seems to work ok.
	   //   - on build server with SWTkeyboard, send CR causes assertion failed exception in SWTBot "can't post key"
	
	private static final KeyStroke ESCAPE_KEY = Keystrokes.ESC;
	private static final KeyStroke DOWN_KEY = Keystrokes.DOWN;
	private static final KeyStroke UP_KEY = Keystrokes.UP;
	
	private Keyboard keyboard = KeyboardFactory.getAWTKeyboard();
	private SWTWorkbenchBot bot;

	public TestKeyboard(SWTWorkbenchBot bot) {
		this.bot = bot;
	}

	public void CTRL_SPACE() {
		keyboard.pressShortcut(SWT.CTRL, ' ');
	}
	public void DOWN_KEY() {
		keyboard.pressShortcut(DOWN_KEY);
	}
	public void ENTER_KEY() {
		Exception reportException = null;
		boolean ok = false;
		for (KeyStroke key : ENTER_KEY) {
			try {
				keyboard.pressShortcut(key);
				ok = true;
			}
			catch (Exception e) {
				reportException = e;
				// On build machine it seems to not like one of the keys and throws an Exception,
				// On my machine, some widgets seems to work ok getting one key only (LF?), but
				// some other widgets only respond when sent both keys in sequence.
			}
		}
		if (ok) //At least one key was accepted keep fingers crossed and hope this counts as "enter"
			return;
		else  // Definitely not good, neither key was accepted.
			throw new Error(reportException);
	}

	public void ESCAPE_KEY() {
		keyboard.pressShortcut(ESCAPE_KEY);
	}

	public void typeText(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			Assert.assertTrue("Only visible ascii no funny chars: (char)"+(int)c+" not allowed!", c >= ' ' && c <= '~');
		}
		keyboard.typeText(text);
	}

	public void UP_KEY() {
		keyboard.pressShortcut(UP_KEY);
	}

	public void ALT_SHIFT_CTRL_G() {
		if (StsTestUtil.isOnBuildSite()) {
			// Workaround, shortcut not working on build server (no matter what I try)
			// Warning: This workaround only works if Grails perspective is open!
			// Only the grails perpective has the menu being used here!
			bot.menu("Navigate").menu("Open Grails Command Prompt").click();
		}
		else
			keyboard.pressShortcut(SWT.ALT+SWT.CTRL+SWT.SHIFT, 'g');
	}
	
	public void ALT_G_M() throws Exception {
		keyboard.pressShortcut(SWT.ALT, 'g');
		keyboard.pressShortcut(KeyStroke.getInstance("M"));
	}
	
}
