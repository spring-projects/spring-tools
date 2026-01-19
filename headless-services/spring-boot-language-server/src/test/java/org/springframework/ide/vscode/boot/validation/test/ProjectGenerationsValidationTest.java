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
package org.springframework.ide.vscode.boot.validation.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.RestTemplateFactory;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.HoverTestConf;
import org.springframework.ide.vscode.boot.validation.generations.SpringIoProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.Link;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.boot.validation.generations.json.SpringProject;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(HoverTestConf.class)
public class ProjectGenerationsValidationTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private RestTemplateFactory restTemplateFactory;
	@Autowired private BootJavaConfig config;

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;


	@BeforeEach
	public void setup() throws Exception {
		harness.useProject(projects.mavenProject("empty-boot-1.3.0-app"));
		harness.intialize(null);
	}


    @Test
    void testProjectsInfoFromSpringIo() throws Exception {
        SpringProjectsProvider cache = new SpringIoProjectsProvider(config, restTemplateFactory, harness.getServer().getProgressService(), harness.getServer().getMessageService(), -1);

        SpringProject project = cache.getProject("spring-boot");
        assertNotNull(project);
        assertEquals("Spring Boot", project.getName());
        assertEquals("spring-boot", project.getSlug());
        Link generationsUrl = project.get_links().getGenerations();
        assertNotNull(generationsUrl);
        assertEquals("https://api.spring.io/projects/spring-boot/generations", generationsUrl.getHref());

        project = cache.getProject("spring-integration");
        assertNotNull(project);
        assertEquals("Spring Integration", project.getName());
        assertEquals("spring-integration", project.getSlug());
        generationsUrl = project.get_links().getGenerations();
        assertNotNull(generationsUrl);
        assertEquals("https://api.spring.io/projects/spring-integration/generations", generationsUrl.getHref());

        // Enable when generations is  actually available  from  spring.io
        ResolvedSpringProject resolvedProject = cache.getProject("spring-boot");
        assertNotNull(resolvedProject);
		List<Generation> generations = resolvedProject.getGenerations();
		assertNotNull(generations);
		assertTrue(generations.size() > 5);
    }

    @Test
    void testLinkedGenerationsFromSpringIo() throws Exception {
        SpringProjectsProvider cache = new SpringIoProjectsProvider(config, restTemplateFactory, harness.getServer().getProgressService(), harness.getServer().getMessageService(), -1);

        ResolvedSpringProject project = cache.getProject("spring-integration");
		List<Generation> generations = project.getGenerations();
		assertNotNull(generations);
		assertTrue(generations.size() > 5);
		
		Generation specificGeneration = generations.stream().filter(generation -> generation.getName().equals("5.5.x")).findAny().get();
		Map<String, String[]> linkedGenerations = specificGeneration.getLinkedGenerations();
		assertEquals(1, linkedGenerations.size());
		
		String[] linkedBootGenerations = linkedGenerations.get("spring-boot");
		assertEquals(3, linkedBootGenerations.length);
		assertEquals("2.5.x", linkedBootGenerations[0]);
		assertEquals("2.6.x", linkedBootGenerations[1]);
		assertEquals("2.7.x", linkedBootGenerations[2]);
    }

    @Test
    void testGenerationsFromSample() throws Exception {

        SampleProjectsProvider provider = new SampleProjectsProvider();

        ResolvedSpringProject project = provider.getProject("spring-boot");
        assertNotNull(project);

        List<Generation> genList = project.getGenerations();

        assertNotNull(genList);
        assertTrue(genList.size() > 0);

        Generation generation = genList.get(0);
        assertEquals("1.3.x", generation.getName());
        assertEquals("2019-01-01", generation.getInitialReleaseDate());
        assertEquals("2020-01-01", generation.getOssSupportEndDate());
        assertEquals("2021-01-01", generation.getCommercialSupportEndDate());
    }
    
}
