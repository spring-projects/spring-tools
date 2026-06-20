/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ClasspathTests {

    @Test
    void testDependencyVersionCalculation2() throws Exception {
        assertEquals("1.2.3",                 Classpath.getDependencyVersion("spring-boot-1.2.3.jar"));
        assertEquals("1.2.3-RELEASE",         Classpath.getDependencyVersion("spring-boot-1.2.3-RELEASE.jar"));
        assertEquals("1.2.3.RELEASE",         Classpath.getDependencyVersion("spring-boot-1.2.3.RELEASE.jar"));
        assertEquals("1.2.3.BUILD-SNAPSHOT",  Classpath.getDependencyVersion("spring-boot-1.2.3.BUILD-SNAPSHOT.jar"));
        assertEquals("1.2.3.BUILD-SNAPSHOT",  Classpath.getDependencyVersion("spring-boot-actuator-1.2.3.BUILD-SNAPSHOT.jar"));
        assertNull(Classpath.getDependencyVersion("some-library.jar"));
    }

    @Test
    void testDependencyNameExtraction() throws Exception {
        assertEquals("spring-boot",          Classpath.getDependencyName("spring-boot-1.2.3.jar"));
        assertEquals("spring-boot",          Classpath.getDependencyName("spring-boot-1.2.3-RELEASE.jar"));
        assertEquals("spring-boot",          Classpath.getDependencyName("spring-boot-1.2.3.RELEASE.jar"));
        assertEquals("spring-boot",          Classpath.getDependencyName("spring-boot-1.2.3.BUILD-SNAPSHOT.jar"));
        assertEquals("spring-boot-actuator", Classpath.getDependencyName("spring-boot-actuator-1.2.3.BUILD-SNAPSHOT.jar"));
        assertEquals("commons-lang3",        Classpath.getDependencyName("commons-lang3-3.12.0.jar"));
        assertEquals("jackson-databind",     Classpath.getDependencyName("jackson-databind-2.13.4.jar"));
        assertEquals("some-library",         Classpath.getDependencyName("some-library.jar"));
        assertEquals("some-library",         Classpath.getDependencyName("some-library"));
    }

}
