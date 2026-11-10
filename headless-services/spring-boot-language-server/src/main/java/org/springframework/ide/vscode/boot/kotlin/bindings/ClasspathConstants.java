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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IType;

/**
 * The values of the constants (static final fields with a constant value) of classes on the
 * classpath. They are read from the class files: the type index knows the fields, but not what
 * they are set to.
 *
 * @author Martin Lippert
 */
final class ClasspathConstants {

	private static final Logger log = LoggerFactory.getLogger(ClasspathConstants.class);

	private final Map<String, Map<String, Object>> constantsByClass = new ConcurrentHashMap<>();

	/**
	 * @return the value of the constant, null if the class has no such constant
	 */
	Object get(IType type, String field) {
		return constantsByClass.computeIfAbsent(type.getFullyQualifiedName(), name -> read(type)).get(field);
	}

	void clear() {
		constantsByClass.clear();
	}

	private Map<String, Object> read(IType type) {
		try {
			IJavaModuleData module = type.classpathContainer();
			File container = module != null ? module.getContainer() : null;
			if (container == null) {
				return Map.of();
			}

			String entry = type.getFullyQualifiedName().replace('.', '/') + ".class";
			byte[] bytes = readClassFile(container, entry);
			if (bytes == null) {
				return Map.of();
			}

			Map<String, Object> result = new HashMap<>();
			new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					boolean isConstant = value != null && (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_FINAL) != 0;
					if (isConstant) {
						result.put(name, convert(descriptor, value));
					}
					return null;
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			return result;
		}
		catch (Exception e) {
			log.debug("cannot read the constants of {}", type.getFullyQualifiedName(), e);
			return Map.of();
		}
	}

	private static byte[] readClassFile(File container, String entry) throws Exception {
		if (container.isDirectory()) {
			Path path = container.toPath().resolve(entry);
			return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
		}

		try (ZipFile zip = new ZipFile(container)) {
			ZipEntry zipEntry = zip.getEntry(entry);
			if (zipEntry == null) {
				return null;
			}
			try (InputStream in = zip.getInputStream(zipEntry)) {
				return in.readAllBytes();
			}
		}
	}

	/** class files keep booleans and characters as integers */
	private static Object convert(String descriptor, Object value) {
		if (value instanceof Integer number) {
			if ("Z".equals(descriptor)) {
				return number != 0;
			}
			if ("C".equals(descriptor)) {
				return (char) number.intValue();
			}
		}
		return value;
	}

}
