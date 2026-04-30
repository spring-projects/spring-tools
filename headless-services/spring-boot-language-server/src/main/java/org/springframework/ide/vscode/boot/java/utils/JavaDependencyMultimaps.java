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
package org.springframework.ide.vscode.boot.java.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

/**
 * Converts dependency multimaps between the typed model and the string map format used for JSON cache persistence.
 */
public final class JavaDependencyMultimaps {

	private JavaDependencyMultimaps() {
	}

	public static Map<String, Collection<String>> toSerializationMap(Multimap<SourceJavaFile, QualifiedTypeName> typed) {
		Map<String, Collection<String>> raw = new HashMap<>();
		for (Map.Entry<SourceJavaFile, Collection<QualifiedTypeName>> e : typed.asMap().entrySet()) {
			if (!e.getValue().isEmpty()) {
				raw.put(e.getKey().absolutePath(),
						e.getValue().stream().map(QualifiedTypeName::name).collect(Collectors.toUnmodifiableList()));
			}
		}
		return raw;
	}

	public static Multimap<SourceJavaFile, QualifiedTypeName> fromSerializationMap(
			Map<String, ? extends Collection<String>> raw) {
		Multimap<SourceJavaFile, QualifiedTypeName> out = MultimapBuilder.hashKeys().hashSetValues().build();
		if (raw == null) {
			return out;
		}
		for (Map.Entry<String, ? extends Collection<String>> e : raw.entrySet()) {
			if (e.getKey() == null || e.getValue() == null) {
				continue;
			}
			SourceJavaFile file = SourceJavaFile.of(e.getKey());
			for (String v : e.getValue()) {
				if (v != null) {
					out.put(file, QualifiedTypeName.of(v));
				}
			}
		}
		return out;
	}
}
