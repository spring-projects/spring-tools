/*******************************************************************************
 * Copyright (c) 2018, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.jandex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ide.vscode.commons.java.IAnnotation;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IPrimitiveType;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.jdtls.JdtLsIndex;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.java.TypeData;
import org.springframework.ide.vscode.commons.protocol.java.TypeDescriptorData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import reactor.util.function.Tuple2;

public class JdtLsIndexTest {

	private Gson gson = new Gson();

	private TypeData loadJsonData(String fileName) throws Exception {
		File jsonFile = new File(JdtLsIndexTest.class.getResource("/java-data-json/" + fileName).toURI());
		return gson.fromJson(new FileReader(jsonFile), TypeData.class);
	}

	private List<TypeDescriptorData> loadJsonSearchTypeResults(String fileName) throws Exception {
		File jsonFile = new File(JdtLsIndexTest.class.getResource("/java-data-json/" + fileName).toURI());
		Type listType = new TypeToken<List<TypeDescriptorData>>(){}.getType();
		return gson.fromJson(new FileReader(jsonFile), listType);
	}

	@Test
	public void findTypeInJRE() throws Exception {
		STS4LanguageClient client = Mockito.mock(STS4LanguageClient.class);
		when(client.javaType(any())).thenReturn(CompletableFuture.supplyAsync(() -> {
			try {
				return loadJsonData("Map.json");
			} catch (Exception e) {
				return null;
			}
		}));
		// Some valid URI necessary for URI#toString() to succeed
		JdtLsIndex index = new JdtLsIndex(client, Paths.get(System.getProperty("java.io.tmpdir")).toUri(), ProjectObserver.NULL);
		IType type = index.findType("java.util.Map");
		assertNotNull(type);
		assertEquals("Ljava/util/Map;", type.getBindingKey());
		assertEquals("Map", type.getElementName());
		assertEquals("java.util.Map", type.getFullyQualifiedName());
		assertTrue(type.isInterface());
		assertFalse(type.isClass());
		assertEquals("java.util.Map<K, V>", type.signature());
	}

	@Test
	public void findMethodNoArgsInType() throws Exception {
		STS4LanguageClient client = Mockito.mock(STS4LanguageClient.class);
		when(client.javaType(any())).thenReturn(CompletableFuture.supplyAsync(() -> {
			try {
				return loadJsonData("Map.json");
			} catch (Exception e) {
				return null;
			}
		}));
		// Some valid URI necessary for URI#toString() to succeed
		JdtLsIndex index = new JdtLsIndex(client, Paths.get(System.getProperty("java.io.tmpdir")).toUri(), ProjectObserver.NULL);
		IType type = index.findType("java.util.Map");
		assertNotNull(type);

		IMethod method = type.getMethod("size", Stream.of());
		assertNotNull(method);
		assertEquals("Ljava/util/Map;.size()I", method.getBindingKey());
		assertEquals(IPrimitiveType.INT, method.getReturnType());
		assertEquals(type, method.getDeclaringType());
		assertTrue(method.parameters().collect(Collectors.toList()).isEmpty());
	}

	@Test
	public void typeAnnotation() throws Exception {
		STS4LanguageClient client = Mockito.mock(STS4LanguageClient.class);
		when(client.javaType(any())).thenReturn(CompletableFuture.supplyAsync(() -> {
			try {
				return loadJsonData("ServerProperties.json");
			} catch (Exception e) {
				return null;
			}
		}));
		// Some valid URI necessary for URI#toString() to succeed
		JdtLsIndex index = new JdtLsIndex(client, Paths.get(System.getProperty("java.io.tmpdir")).toUri(), ProjectObserver.NULL);
		IType type = index.findType("org.springframework.boot.autoconfigure.web.ServerProperties");
		assertNotNull(type);

		List<IAnnotation> annotations = type.getAnnotations().collect(Collectors.toList());
		assertEquals(1, annotations.size());
		IAnnotation a = annotations.get(0);

		// Don't tests element name for Annotation as it is different in JDT for source and binary types
		assertEquals("org.springframework.boot.context.properties.ConfigurationProperties", a.fqName());
		assertEquals("Lorg/springframework/boot/context/properties/ConfigurationProperties;", a.getBindingKey());
	}

	@Test
	public void searchType() throws Exception {
		STS4LanguageClient client = Mockito.mock(STS4LanguageClient.class);
		when(client.javaSearchTypes(any())).thenReturn(CompletableFuture.supplyAsync(() -> {
			try {
				return loadJsonSearchTypeResults("search-util-map.json");
			} catch (Exception e) {
				return null;
			}
		}));
		// Some valid URI necessary for URI#toString() to succeed
		JdtLsIndex index = new JdtLsIndex(client, Paths.get(System.getProperty("java.io.tmpdir")).toUri(), ProjectObserver.NULL);
		List<Tuple2<IType, Double>> results = index.fuzzySearchTypes("util.Map", true, false).collectSortedList((o1, o2) -> o2.getT2().compareTo(o1.getT2())).block();
		IType type = results.get(0).getT1();
		assertEquals("io.netty.util.Mapping", type.getFullyQualifiedName());
	}

	@Test
	public void searchPackage() throws Exception {
		STS4LanguageClient client = Mockito.mock(STS4LanguageClient.class);
		when(client.javaSearchPackages(any())).thenReturn(CompletableFuture.supplyAsync(() -> {
			try {
				return Arrays.asList("org.spring.example", "java.util", "com.example", "org.spring.data", "com.another.example", "org.example");
			} catch (Exception e) {
				return null;
			}
		}));
		// Some valid URI necessary for URI#toString() to succeed
		JdtLsIndex index = new JdtLsIndex(client, Paths.get(System.getProperty("java.io.tmpdir")).toUri(), ProjectObserver.NULL);
		List<Tuple2<String, Double>> results = index.fuzzySearchPackages("com.e", true, false).collectSortedList((o1, o2) -> o2.getT2().compareTo(o1.getT2())).block();
		List<String> packages = results.stream().map(t -> t.getT1()).collect(Collectors.toList());
		assertEquals(Arrays.asList("com.example", "com.another.example"), packages);
	}
}
