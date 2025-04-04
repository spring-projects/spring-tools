/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.RemoveAnnotation;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.BeanRegistrarDeclarationReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class BeanRegistrarDeclarationReconcilerTest extends BaseReconcilerTest {
	
	@Override
	protected String getFolder() {
		return "sf7validation";
	}

	@Override
	protected String getProjectName() {
		return "sf7-validation";
	}
	
	@BeforeEach
	void setup() throws Exception {
		super.setup();
	}
	
	@AfterEach
	void tearDown() throws Exception {
		super.tearDown();
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), null);
	}
	
	@Test
	void noValidationIfNoBeanRegistrar() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		
		public class MyBeanRegistrar {
		
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();

			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void noConfigBeans() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		
		public class MyBeanRegistrar implements BeanRegistrar {
		
			@Override
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();

			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
	}

	@Test
	void noImportOverConfig() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		
		public class MyBeanRegistrar implements BeanRegistrar {
		
			@Override
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata};
			Bean configBean = new Bean("conf", "com.example.demo.Conf", null, null, null, annotations, true, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
	}

	@Test
	void importOverConfig() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		
		public class MyBeanRegistrar implements BeanRegistrar {
		
			@Override
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location l = new Location();
			Path sourceFolder = IClasspathUtil.getSourceFolders(project.getClasspath()).map(f -> f.toPath()).filter(p -> p.endsWith(Path.of("src",  "main", "java"))).findFirst().orElseThrow();
			l.setUri(sourceFolder.resolve("com/example/demo/Conf.java").toUri().toASCIIString());

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata importMetadata = new AnnotationMetadata(Annotations.IMPORT, false, null, Map.of());
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata, importMetadata};
			Bean configBean = new Bean("conf", "com.example.demo.Conf", l, null, null, annotations, true, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
	}
	
	@Test
	void imporWithBeanRegistrar() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		
		public class MyBeanRegistrar implements BeanRegistrar {
		
			@Override
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location l = new Location();
			Path sourceFolder = IClasspathUtil.getSourceFolders(project.getClasspath()).map(f -> f.toPath()).filter(p -> p.endsWith(Path.of("src",  "main", "java"))).findFirst().orElseThrow();
			l.setUri(sourceFolder.resolve("com/example/demo/B.java").toUri().toASCIIString());

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata importMetadata = new AnnotationMetadata(Annotations.IMPORT, false, null,
					Map.of("value", new AnnotationAttributeValue[] {
							new AnnotationAttributeValue("com.example.demo.MyBeanRegistrar", null) }));
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata, importMetadata};
			Bean configBean = new Bean("conf", "com.example.demo.Conf", l, null, null, annotations, true, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}


	@Test
	void testConfigWithProperImport() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		
		public class MyBeanRegistrar implements BeanRegistrar {
		
			@Override
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location l = new Location();
			Path sourceFolder = IClasspathUtil.getSourceFolders(project.getClasspath()).map(f -> f.toPath()).filter(p -> p.endsWith(Path.of("src",  "test", "java"))).findFirst().orElseThrow();
			l.setUri(sourceFolder.resolve("com/example/demo/Conf.java").toUri().toASCIIString());

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata importMetadata = new AnnotationMetadata(Annotations.IMPORT, false, null,
					Map.of("value", new AnnotationAttributeValue[] {
							new AnnotationAttributeValue("com.example.demo.MyBeanRegistrar", null) }));
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata, importMetadata};
			Bean configBean = new Bean("conf", "com.example.demo.Conf", l, null, null, annotations, true, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
	}
	
	@Test
	void componentAnnotationOver() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		import org.springframework.stereotype.Component;
		
		@Component
		public class MyBeanRegistrar implements BeanRegistrar {
		
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location l = new Location();
			Path sourceFolder = IClasspathUtil.getSourceFolders(project.getClasspath()).map(f -> f.toPath()).filter(p -> p.endsWith(Path.of("src",  "main", "java"))).findFirst().orElseThrow();
			l.setUri(sourceFolder.resolve("com/example/demo/B.java").toUri().toASCIIString());

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata importMetadata = new AnnotationMetadata(Annotations.IMPORT, false, null,
					Map.of("value", new AnnotationAttributeValue[] {
							new AnnotationAttributeValue("com.example.demo.MyBeanRegistrar", null) }));
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata, importMetadata};
			Bean configBean = new Bean("conf", "com.example.demo.Conf", l, null, null, annotations, true, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem p = problems.get(0);
		
		assertEquals(Boot4JavaProblemType.REGISTRAR_BEAN_INVALID_ANNOTATION.getLabel(), p.getType().getLabel());
		
		assertEquals(1, p.getQuickfixes().size());
		
		QuickfixData<?> qf = p.getQuickfixes().get(0);
		
		assertEquals("Remove `@Component`", qf.title);
		
		FixDescriptor fd = (FixDescriptor) qf.params;
		
		assertEquals(RemoveAnnotation.class.getName(), fd.getRecipeId());
		assertEquals("@org.springframework.stereotype.Component", fd.getParameters().get("annotationPattern"));
		
	}

	@Test
	void serviceAnnotationOver() throws Throwable {
		String source = """
		package com.example.demo;
		
		import org.springframework.beans.factory.BeanRegistrar;
		import org.springframework.beans.factory.BeanRegistry;
		import org.springframework.core.env.Environment;
		import org.springframework.stereotype.Service;
		
		@Service("myService")
		public class MyBeanRegistrar implements BeanRegistrar {
		
			public void register(BeanRegistry registry, Environment env) {
			}
		
		}
		""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			Location l = new Location();
			Path sourceFolder = IClasspathUtil.getSourceFolders(project.getClasspath()).map(f -> f.toPath()).filter(p -> p.endsWith(Path.of("src",  "main", "java"))).findFirst().orElseThrow();
			l.setUri(sourceFolder.resolve("com/example/demo/B.java").toUri().toASCIIString());

			AnnotationMetadata annotationMetadata = new AnnotationMetadata(Annotations.CONFIGURATION, false, null, Map.of());
			AnnotationMetadata importMetadata = new AnnotationMetadata(Annotations.IMPORT, false, null,
					Map.of("value", new AnnotationAttributeValue[] {
							new AnnotationAttributeValue("com.example.demo.MyBeanRegistrar", null) }));
			AnnotationMetadata[] annotations = new AnnotationMetadata[] {annotationMetadata, importMetadata};
			Bean configBean = new Bean("conf", "com.example.demo.Conf", l, null, null, annotations, true, "symbolLabel");
			Bean[] beans = new Bean[] {configBean};
			springIndex.updateBeans(getProjectName(), beans);
		
			BeanRegistrarDeclarationReconciler r = new BeanRegistrarDeclarationReconciler(new QuickfixRegistry(), springIndex);
			
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem p = problems.get(0);
		
		assertEquals(Boot4JavaProblemType.REGISTRAR_BEAN_INVALID_ANNOTATION.getLabel(), p.getType().getLabel());
		
		assertEquals(1, p.getQuickfixes().size());
		
		QuickfixData<?> qf = p.getQuickfixes().get(0);
		
		assertEquals("Remove `@Service`", qf.title);
		
		FixDescriptor fd = (FixDescriptor) qf.params;
		
		assertEquals(RemoveAnnotation.class.getName(), fd.getRecipeId());
		assertEquals("@org.springframework.stereotype.Service", fd.getParameters().get("annotationPattern"));
		
	}

}
