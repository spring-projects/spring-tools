/*******************************************************************************
 * Copyright (c) 2022, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.common;

import java.util.EnumSet;

import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory.Toggle;

import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory.Toggle.Option.*;

public class SpringProblemCategories {
	
	public static final ProblemCategory BOOT_2 = new ProblemCategory("boot2", "Boot 2.x Best Practices & Optimizations",
			new Toggle("Enablement", EnumSet.allOf(Toggle.Option.class), AUTO, "boot-java.validation.java.boot2"));
	
	public static final ProblemCategory BOOT_3 = new ProblemCategory("boot3", "Boot 3.x Best Practices & Optimizations", 
			new Toggle("Enablement", EnumSet.allOf(Toggle.Option.class), AUTO, "boot-java.validation.java.boot3"));
	
	public static final ProblemCategory BOOT_4 = new ProblemCategory("boot4", "Boot 4.x Best Practices & Optimizations", 
			new Toggle("Enablement", EnumSet.allOf(Toggle.Option.class), AUTO, "boot-java.validation.java.boot4"));
	
	public static final ProblemCategory SPRING_AOT = new ProblemCategory("spring-aot", "AOT Optimizations", 
			new Toggle("Enablement", EnumSet.of(OFF, ON), OFF, "boot-java.validation.java.spring-aot"));
	
	public static final ProblemCategory PROPERTIES = new ProblemCategory("application-properties", "Property Config Files", null);
	
	public static final ProblemCategory YAML = new ProblemCategory("application-yaml", "YAML Config Files", null);
	
	public static final ProblemCategory SPEL = new ProblemCategory("spel", "SpEL Expressions",
			new Toggle("Enablement", EnumSet.of(OFF, ON), ON, "boot-java.validation.spel.on"));
	
	public static final ProblemCategory VERSION_VALIDATION = new ProblemCategory("version-validation", "Versions and Support Ranges",
			new Toggle("Enablement", EnumSet.of(OFF, ON), ON, "boot-java.validation.java.version-validation"));

	public static final ProblemCategory DATA_QUERY = new ProblemCategory("data-query", "Data Queries",
			new Toggle("Enablement", EnumSet.of(OFF, ON), ON, "boot-java.validation.data-query"));
	
	public static final ProblemCategory CRON = new ProblemCategory("cron", "CRON Expressions",
			new Toggle("Enablement", EnumSet.of(OFF, ON), ON, "boot-java.validation.cron"));

	public static final ProblemCategory SPRING_AI = new ProblemCategory("spring-ai", "Spring AI",
			new Toggle("Enablement", EnumSet.of(OFF, ON), ON, "boot-java.validation.spring-ai"));

}
