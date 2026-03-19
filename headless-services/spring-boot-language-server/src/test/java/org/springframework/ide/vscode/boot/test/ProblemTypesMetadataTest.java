/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.test;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.Boot3JavaProblemType;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.SpelProblemType;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlProblemType;

public class ProblemTypesMetadataTest {
	
	@Test
	public void dataIsConsitent() throws Exception {
		//If this test fails it probably just means you have to run the main
		// method in org.springframework.ide.vscode.boot.test.ProblemTypesToJson
		// to synchronize the metadata json file with the real problem type objects in the source code.
		ProblemTypesToJson reader = new ProblemTypesToJson().read();
		reader.validate("application-properties", ApplicationPropertiesProblemType.values());
		reader.validate("application-yaml", ApplicationYamlProblemType.values());
		reader.validate("boot2", Boot2JavaProblemType.values());
		reader.validate("boot3", Boot3JavaProblemType.values());
		reader.validate("boot4", Boot4JavaProblemType.values());
		reader.validate("spring-aot", SpringAotJavaProblemType.values());
		reader.validate("spel", SpelProblemType.values());
		reader.validate("version-validation", VersionValidationProblemType.values());
	}

}
