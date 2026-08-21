/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.maven;

import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class LightUpgradeDependencyVersionTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.recipe(new LightUpgradeDependencyVersion("org.springframework.boot", "*", "3.4.5"));
	}

	@Test
	void artifactIdGlobRestrictsMatches() {
		rewriteRun(
			spec -> spec.recipe(new LightUpgradeDependencyVersion("org.springframework.boot", "spring-boot-starter-web", "3.4.5")),
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-web</artifactId>
							<version>3.4.1</version>
						</dependency>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-actuator</artifactId>
							<version>3.4.1</version>
						</dependency>
					</dependencies>
				</project>
				""",
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-web</artifactId>
							<version>3.4.5</version>
						</dependency>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-actuator</artifactId>
							<version>3.4.1</version>
						</dependency>
					</dependencies>
				</project>
				"""
			)
		);
	}

	@Test
	void groupIdMismatchIsNoOp() {
		rewriteRun(
			spec -> spec.recipe(new LightUpgradeDependencyVersion("org.springframework.cloud", "*", "3.4.5")),
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>3.4.1</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
				</project>
				"""
			)
		);
	}

	@Test
	void upgradesStarterParentLiteralVersion() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>3.4.1</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
				</project>
				""",
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>3.4.5</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
				</project>
				"""
			)
		);
	}

	@Test
	void upgradesImportedBomVersion() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>org.springframework.boot</groupId>
								<artifactId>spring-boot-dependencies</artifactId>
								<version>3.4.1</version>
								<type>pom</type>
								<scope>import</scope>
							</dependency>
						</dependencies>
					</dependencyManagement>
				</project>
				""",
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>org.springframework.boot</groupId>
								<artifactId>spring-boot-dependencies</artifactId>
								<version>3.4.5</version>
								<type>pom</type>
								<scope>import</scope>
							</dependency>
						</dependencies>
					</dependencyManagement>
				</project>
				"""
			)
		);
	}

	@Test
	void upgradesExplicitDependencyVersionOverride() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-web</artifactId>
							<version>3.4.1</version>
						</dependency>
					</dependencies>
				</project>
				""",
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-web</artifactId>
							<version>3.4.5</version>
						</dependency>
					</dependencies>
				</project>
				"""
			)
		);
	}

	@Test
	void upgradesSameFilePropertyIndirection() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>${spring-boot.version}</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<properties>
						<spring-boot.version>3.4.1</spring-boot.version>
					</properties>
				</project>
				""",
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>${spring-boot.version}</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<properties>
						<spring-boot.version>3.4.5</spring-boot.version>
					</properties>
				</project>
				"""
			)
		);
	}

	@Test
	void propertyDefinedElsewhereIsOutOfScopeAndSkipped() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>${spring-boot.version}</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
				</project>
				"""
			)
		);
	}

	@Test
	void noSpringBootReferencesIsNoOp() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
				</project>
				"""
			)
		);
	}

	@Test
	void upgradesBothParentAndImportedBomInSamePom() {
		rewriteRun(
			xml(
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>3.4.1</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>org.springframework.boot</groupId>
								<artifactId>spring-boot-dependencies</artifactId>
								<version>3.4.1</version>
								<type>pom</type>
								<scope>import</scope>
							</dependency>
						</dependencies>
					</dependencyManagement>
				</project>
				""",
				"""
				<project>
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>3.4.5</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>demo</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>org.springframework.boot</groupId>
								<artifactId>spring-boot-dependencies</artifactId>
								<version>3.4.5</version>
								<type>pom</type>
								<scope>import</scope>
							</dependency>
						</dependencies>
					</dependencyManagement>
				</project>
				"""
			)
		);
	}

}
