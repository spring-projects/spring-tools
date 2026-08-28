/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.path;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class JavaObjectNav implements YamlNavigable<JavaObjectNav> {

	private Object obj;

	public JavaObjectNav(Object obj) {
		this.obj = obj;
	}

	public Stream<String> asStringMaybe() {
		if (obj instanceof String) {
			return Stream.of((String)obj);
		}
		return Stream.empty();
	}

	@Override
	public Stream<JavaObjectNav> traverseAmbiguously(YamlPathSegment s) {
		if (obj == null) {
			return Stream.empty();
		} else {
			switch (s.getType()) {
			case ANY_CHILD:
				if (obj instanceof List) {
					List list = (List) obj;
					return list.stream().map(JavaObjectNav::new);
				} else if (obj instanceof Map) {
					Map<String, ?> map = (Map) obj;
					return map.values().stream().map(JavaObjectNav::new);
				}
				break;
			case KEY_AT_KEY:
				if (obj instanceof Map) {
					Map<String, ?> map = (Map) obj;
					String key = s.toPropString();
					if (map.containsKey(key)) {
						return Stream.of(new JavaObjectNav(key));
					}
					Object v = map.get(s.toPropString());
					if (v != null) {
						return Stream.of(new JavaObjectNav(v));
					}
				}
				break;
			case VAL_AT_KEY:
				if (obj instanceof Map) {
					Map<String, ?> map = (Map) obj;
					Object v = map.get(s.toPropString());
					if (v != null) {
						return Stream.of(new JavaObjectNav(v));
					}
				}
				break;
			case VAL_AT_INDEX:
				if (obj instanceof List) {
					List list = (List) obj;
					int index = s.toIndex();
					if (index >= 0 && index < list.size()) {
						return Stream.of(new JavaObjectNav(list.get(index)));
					}
				}
				break;
			}
		}
		return Stream.empty();
	}

}
