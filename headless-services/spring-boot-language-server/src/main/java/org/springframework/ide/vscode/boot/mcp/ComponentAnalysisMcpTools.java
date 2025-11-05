/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.stereotype.Component;

/**
 * MCP tools for analyzing Spring components and dependency injection
 * 
 * @author Martin Lippert
 */
@Component
public class ComponentAnalysisMcpTools {

	private static final Logger logger = LoggerFactory.getLogger(ComponentAnalysisMcpTools.class);
	
	private final JavaProjectFinder projectFinder;
	private final SpringMetamodelIndex springIndex;

	public ComponentAnalysisMcpTools(JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex) {
		this.projectFinder = projectFinder;
		this.springIndex = springIndex;
	}

	/**
	 * Record representing component information for MCP clients
	 */
	public static record ComponentInfo(
			String name,
			String type,
			List<String> annotations,
			String sourceFile,
			int startLine,
			int startColumn,
			int endLine,
			int endColumn
	) {}

	/**
	 * Record representing bean usage information including where it's defined and injected
	 */
	public static record BeanUsageInfo(
			String beanName,
			String beanType,
			ComponentInfo definition,
			List<InjectionPointInfo> injectionPoints
	) {}

	/**
	 * Record representing an injection point
	 */
	public static record InjectionPointInfo(
			String injectedIntoBean,
			String injectedIntoBeanType,
			String injectionName,
			String injectionType,
			String sourceFile,
			int startLine,
			int startColumn,
			int endLine,
			int endColumn
	) {}

	@Tool(description = """
			Get detailed information about a specific bean, including where it's defined and all places where it's injected.
			This helps understand bean usage and dependencies across the application.
			Returns information for all beans with the given name (there may be multiple beans with the same name in different contexts).
			""")
	public List<BeanUsageInfo> getBeanUsageInfo(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName,
			@ToolParam(description = "the name of the bean to analyze") String beanName) throws Exception {
		
		logger.info("get bean usage info for: {} in project: {}", beanName, projectName);
		
		IJavaProject project = getProject(projectName);
		
		// Find all beans with this name (there may be multiple)
		Bean[] beans = springIndex.getBeansWithName(project.getElementName(), beanName);
		
		if (beans.length == 0) {
			throw new Exception("bean with name '" + beanName + "' not found in project: " + projectName);
		}
		
		List<BeanUsageInfo> usageInfos = new ArrayList<>();
		
		// Create usage info for each bean found
		for (Bean bean : beans) {
			ComponentInfo definition = createComponentInfo(bean);
			
			// Find all injection points for this bean
			List<InjectionPointInfo> injectionPoints = findInjectionPoints(project.getElementName(), bean);
			
			BeanUsageInfo usageInfo = new BeanUsageInfo(
					bean.getName(),
					bean.getType(),
					definition,
					injectionPoints
			);
			
			usageInfos.add(usageInfo);
		}
		
		logger.info("found {} beans with name {} in project: {}", beans.length, beanName, projectName);
		
		return usageInfos;
	}

	@Tool(description = """
			Find all beans that match a specific type.
			This is useful for finding all implementations of an interface or all beans of a certain class.
			""")
	public List<ComponentInfo> findBeansByType(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName,
			@ToolParam(description = "the fully qualified type name to search for") String typeName) throws Exception {
		
		logger.info("find beans by type: {} for project: {}", typeName, projectName);
		
		IJavaProject project = getProject(projectName);
		
		// Find beans with matching type
		Bean[] beans = springIndex.getBeansWithType(project.getElementName(), typeName);
		
		List<ComponentInfo> components = Arrays.stream(beans)
				.map(this::createComponentInfo)
				.collect(Collectors.toList());
		
		logger.info("found {} beans of type {} for project: {}", components.size(), typeName, projectName);
		
		return components;
	}

	//
	// Helper methods
	//

	private ComponentInfo createComponentInfo(Bean bean) {
		// Convert AnnotationMetadata[] to List<String> of annotation types
		List<String> annotationTypes = bean.getAnnotations() != null 
				? Arrays.stream(bean.getAnnotations())
						.map(annotation -> annotation.getAnnotationType())
						.collect(Collectors.toList())
				: List.of();
		
		return new ComponentInfo(
				bean.getName(),
				bean.getType(),
				annotationTypes,
				bean.getLocation() != null ? bean.getLocation().getUri() : null,
				bean.getLocation() != null ? bean.getLocation().getRange().getStart().getLine() : 0,
				bean.getLocation() != null ? bean.getLocation().getRange().getStart().getCharacter() : 0,
				bean.getLocation() != null ? bean.getLocation().getRange().getEnd().getLine() : 0,
				bean.getLocation() != null ? bean.getLocation().getRange().getEnd().getCharacter() : 0
		);
	}

	private List<InjectionPointInfo> findInjectionPoints(String projectName, Bean targetBean) {
		List<InjectionPointInfo> injectionPoints = new ArrayList<>();
		
		// Get all beans in the project
		Bean[] allBeans = springIndex.getBeansOfProject(projectName);
		
		// Look through all beans to find injection points where this bean could be injected
		for (Bean bean : allBeans) {
			if (bean.getInjectionPoints() != null) {
				for (InjectionPoint injectionPoint : bean.getInjectionPoints()) {
					// Check if the target bean is type-compatible with the injection point
					if (targetBean.isTypeCompatibleWith(injectionPoint.getType())) {
						InjectionPointInfo info = new InjectionPointInfo(
								bean.getName(),
								bean.getType(),
								injectionPoint.getName(),
								injectionPoint.getType(),
								injectionPoint.getLocation() != null ? injectionPoint.getLocation().getUri() : null,
								injectionPoint.getLocation() != null ? injectionPoint.getLocation().getRange().getStart().getLine() : 0,
								injectionPoint.getLocation() != null ? injectionPoint.getLocation().getRange().getStart().getCharacter() : 0,
								injectionPoint.getLocation() != null ? injectionPoint.getLocation().getRange().getEnd().getLine() : 0,
								injectionPoint.getLocation() != null ? injectionPoint.getLocation().getRange().getEnd().getCharacter() : 0
						);
						injectionPoints.add(info);
					}
				}
			}
		}
		
		return injectionPoints;
	}

	private IJavaProject getProject(String projectName) throws Exception {
		Optional<? extends IJavaProject> found = projectFinder.all().stream()
				.filter(project -> project.getElementName().toLowerCase().equals(projectName.toLowerCase()))
				.findFirst();

		if (found.isEmpty()) {
			throw new Exception("project with name " + projectName + " not found");
		} else {
			return found.get();
		}
	}

}

