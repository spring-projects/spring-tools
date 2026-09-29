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
package org.springframework.ide.vscode.boot.java.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBytecode;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JarTypes;
import org.springframework.stereotype.Component;

/**
 * The two kinds of member only a method body tells, read from bytecode with ASM
 * ({@code JarBytecode}): event publishers and the beans a {@code BeanRegistrar} registers. Their
 * parity with the source side over the events fixture is {@code StructureParityTest}'s.
 *
 * @author Martin Lippert
 */
public class JarBytecodeScannersTest {

	@TempDir
	Path tempDir;

	@Test
	void aRegistrarComponentsRegisteredBeansAreItsMembers() throws Exception {
		JarType registrar = scan("com.example.Registrar", Map.of(
				"com.example.Foo", "package com.example; public class Foo {}",
				"com.example.Registrar", """
						package com.example;
						import org.springframework.beans.factory.BeanRegistrar;
						import org.springframework.beans.factory.BeanRegistry;
						import org.springframework.core.env.Environment;
						@org.springframework.stereotype.Component
						public class Registrar implements BeanRegistrar {
							public void register(BeanRegistry registry, Environment env) {
								registry.registerBean(Foo.class);
								registry.registerBean("bar", Foo.class);
								registry.registerBean(Foo.class, spec -> spec.lazyInit());
								if (env.getActiveProfiles().length > 0) {
									registry.registerBean("baz", Foo.class, spec -> spec.lazyInit());
								}
								Runnable later = () -> registry.registerBean("inLambda", Foo.class);
								later.run();
							}
						}
						"""));

		assertEquals(List.of(label("foo"), label("bar"), label("foo"), label("baz"), label("inLambda")), JarTypes.memberLabels(registrar));
		assertEquals("Lcom/example/Registrar;.register(Lorg/springframework/beans/factory/BeanRegistry;Lorg/springframework/core/env/Environment;)V",
				JarTypes.memberKeys(registrar).get(0));
	}

	@Test
	void aComponentWithAnInjectedPublisherHasItsPublishedEventsAsMembersInSourceOrder() throws Exception {
		JarType publisher = scan("com.example.Publisher", Map.of(
				"com.example.First", "package com.example; public class First {}",
				"com.example.Second", "package com.example; public class Second extends org.springframework.context.ApplicationEvent { public Second() { super(\"\"); } }",
				"com.example.Publisher", """
						package com.example;
						import org.springframework.context.ApplicationEventPublisher;
						@org.springframework.stereotype.Component
						public class Publisher {
							private final ApplicationEventPublisher publisher;
							public Publisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
							public void run() {
								Runnable later = () -> publisher.publishEvent(new First());
								publisher.publishEvent(new Second());
								later.run();
							}
						}
						"""));

		assertEquals(List.of("publishes: First", "publishes: Second"), JarTypes.memberLabels(publisher));
	}

	/**
	 * A publisher injected into a field, called through a subtype of {@code ApplicationEventPublisher}
	 * - the call resolves to the method it declares - in an anonymous class, and in a method whose
	 * analysis has to merge two values into an interface the verifier cannot see them implement.
	 */
	@Test
	void publishedEventsAreFoundThroughSubtypesInNestedClassesAndAcrossMerges() throws Exception {
		JarType publisher = scan("com.example.FieldPublisher", Map.of(
				"com.example.First", "package com.example; public class First {}",
				"com.example.Second", "package com.example; public class Second {}",
				"com.example.Marker", "package com.example; public interface Marker { default void mark() {} }",
				"com.example.Base", "package com.example; public class Base {}",
				"com.example.A", "package com.example; public class A extends Base implements Marker {}",
				"com.example.B", "package com.example; public class B extends Base implements Marker {}",
				"com.example.FieldPublisher", """
						package com.example;
						import org.springframework.beans.factory.annotation.Autowired;
						import org.springframework.context.ApplicationContext;
						@org.springframework.stereotype.Component
						public class FieldPublisher {
							@Autowired private org.springframework.context.ApplicationEventPublisher publisher;
							private ApplicationContext context;
							public void run(boolean flag) {
								Marker marker = flag ? new A() : new B();
								marker.mark();
								publisher.publishEvent(new First());
								new Runnable() {
									public void run() { context.publishEvent(new Second()); }
								}.run();
							}
						}
						"""));

		assertEquals(List.of("publishes: First", "publishes: Second"), JarTypes.memberLabels(publisher));
	}

	/**
	 * The source side looks for published events only where a publisher is injected - and so only
	 * those classes' method bodies are read.
	 */
	@Test
	void aComponentWithoutAnInjectedPublisherHasNone() throws Exception {
		JarType notInjecting = scan("com.example.NotInjecting", Map.of(
				"com.example.NotInjecting", """
						package com.example;
						import org.springframework.context.ApplicationEventPublisher;
						@org.springframework.stereotype.Component
						public class NotInjecting {
							public void run(ApplicationEventPublisher publisher) { publisher.publishEvent("event"); }
						}
						"""));

		int read = JarBytecode.classesReadSoFar();
		assertEquals(List.of(), JarTypes.memberLabels(notInjecting));
		assertEquals(read, JarBytecode.classesReadSoFar(), "no bytecode read for a class without an injected publisher");
	}

	private static String label(String beanName) {
		return BeanUtils.COMPONENT_LABEL_STRATEGY.createLabel(beanName, null, "com.example.Foo");
	}

	private JarType scan(String fqn, Map<String, String> sources) throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", sources, Map.of());
		return JarTypes.scan(jar, List.of(jarOf(BeanRegistry.class), jarOf(Environment.class), jarOf(ApplicationEventPublisher.class), jarOf(Component.class),
				jarOf(Autowired.class)))
				.get(fqn);
	}

	private static File jarOf(Class<?> type) throws Exception {
		return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

}
