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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.AddConfigurationIfBeansPresentReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

public class AddConfigurationIfBeansPresentReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "addconfiguration";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), null);
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
	void basicCase() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					public String sayHello() {
						System.out.println("hello");
					}
					
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			AddConfigurationIfBeansPresentReconciler r = new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("A", markedStr);

		assertEquals(2, problem.getQuickfixes().size());
	}

	@Test
	void beanCase() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata};
			Bean configBean = new Bean("a", "example.demo.A", null, null, null, annotations, false, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			AddConfigurationIfBeansPresentReconciler r = new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void feignClientConfigCase() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.FEIGN_CLIENT, false, null, Map.of("configuration", new AnnotationAttributeValue[] {new AnnotationAttributeValue("example.demo.A", null)}));
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata};
			Bean configBean = new Bean("feignClient", "example.demo.FeignClientExample", null, null, null, annotations, false, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
			
			AddConfigurationIfBeansPresentReconciler r = new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}
	
	@Test
	void loadBalancerConfigCase() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.LOAD_BALANCER_CLIENT, false, null, Map.of("configuration", new AnnotationAttributeValue[] {new AnnotationAttributeValue("example.demo.A", null)}));
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata};
			Bean configBean = new Bean("loadBalancerClient", "example.demo.LoadBalancerExample", null, null, null, annotations, false, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
			
			AddConfigurationIfBeansPresentReconciler r = new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void quickFixAddToFeignClientConfiguration() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location feignClientLocation = new Location("file:///some/path/FeignClientExample.java", new Range(new Position(0, 0), new Position(10, 0)));
			AnnotationMetadata feignAnnotation = new AnnotationMetadata(Annotations.FEIGN_CLIENT, false, null, Map.of("name", new AnnotationAttributeValue[] {new AnnotationAttributeValue("stores", null)}));
			Bean feignBean = new Bean("feignClient", "example.demo.FeignClientExample", feignClientLocation, null, null, new AnnotationMetadata[] {feignAnnotation}, false, "symbolLabel");
			springIndex.updateBeans(getProjectName(), new Bean[] {feignBean});
			
			return new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION, problem.getType());
		
		// 2 standard @Configuration fixes + 1 FeignClient fix
		assertEquals(3, problem.getQuickfixes().size());
		assertTrue(problem.getQuickfixes().stream().anyMatch(qf -> qf.title.contains("@FeignClient")));
	}

	@Test
	void quickFixAddToLoadBalancerClientConfiguration() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location lbClientLocation = new Location("file:///some/path/LoadBalancerClientExample.java", new Range(new Position(0, 0), new Position(10, 0)));
			AnnotationMetadata lbAnnotation = new AnnotationMetadata(Annotations.LOAD_BALANCER_CLIENT, false, null, Map.of("name", new AnnotationAttributeValue[] {new AnnotationAttributeValue("first", null)}));
			Bean lbBean = new Bean("loadBalancerClient", "example.demo.LoadBalancerClientExample", lbClientLocation, null, null, new AnnotationMetadata[] {lbAnnotation}, false, "symbolLabel");
			springIndex.updateBeans(getProjectName(), new Bean[] {lbBean});
			
			return new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION, problem.getType());
		
		// 2 standard @Configuration fixes + 1 LoadBalancerClient fix
		assertEquals(3, problem.getQuickfixes().size());
		assertTrue(problem.getQuickfixes().stream().anyMatch(qf -> qf.title.contains("@LoadBalancerClient")));
	}

	@Test
	void quickFixAddToBothFeignAndLoadBalancerClientConfiguration() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location feignClientLocation = new Location("file:///some/path/FeignClientExample.java", new Range(new Position(0, 0), new Position(10, 0)));
			AnnotationMetadata feignAnnotation = new AnnotationMetadata(Annotations.FEIGN_CLIENT, false, null, Map.of("name", new AnnotationAttributeValue[] {new AnnotationAttributeValue("stores", null)}));
			Bean feignBean = new Bean("feignClient", "example.demo.FeignClientExample", feignClientLocation, null, null, new AnnotationMetadata[] {feignAnnotation}, false, "symbolLabel");
			
			Location lbClientLocation = new Location("file:///some/path/LoadBalancerClientExample.java", new Range(new Position(0, 0), new Position(10, 0)));
			AnnotationMetadata lbAnnotation = new AnnotationMetadata(Annotations.LOAD_BALANCER_CLIENT, false, null, Map.of("name", new AnnotationAttributeValue[] {new AnnotationAttributeValue("first", null)}));
			Bean lbBean = new Bean("loadBalancerClient", "example.demo.LoadBalancerClientExample", lbClientLocation, null, null, new AnnotationMetadata[] {lbAnnotation}, false, "symbolLabel");
			
			springIndex.updateBeans(getProjectName(), new Bean[] {feignBean, lbBean});
			
			return new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION, problem.getType());
		
		// 2 standard @Configuration fixes + 1 FeignClient fix + 1 LoadBalancerClient fix
		assertEquals(4, problem.getQuickfixes().size());
		assertTrue(problem.getQuickfixes().stream().anyMatch(qf -> qf.title.contains("@FeignClient")));
		assertTrue(problem.getQuickfixes().stream().anyMatch(qf -> qf.title.contains("@LoadBalancerClient")));
	}

	@Test
	void noClientConfigQuickFixWhenNoClientBeansInIndex() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					String myBean() {
						return "my-bean";
					}
					
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			// Only a regular bean in the index, no @FeignClient or @LoadBalancerClient
			AnnotationMetadata configAnnotation = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			Bean regularBean = new Bean("otherBean", "example.demo.OtherBean", null, null, null, new AnnotationMetadata[] {configAnnotation}, true, "symbolLabel");
			springIndex.updateBeans(getProjectName(), new Bean[] {regularBean});
			
			return new AddConfigurationIfBeansPresentReconciler(new QuickfixRegistry(), springIndex);
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		// Only 2 standard @Configuration fixes, no client config fixes
		assertEquals(2, problem.getQuickfixes().size());
	}

}
