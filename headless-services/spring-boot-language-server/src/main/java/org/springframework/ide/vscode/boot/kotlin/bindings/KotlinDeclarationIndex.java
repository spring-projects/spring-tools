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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;

/**
 * The classes declared in the Kotlin source files of a project, by their JVM names. The files of
 * a project refer to each other's classes, so this is what a class written in Kotlin is looked up
 * in, before the classpath is asked.
 *
 * @author Martin Lippert
 */
public class KotlinDeclarationIndex {

	private final Map<String, KotlinSourceClass> classesByBinaryName = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> binaryNamesByFile = new ConcurrentHashMap<>();

	private final Map<String, TopLevelConstant> topLevelConstants = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> topLevelConstantsByFile = new ConcurrentHashMap<>();

	/** a {@code const val} on the top level of a file, which is where it needs to be evaluated */
	public record TopLevelConstant(KotlinSyntax.File file, KotlinSyntax.Constant constant) {
	}

	/** records the classes of the file, replacing what was recorded for the file before */
	public void put(String fileKey, KotlinSyntax.File file) {
		remove(fileKey);

		Set<String> names = new HashSet<>();
		for (KotlinSyntax.ClassDeclaration declaration : file.classes()) {
			add(KotlinSourceClass.topLevel(file, declaration), names);
		}
		binaryNamesByFile.put(fileKey, names);

		Set<String> constantKeys = new HashSet<>();
		for (KotlinSyntax.Constant constant : file.constants()) {
			String key = constantKey(file.packageName(), constant.name());
			topLevelConstants.put(key, new TopLevelConstant(file, constant));
			constantKeys.add(key);
		}
		topLevelConstantsByFile.put(fileKey, constantKeys);
	}

	private static String constantKey(String packageName, String name) {
		return packageName.isEmpty() ? name : packageName + "." + name;
	}

	/** @return null if there is no such constant on the top level of a file in the package */
	public TopLevelConstant findTopLevelConstant(String packageName, String name) {
		return topLevelConstants.get(constantKey(packageName, name));
	}

	private void add(KotlinSourceClass source, Set<String> names) {
		classesByBinaryName.put(source.binaryName(), source);
		names.add(source.binaryName());
		for (KotlinSyntax.ClassDeclaration nested : source.declaration().nestedClasses()) {
			add(source.nested(nested), names);
		}
	}

	public void remove(String fileKey) {
		Set<String> names = binaryNamesByFile.remove(fileKey);
		if (names != null) {
			names.forEach(classesByBinaryName::remove);
		}

		Set<String> constantKeys = topLevelConstantsByFile.remove(fileKey);
		if (constantKeys != null) {
			constantKeys.forEach(topLevelConstants::remove);
		}
	}

	public KotlinSourceClass find(String binaryName) {
		return classesByBinaryName.get(binaryName);
	}

	public List<KotlinSourceClass> all() {
		return new ArrayList<>(classesByBinaryName.values());
	}

}
