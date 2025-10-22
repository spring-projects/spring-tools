/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.lang.reflect.Type;
import java.util.Map;

import org.eclipse.lsp4j.Location;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

@SuppressWarnings({ "restriction", "serial" })
public class StereotypeNodeDeserializer implements com.google.gson.JsonDeserializer<StereotypeNode> {

	private static final String LOCATION = "location";
	private static final String ICON = "icon";
	private static final String TEXT = "text";
	private static final String CHILDREN = "children";
	private static final String REFERENCE = "reference";
	
	private static final String NODE_ID = "nodeId";
	

	@Override
	public StereotypeNode deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
		JsonObject object = json.getAsJsonObject();
		JsonObject attributes = object.getAsJsonObject("attributes");
		return new StereotypeNode(
				extractString(attributes, NODE_ID),
				extractString(attributes, TEXT),
				extractString(attributes, ICON),
				context.deserialize(attributes.get(LOCATION), Location.class),
				context.deserialize(attributes.get(REFERENCE), Location.class),
				context.deserialize(attributes, new TypeToken<Map<String, Object>>(){}.getType()),
				context.deserialize(object.get(CHILDREN), StereotypeNode[].class)
		);
	}
	
	private static String extractString(JsonObject object, String property) {
		JsonElement e = object.get(property);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}
	
}
