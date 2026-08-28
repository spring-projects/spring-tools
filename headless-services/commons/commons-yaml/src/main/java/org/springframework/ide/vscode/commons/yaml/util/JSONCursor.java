/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.util;

import java.util.stream.Stream;

import org.springframework.ide.vscode.commons.util.GsonUtil;
import org.springframework.ide.vscode.commons.yaml.path.YamlNavigable;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;

import com.google.gson.JsonElement;


/**
 * Allows using {@link YamlPath} / {@link YamlNavigable}
 */
public class JSONCursor implements YamlNavigable<JSONCursor> {

	public final JsonElement target;

	public JSONCursor(JsonElement target) {
		super();
		this.target = target;
	}

	@Override
	public Stream<JSONCursor> traverseAmbiguously(YamlPathSegment s) {
		return oneStep(s).map(JSONCursor::new);
	}

	private Stream<JsonElement> oneStep(YamlPathSegment s) {
		if (target == null) {
			return Stream.empty();
		}
		switch (s.getType()) {
			case KEY_AT_KEY: {
				return GsonUtil.getFromKey(target, s.toPropString());
			}
			case ANY_CHILD: {
				return GsonUtil.getFromElements(target);
			}
			case VAL_AT_INDEX: {
				return GsonUtil.getFromIndex(target, s.toIndex());
			}
			case VAL_AT_KEY: {
				return GsonUtil.getFromKey(target, s.toPropString());
			}
			default:
				throw new IllegalStateException("Missing case for "+s.getType());
		}
	}

	@Override
	public String toString() {
		return ""+target;
	}

}
