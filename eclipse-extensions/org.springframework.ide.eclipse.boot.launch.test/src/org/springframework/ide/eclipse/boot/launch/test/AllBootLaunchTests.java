/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	BootLaunchUIModelTest.class,
	ProfileHistoryTest.class,
	BootLaunchConfigurationDelegateTest.class,
	BootLaunchShortcutTest.class
})
public class AllBootLaunchTests {

}
