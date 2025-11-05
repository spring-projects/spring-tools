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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebEndpointIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.stereotype.Component;

/**
 * MCP tools for accessing request mapping information from Spring Boot projects
 * 
 * @author Martin Lippert
 */
@Component
public class RequestMappingMcpTools {

	private static final Logger logger = LoggerFactory.getLogger(RequestMappingMcpTools.class);
	
	private final JavaProjectFinder projectFinder;
	private final SpringMetamodelIndex springIndex;

	public RequestMappingMcpTools(JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex) {
		this.projectFinder = projectFinder;
		this.springIndex = springIndex;
	}

	/**
	 * Record representing request mapping information for MCP clients
	 */
	public static record RequestMappingInfo(
			String path,
			List<String> httpMethods,
			List<String> contentTypes,
			List<String> acceptTypes,
			String version,
			String controllerName,
			String controllerType,
			String methodSignature,
			String sourceFile,
			int startLine,
			int startColumn,
			int endLine,
			int endColumn
	) {}

	@Tool(description = """
			Get all REST endpoints/request mappings defined in the project.
			This includes endpoints from @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping annotations,
			as well as HttpExchange interfaces.
			""")
	public List<RequestMappingInfo> getRequestMappings(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName) throws Exception {
		
		logger.info("get request mappings for project: {}", projectName);
		
		IJavaProject project = getProject(projectName);
		
		// Get all WebEndpointIndexElement objects directly from the index
		List<WebEndpointIndexElement> webEndpoints = springIndex.getNodesOfType(
				project.getElementName(), 
				WebEndpointIndexElement.class);
		
		// Convert to RequestMappingInfo
		List<RequestMappingInfo> requestMappings = webEndpoints.stream()
				.map(this::createRequestMappingInfo)
				.collect(Collectors.toList());
		
		logger.info("found {} request mappings for project: {}", requestMappings.size(), projectName);
		
		return requestMappings;
	}

	@Tool(description = """
			Find request mappings by HTTP method (GET, POST, PUT, DELETE, PATCH, etc.).
			Returns all endpoints that handle the specified HTTP method.
			""")
	public List<RequestMappingInfo> findRequestMappingsByMethod(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName,
			@ToolParam(description = "the HTTP method to filter by (e.g., 'GET', 'POST', 'PUT', 'DELETE', 'PATCH')") String httpMethod) throws Exception {
		
		logger.info("find request mappings by method: {} for project: {}", httpMethod, projectName);
		
		// Get all request mappings first
		List<RequestMappingInfo> allMappings = getRequestMappings(projectName);
		
		// Filter by HTTP method (case-insensitive)
		String normalizedMethod = httpMethod.toUpperCase();
		List<RequestMappingInfo> filteredMappings = allMappings.stream()
				.filter(mapping -> mapping.httpMethods().stream()
						.anyMatch(method -> method.equalsIgnoreCase(normalizedMethod)))
				.collect(Collectors.toList());
		
		logger.info("found {} request mappings with method {} for project: {}", 
				filteredMappings.size(), httpMethod, projectName);
		
		return filteredMappings;
	}

	//
	// Helper methods
	//

	private RequestMappingInfo createRequestMappingInfo(WebEndpointIndexElement webEndpoint) {
		// Get the parent bean to access controller information
		Bean parentBean = getParentBean(webEndpoint);
		
		// Extract method signature if this is a RequestMappingIndexElement
		String methodSignature = null;
		if (webEndpoint instanceof RequestMappingIndexElement requestMapping) {
			methodSignature = requestMapping.getMethodSignature();
		}
		
		return new RequestMappingInfo(
				webEndpoint.getPath(),
				webEndpoint.getHttpMethods() != null ? 
						Arrays.asList(webEndpoint.getHttpMethods()) : List.of(),
				webEndpoint.getContentTypes() != null ? 
						Arrays.asList(webEndpoint.getContentTypes()) : List.of(),
				webEndpoint.getAcceptTypes() != null ? 
						Arrays.asList(webEndpoint.getAcceptTypes()) : List.of(),
				webEndpoint.getVersion(),
				parentBean != null ? parentBean.getName() : null,
				parentBean != null ? parentBean.getType() : null,
				methodSignature,
				parentBean != null && parentBean.getLocation() != null ? parentBean.getLocation().getUri() : null,
				parentBean != null && parentBean.getLocation() != null ? parentBean.getLocation().getRange().getStart().getLine() : 0,
				parentBean != null && parentBean.getLocation() != null ? parentBean.getLocation().getRange().getStart().getCharacter() : 0,
				parentBean != null && parentBean.getLocation() != null ? parentBean.getLocation().getRange().getEnd().getLine() : 0,
				parentBean != null && parentBean.getLocation() != null ? parentBean.getLocation().getRange().getEnd().getCharacter() : 0
		);
	}

	private Bean getParentBean(WebEndpointIndexElement webEndpoint) {
		// Search through all beans to find the one that contains this web endpoint as a child
		Bean[] allBeans = springIndex.getBeans();
		for (Bean bean : allBeans) {
			if (bean.getChildren().contains(webEndpoint)) {
				return bean;
			}
		}
		return null;
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

