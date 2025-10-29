/*******************************************************************************
 * Copyright (c) 2018, 2023 Pivotal, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.commons.java.ClasspathData;
import org.springframework.ide.vscode.commons.java.IField;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class JandexClasspathTest {

	@TempDir
	File folder;

	class TestProject {
		String name;
		File root;
		File testClassesFolder;
		File outputFolder;
		BasicFileObserver fileObserver = new BasicFileObserver();

		TestProject(String name) throws Exception {
			this.name = name;
			this.root = new File(JandexClasspathTest.class.getResource("/" + name ).toURI());
			testClassesFolder = new File(root, "bin");
			this.outputFolder = folder;
		}

		void createClass(String fqName) throws Exception {
			String relativePath = fqName.replace('.', '/')+".class";
			File classFile = new File(testClassesFolder, relativePath);
			File target = new File(outputFolder, relativePath);
			target.getParentFile().mkdirs();
			Files.copy(classFile, target);
			fileObserver.notifyFileCreated(target.toURI().toASCIIString());
		}

		ClasspathData getClasspath() {
			return new ClasspathData(name, ImmutableList.of(
					CPE.source(new File(root, "src"), outputFolder)
			), null);
		}

		JandexClasspath getJandexClasspath() {
			return new JandexClasspath(getClasspath(), fileObserver, null);
		}

		public void deleteClass(String fqName, BiConsumer<BasicFileObserver, String> eventNoficator) {
			String relativePath = fqName.replace('.', '/')+".class";
			File classFile = new File(outputFolder, relativePath);
			classFile.delete();
			eventNoficator.accept(fileObserver, classFile.toURI().toASCIIString());
		}

		public void deleteClass(String fqName) {
			deleteClass(fqName, (fileObserver, path) -> fileObserver.notifyFileDeleted(path));
		}
	}

	@Test public void classfileChangesShouldTriggerReindexing() throws Exception {
		TestProject project = new TestProject("simple-java-project");
		project.createClass("demo.Hello");

		JandexClasspath subject = project.getJandexClasspath();

		assertNotNull(subject.findType("demo.Hello"));
		assertNull(subject.findType("demo.Goodbye"));

		project.createClass("demo.Goodbye");

		assertNotNull(subject.findType("demo.Hello"));
		assertNotNull(subject.findType("demo.Goodbye"));

		project.deleteClass("demo.Hello");

		assertNull(subject.findType("demo.Hello"));
		assertNotNull(subject.findType("demo.Goodbye"));

		project.deleteClass("demo.Goodbye", (fileObserver, deletedFile) -> fileObserver.notifyFileChanged(deletedFile));

		assertNull(subject.findType("demo.Hello"));
		assertNull(subject.findType("demo.Goodbye"));
	}


	@Test public void fieldSignature() throws Exception {
		TestProject project = new TestProject("simple-java-project");
		project.createClass("demo.Hello");

		JandexClasspath subject = project.getJandexClasspath();

		IType type = subject.findType("demo.Hello");
		assertNotNull(type);

		IField field = type.getField("message");
		assertNotNull(field);

		assertEquals("String demo.Hello.message", field.signature());
	}

	@Test public void methodSignature() throws Exception {
		TestProject project = new TestProject("simple-java-project");
		project.createClass("demo.Hello");

		JandexClasspath subject = project.getJandexClasspath();

		IType type = subject.findType("demo.Hello");
		assertNotNull(type);

		IMethod method = type.getMethod("getMessage", Stream.of());
		assertNotNull(method);

		assertEquals("java.util.List<java.lang.String> getMessage()", method.signature());
	}
}
