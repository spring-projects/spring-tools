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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.reconcilers.WebApiVersionSyntaxReconciler;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

/**
 * Test class for validating API version syntax in functional Spring WebMVC endpoints (RouterFunction).
 * 
 * Note: This test class is prepared for when version validation support for functional endpoints 
 * is fully implemented. Currently, validation for functional endpoints is not yet complete.
 * 
 * @author Martin Lippert
 */
public class WebMvcFunctionalApiVersionSyntaxReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "webapiversioning";
	}

	@Override
	protected String getProjectName() {
		return "sf7-validation";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new WebApiVersionSyntaxReconciler(null);
	}

	@BeforeEach
	void setup() throws Exception {
		super.setup();
	}
	
	@AfterEach
	void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test
	void functionalEndpointValidationRequiresCompleteAst() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.2.3"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		
		boolean requireCompleteAstExceptionThrown = false;
		
		try {
			reconcile(() -> {
				SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
				
				WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
						.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
						.buildFor(null);
				springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});
	
				WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
				return r;
			}, "MyRouter.java", source, false);
		}
		catch (RequiredCompleteAstException e) {
			requireCompleteAstExceptionThrown = true;
		}
		
		assertTrue(requireCompleteAstExceptionThrown);
	}

	@Test
	void functionalEndpointWithValidVersion() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.2.3"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(0, problems.size());
	}

	@Test
	void functionalEndpointWithInvalidVersionInStringLiteral() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("a.b.c"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(1, problems.size());
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot4JavaProblemType.API_VERSION_SYNTAX_ERROR, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("\"a.b.c\"", markedStr);
	}

	@Test
	void functionalEndpointWithStandardVersionParser() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.2.3"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.versionParser(WebConfigIndexElement.DEFAULT_VERSION_PARSER)
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(0, problems.size());
	}

	@Test
	void functionalEndpointWithCustomVersionParser() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						// With custom parser, any version string should be accepted
						return RouterFunctions.route()
							.GET("/api/users", version("custom-version"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.versionParser("custom.version.Parser")
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		// With custom parser, no validation errors should occur
		assertEquals(0, problems.size());
	}

	@Test
	void nestedFunctionalEndpointWithVersion() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.path("/api", builder -> builder
								.GET("/users", version("1.0.0"), request -> ServerResponse.ok().build())
								.POST("/users", version("1.0.0"), request -> ServerResponse.ok().build())
							)
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(0, problems.size());
	}
	
	@Test
	void functionalEndpointWithWildcardVersion() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.1+"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(0, problems.size());
	}
	
	@Test
	void functionalEndpointWithInvalidWildcardVersion() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.1++"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(1, problems.size());
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot4JavaProblemType.API_VERSION_SYNTAX_ERROR, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("\"1.1++\"", markedStr);
	}

	@Test
	void functionalEndpointWithMultipleRoutes() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.0.0"), request -> ServerResponse.ok().build())
							.GET("/api/products", version("2.0.0"), request -> ServerResponse.ok().build())
							.POST("/api/orders", version("1.5.0"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(0, problems.size());
	}

	@Test
	void functionalEndpointWithMixedValidAndInvalidVersions() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.function.RouterFunction;
				import org.springframework.web.servlet.function.RouterFunctions;
				import org.springframework.web.servlet.function.ServerResponse;
				import static org.springframework.web.servlet.function.RequestPredicates.version;
				
				@Configuration
				public class MyRouter {
				
					@Bean
					public RouterFunction<ServerResponse> routes() {
						return RouterFunctions.route()
							.GET("/api/users", version("1.0.0"), request -> ServerResponse.ok().build())
							.GET("/api/products", version("invalid"), request -> ServerResponse.ok().build())
							.POST("/api/orders", version("2.0.0"), request -> ServerResponse.ok().build())
							.build();
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "someURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "MyRouter.java", source, true);
		
		assertEquals(1, problems.size());
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot4JavaProblemType.API_VERSION_SYNTAX_ERROR, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("\"invalid\"", markedStr);
	}

}

