/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
		//SpringBootProjectTests.class: removed for now
		//  functionality for this is now tested via EditStartersModelTest, and
		//  other tests that use the functionalities provided by ISpringBootProject
//		EditStartersModelTest.class,
		EnableDisableBootDevtoolsTest.class,
		NewSpringBootWizardModelTest.class,
		NewSpringBootWizardTest.class,
		InitializrDependencySpecTest.class,
		SpringBootValidationTest.class,
		GSGWizardModelTest.class,
		InitializrFactoryModelTest.class,
		DependencyTooltipContentTest.class,
		BootPropertyTesterTest.class,
		AddStartersModelTest.class,
		NameGeneratorTest.class
})
public class AllSpringBootTests {

	public static final String PLUGIN_ID = "org.springframework.ide.eclipse.boot.test";

}
