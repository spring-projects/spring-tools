/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.mcp.ComponentAnalysisMcpTools;
import org.springframework.ide.vscode.boot.mcp.ComponentAnalysisMcpTools.BeanUsageInfo;
import org.springframework.ide.vscode.boot.mcp.ProjectInformation;
import org.springframework.ide.vscode.boot.mcp.RequestMappingMcpTools;
import org.springframework.ide.vscode.boot.mcp.RequestMappingMcpTools.RequestMappingInfo;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.languageserver.util.SettingsStore;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

import com.google.gson.JsonParser;

/**
 * The standalone language server, as the Claude Code plugin starts it (no LSP transport, the
 * project directory given as a property, the file watcher on), on a Kotlin project in the shape
 * that start.spring.io generates for Maven: the Kotlin sources in {@code src/main/kotlin}, which
 * the POM names as its source directory, and the Kotlin Maven plugin. The project has to be found,
 * its Kotlin files indexed, and changes on disk followed, all of it seen through the MCP tools the
 * plugin offers.
 *
 * @author Martin Lippert
 */
class StandaloneKotlinProjectTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	/** the first run of Gradle downloads its distribution and the plugins */
	private static final Duration GRADLE_TIMEOUT = Duration.ofMinutes(5);

	@TempDir Path workspace;

	private ConfigurableApplicationContext context;

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	void kotlinProjectIsFoundIndexedAndFollowed() throws Exception {
		Path project = createKotlinProject("demo");
		enableKotlinSupport();

		startServer();

		ProjectInformation projectInformation = context.getBean(ProjectInformation.class);
		RequestMappingMcpTools requestMappings = context.getBean(RequestMappingMcpTools.class);
		ComponentAnalysisMcpTools components = context.getBean(ComponentAnalysisMcpTools.class);
		SpringMetamodelIndex springIndex = context.getBean(SpringMetamodelIndex.class);

		// the project is found, as a Spring Boot project
		await(() -> projectInformation.getProjectList().stream().anyMatch(p -> p.projectName().equals("demo") && p.isSpringBootProject()));

		// the beans of the Kotlin sources are indexed, with their injection points
		await(() -> beanNames(springIndex).containsAll(List.of("demoApplication", "greetingController", "greetingService", "appProperties")));
		Bean controller = springIndex.getBeansWithName("demo", "greetingController")[0];
		assertThat(controller.getType()).isEqualTo("com.example.demo.web.GreetingController");
		assertThat(controller.getLocation().getUri()).endsWith("/src/main/kotlin/com/example/demo/web/GreetingController.kt");

		List<BeanUsageInfo> serviceUsage = components.getBeanUsageInfo("demo", "greetingService");
		assertThat(serviceUsage).hasSize(1);
		assertThat(serviceUsage.get(0).injectionPoints())
				.anyMatch(injection -> injection.injectedIntoBean().equals("greetingController") && injection.injectionName().equals("service"));

		// the request mappings, through the tool
		List<RequestMappingInfo> mappings = requestMappings.getRequestMappings("demo");
		assertThat(mappings).extracting(RequestMappingInfo::path).containsExactly("/greeting");
		assertThat(mappings.get(0).httpMethods()).containsExactly("GET");
		assertThat(mappings.get(0).sourceFile()).endsWith("GreetingController.kt");

		// a Kotlin file that is created on disk is picked up by the file watcher
		Path hello = project.resolve("src/main/kotlin/com/example/demo/web/HelloController.kt");
		Files.writeString(hello, """
				package com.example.demo.web

				import org.springframework.web.bind.annotation.GetMapping
				import org.springframework.web.bind.annotation.RestController

				@RestController
				class HelloController {

					@GetMapping("/hello")
					fun hello() = "hello"
				}
				""");
		await(() -> paths(requestMappings).contains("/hello"));

		// changed
		Files.writeString(hello, Files.readString(hello).replace("\"/hello\"", "\"/hi\""));
		await(() -> paths(requestMappings).contains("/hi") && !paths(requestMappings).contains("/hello"));

		// and deleted
		Files.delete(hello);
		await(() -> !paths(requestMappings).contains("/hi") && !beanNames(springIndex).contains("helloController"));
		assertThat(paths(requestMappings)).containsExactly("/greeting");
	}

	/**
	 * A Kotlin project as start.spring.io generates it for Gradle with the Kotlin DSL, which is
	 * what it generates for Kotlin by default, with a second Kotlin source directory that the build
	 * declares. The Gradle classpath reports source folders with Kotlin files as ones without Java
	 * content, so the Kotlin sources are found through the source folders of the project, not
	 * through its Java source folders.
	 * <p>
	 * This runs Gradle through the tooling API, which downloads the Gradle distribution of the
	 * wrapper and the Gradle plugins on first use.
	 */
	@Test
	void gradleKotlinProjectWithTheSourceDirectoriesOfItsBuild() throws Exception {
		Path project = workspace.resolve("demo");
		write(project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
		write(project.resolve("build.gradle.kts"), """
				plugins {
					kotlin("jvm") version "2.3.21"
					kotlin("plugin.spring") version "2.3.21"
					id("org.springframework.boot") version "4.0.2"
					id("io.spring.dependency-management") version "1.1.7"
				}

				group = "com.example"
				version = "0.0.1-SNAPSHOT"

				repositories {
					mavenCentral()
				}

				dependencies {
					implementation("org.springframework.boot:spring-boot-starter-web")
					implementation("org.jetbrains.kotlin:kotlin-reflect")
				}

				sourceSets {
					main {
						kotlin.srcDir("src/generated/kotlin")
					}
				}
				""");
		write(project.resolve("gradle/wrapper/gradle-wrapper.properties"), """
				distributionBase=GRADLE_USER_HOME
				distributionPath=wrapper/dists
				distributionUrl=https\\://services.gradle.org/distributions/gradle-9.9.2-bin.zip
				zipStoreBase=GRADLE_USER_HOME
				zipStorePath=wrapper/dists
				""");
		write(project.resolve("src/main/kotlin/com/example/demo/DemoApplication.kt"), """
				package com.example.demo

				import org.springframework.boot.autoconfigure.SpringBootApplication

				@SpringBootApplication
				class DemoApplication
				""");
		write(project.resolve("src/generated/kotlin/com/example/demo/GeneratedController.kt"), """
				package com.example.demo

				import org.springframework.web.bind.annotation.GetMapping
				import org.springframework.web.bind.annotation.RestController

				@RestController
				class GeneratedController {

					@GetMapping("/generated")
					fun generated() = "generated"
				}
				""");
		enableKotlinSupport();

		startServer();

		ProjectInformation projectInformation = context.getBean(ProjectInformation.class);
		RequestMappingMcpTools requestMappings = context.getBean(RequestMappingMcpTools.class);
		SpringMetamodelIndex springIndex = context.getBean(SpringMetamodelIndex.class);

		await(GRADLE_TIMEOUT, () -> projectInformation.getProjectList().stream().anyMatch(p -> p.projectName().equals("demo") && p.isSpringBootProject()));
		await(() -> beanNames(springIndex).containsAll(List.of("demoApplication", "generatedController")));
		assertThat(paths(requestMappings)).containsExactly("/generated");
	}

	/**
	 * Kotlin sources are only indexed when the Kotlin support is switched on in the settings
	 * ({@code boot-java.kotlin-support.on}, off by default). The project itself is found anyway,
	 * and switching the setting on indexes the Kotlin sources.
	 */
	@Test
	void kotlinSourcesAreIndexedOnlyWithTheSetting() throws Exception {
		createKotlinProject("demo");

		startServer();

		ProjectInformation projectInformation = context.getBean(ProjectInformation.class);
		SpringMetamodelIndex springIndex = context.getBean(SpringMetamodelIndex.class);

		await(() -> projectInformation.getProjectList().stream().anyMatch(p -> p.projectName().equals("demo")));
		context.getBean(SpringSymbolIndex.class).waitOperation().get();

		assertThat(beanNames(springIndex)).isEmpty();

		// the store the settings files go into
		context.getBean(SettingsStore.class).update(new Settings(JsonParser.parseString("""
				{ "boot-java": { "kotlin-support": { "on": true } } }
				""")));
		await(() -> beanNames(springIndex).contains("greetingController"));
	}

	// ---- the server

	/** the server with the properties the launcher of the plugin gives it, apart from the MCP transport */
	private void startServer() {
		context = new SpringApplicationBuilder(BootLanguageServerBootApp.class, StandaloneProjectServiceConfig.class)
				.properties(
						"languageserver.enabled=false",
						"languageserver.standalone.project-dir=" + workspace.toAbsolutePath(),
						"languageserver.standalone.filewatcher.enabled=true",
						"languageserver.standalone.filewatcher.debounce-ms=100",
						"spring.ai.mcp.server.stdio=false")
				.run();
	}

	/** the setting, in the way a user of the plugin gives it to the language server */
	private void enableKotlinSupport() throws Exception {
		Path settings = workspace.resolve(".claude/spring-tools.json");
		Files.createDirectories(settings.getParent());
		Files.writeString(settings, """
				{ "boot-java": { "kotlin-support": { "on": true } } }
				""");
	}

	private static List<String> beanNames(SpringMetamodelIndex springIndex) {
		return List.of(springIndex.getBeansOfProject("demo")).stream().map(Bean::getName).toList();
	}

	private static List<String> paths(RequestMappingMcpTools requestMappings) throws Exception {
		return requestMappings.getRequestMappings("demo").stream().map(RequestMappingInfo::path).toList();
	}

	private static void await(Callable<Boolean> condition) throws Exception {
		await(TIMEOUT, condition);
	}

	private static void await(Duration timeout, Callable<Boolean> condition) throws Exception {
		long end = System.currentTimeMillis() + timeout.toMillis();
		while (true) {
			try {
				if (condition.call()) {
					return;
				}
			}
			catch (Exception e) {
				// not there yet (for example the project is not known yet)
			}
			if (System.currentTimeMillis() > end) {
				assertThat(condition.call()).as("condition reached within " + timeout).isTrue();
				return;
			}
			Thread.sleep(100);
		}
	}

	// ---- the project

	/** a Kotlin project as start.spring.io generates it for Maven, with a controller, a service and configuration properties */
	private Path createKotlinProject(String name) throws Exception {
		Path project = workspace.resolve(name);

		write(project.resolve("pom.xml"), """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
					xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
					<modelVersion>4.0.0</modelVersion>
					<parent>
						<groupId>org.springframework.boot</groupId>
						<artifactId>spring-boot-starter-parent</artifactId>
						<version>4.0.2</version>
						<relativePath/>
					</parent>
					<groupId>com.example</groupId>
					<artifactId>%s</artifactId>
					<version>0.0.1-SNAPSHOT</version>
					<properties>
						<java.version>21</java.version>
						<kotlin.version>2.3.21</kotlin.version>
					</properties>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-starter-web</artifactId>
						</dependency>
						<dependency>
							<groupId>org.jetbrains.kotlin</groupId>
							<artifactId>kotlin-reflect</artifactId>
						</dependency>
						<dependency>
							<groupId>org.jetbrains.kotlin</groupId>
							<artifactId>kotlin-stdlib</artifactId>
						</dependency>
					</dependencies>
					<build>
						<sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
						<testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
						<plugins>
							<plugin>
								<groupId>org.springframework.boot</groupId>
								<artifactId>spring-boot-maven-plugin</artifactId>
							</plugin>
							<plugin>
								<groupId>org.jetbrains.kotlin</groupId>
								<artifactId>kotlin-maven-plugin</artifactId>
								<configuration>
									<args>
										<arg>-Xjsr305=strict</arg>
									</args>
									<compilerPlugins>
										<plugin>spring</plugin>
									</compilerPlugins>
								</configuration>
								<dependencies>
									<dependency>
										<groupId>org.jetbrains.kotlin</groupId>
										<artifactId>kotlin-maven-allopen</artifactId>
										<version>${kotlin.version}</version>
									</dependency>
								</dependencies>
							</plugin>
						</plugins>
					</build>
				</project>
				""".formatted(name));

		Path sources = project.resolve("src/main/kotlin/com/example/demo");
		write(sources.resolve("DemoApplication.kt"), """
				package com.example.demo

				import org.springframework.boot.autoconfigure.SpringBootApplication
				import org.springframework.boot.context.properties.EnableConfigurationProperties
				import org.springframework.boot.runApplication

				@SpringBootApplication
				@EnableConfigurationProperties(AppProperties::class)
				class DemoApplication

				fun main(args: Array<String>) {
					runApplication<DemoApplication>(*args)
				}
				""");
		write(sources.resolve("AppProperties.kt"), """
				package com.example.demo

				import org.springframework.boot.context.properties.ConfigurationProperties

				@ConfigurationProperties("app")
				data class AppProperties(val greeting: String = "Hello")
				""");
		write(sources.resolve("service/GreetingService.kt"), """
				package com.example.demo.service

				import com.example.demo.AppProperties
				import org.springframework.stereotype.Service

				@Service
				class GreetingService(private val properties: AppProperties) {

					fun greet(name: String): String = "${properties.greeting}, $name"
				}
				""");
		write(sources.resolve("web/GreetingController.kt"), """
				package com.example.demo.web

				import com.example.demo.service.GreetingService
				import org.springframework.web.bind.annotation.GetMapping
				import org.springframework.web.bind.annotation.RequestParam
				import org.springframework.web.bind.annotation.RestController

				@RestController
				class GreetingController(private val service: GreetingService) {

					@GetMapping("/greeting")
					fun greeting(@RequestParam name: String): String = service.greet(name)
				}
				""");
		write(project.resolve("src/main/resources/application.properties"), "spring.application.name=" + name + "\n");

		return project;
	}

	private static void write(Path file, String content) throws Exception {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

}
