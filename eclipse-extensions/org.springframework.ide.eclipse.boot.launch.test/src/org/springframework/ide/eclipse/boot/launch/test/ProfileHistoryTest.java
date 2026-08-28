/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.test;

import org.eclipse.core.resources.IProject;

import org.springframework.ide.eclipse.boot.launch.profiles.ProfileHistory;

/**
 * @author Kris De Volder
 */
public class ProfileHistoryTest extends BootLaunchTestCase {

	private ProfileHistory history;

	protected void setUp() throws Exception {
		super.setUp();
		history = new ProfileHistory();
	}

	public void testSimpleHistory() throws Exception {
		IProject project = createGeneralProject("foo");
		assertHistory(project /*empty*/);

		history.updateHistory(project, "production");
		assertHistory(project,
				"production"
		);

		history.updateHistory(project, "development");
		assertHistory(project,
				"development",
				"production"
		);

		history.updateHistory(project, "experimental");
		assertHistory(project,
				"experimental",
				"development",
				"production"
		);
	}

	public void testMultiAddSame() throws Exception {
		IProject project = createGeneralProject("foo");
		assertHistory(project /*empty*/);

		for (int i = 0; i < 10; i++) {
			history.updateHistory(project, "foo");
			assertHistory(project, "foo");
		}
	}

	public void testExistingMovesToTop() throws Exception {
		IProject project = createGeneralProject("foo");
		assertHistory(project /*empty*/);

		history.updateHistory(project, "production");
		history.updateHistory(project, "development");
		history.updateHistory(project, "experimental");
		assertHistory(project,
				"experimental",
				"development",
				"production"
		);

		history.updateHistory(project, "production");
		assertHistory(project,
				"production",
				"experimental",
				"development"
		);

		history.updateHistory(project, "production");
		assertHistory(project,
				"production",
				"experimental",
				"development"
		);

		history.updateHistory(project, "experimental");
		assertHistory(project,
				"experimental",
				"production",
				"development"
		);
	}

	public void testOverflow() throws Exception {
		IProject project = createGeneralProject("foo");
		int maxHist = 4;
		history.setMaxHistory(maxHist);
		for (int i = 1; i <= 10; i++) {
			history.updateHistory(project, "prof-"+i);
			if (i > maxHist) {
				String[] expected = new String[maxHist];
				for (int j = 0; j < expected.length; j++) {
					expected[j] = "prof-"+(i-j);
				}
				assertHistory(project, expected);
			}
		}
	}

	private void assertHistory(IProject project, String... expecteds) {
		String[] actuals = history.getHistory(project);
		StringBuilder actualStr = new StringBuilder();
		StringBuilder expectStr = new StringBuilder();
		for (String string : actuals) {
			actualStr.append(string+"\n");
		}
		for (String string : expecteds) {
			expectStr.append(string+"\n");
		}
		assertEquals(expectStr.toString(), actualStr.toString());
	}

}
