/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.gradle.sample.toolingapi;

import java.io.File;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;

import sts.model.plugin.StsToolingModel;

public class ToolingApiRunner {
    public static void main(String[] args) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File("../sample"));
        ProjectConnection connection = null;
        
        try {
            connection = connector.connect();
            ModelBuilder<StsToolingModel> customModelBuilder = connection.model(StsToolingModel.class);
            customModelBuilder.withArguments("--init-script", "/Users/aboyko/Documents/runtime-STS/.metadata/.plugins/org.springframework.ide.eclipse.buildship30/gradle-plugin/init.gradle"); 
            StsToolingModel model = customModelBuilder.get();
            System.out.println("Group=" + model.group() + " artifact=" + model.artifact() + " version=" + model.version());
            assert model.version() != null;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
}