/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * Simple Spring Boot application used by STS regression tests. The application
 * prints out some info onto System.out and exits.
 * <p>
 * The test harness uses the output to verify whether launch parameters passed via
 * STS BootLaunchconfiguration produce the expected result.
 *
 * @author Kris De Volder
 */
@SpringBootApplication
public class DumpInfoApplication implements CommandLineRunner {

	public static String[] INTERESTING_PROPERTIES = {
		"debug",
		"zor",
		"foo",
		"bar",
		"com.sun.management.jmxremote.port"
	};

    public static void main(String[] args) {
        SpringApplication.run(DumpInfoApplication.class, args);
    }

    @Autowired
    Environment properties;

	@Override
	public void run(String... args) throws Exception {
		System.out.println(">>>properties");
		for (String prop : INTERESTING_PROPERTIES) {
			System.out.println(prop+"="+render(properties.getProperty(prop)));
		}
		System.out.println("<<<properties");
	}

	private String render(String propertyValue) {
		if (propertyValue == null) {
			return "null";
		} else {
			return "'"+propertyValue+"'";
		}
	}
}

