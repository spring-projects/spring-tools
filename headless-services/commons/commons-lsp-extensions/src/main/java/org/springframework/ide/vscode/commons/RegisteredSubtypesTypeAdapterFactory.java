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
package org.springframework.ide.vscode.commons;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * A Gson {@link TypeAdapterFactory} for polymorphic serialization that only
 * deserializes explicitly registered subtypes. This implementation never falls
 * back to {@code Class.forName}: any unknown type label encountered during
 * deserialization causes an immediate {@link JsonParseException}, preventing
 * arbitrary class initialization from attacker-controlled JSON.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RegisteredSubtypesTypeAdapterFactory<Shape> factory =
 *     RegisteredSubtypesTypeAdapterFactory.of(Shape.class, "type")
 *         .registerSubtype(Circle.class, Circle.class.getName())
 *         .registerSubtype(Rectangle.class, Rectangle.class.getName());
 *
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapterFactory(factory)
 *     .create();
 * }</pre>
 *
 * <p>The type discriminator field (e.g. {@code "type"}) is written as the
 * first field during serialization and consumed (removed) during
 * deserialization, preserving the same JSON shape as before.
 *
 * <p>Register subtypes using their fully-qualified class name
 * ({@link Class#getName()}) as the label to maintain compatibility with
 * previously-written on-disk caches and LSP protocol messages.
 *
 * @param <T> the base type
 */
public final class RegisteredSubtypesTypeAdapterFactory<T> implements TypeAdapterFactory {

	private final Class<T> baseType;
	private final String typeFieldName;
	private final Map<String, Class<? extends T>> labelToSubtype = new LinkedHashMap<>();
	private final Map<Class<? extends T>, String> subtypeToLabel = new LinkedHashMap<>();

	private RegisteredSubtypesTypeAdapterFactory(Class<T> baseType, String typeFieldName) {
		if (baseType == null || typeFieldName == null) {
			throw new NullPointerException();
		}
		this.baseType = baseType;
		this.typeFieldName = typeFieldName;
	}

	/**
	 * Creates a new factory for {@code baseType} using {@code typeFieldName} as
	 * the type discriminator field name. Field names are case-sensitive.
	 */
	public static <T> RegisteredSubtypesTypeAdapterFactory<T> of(Class<T> baseType, String typeFieldName) {
		return new RegisteredSubtypesTypeAdapterFactory<>(baseType, typeFieldName);
	}

	/**
	 * Registers {@code type} identified by {@code label}. Labels are
	 * case-sensitive. Both {@code type} and {@code label} must be unique across
	 * all registrations on this factory.
	 *
	 * @return this factory, for chaining
	 * @throws IllegalArgumentException if {@code type} or {@code label} has
	 *                                  already been registered
	 */
	public RegisteredSubtypesTypeAdapterFactory<T> registerSubtype(Class<? extends T> type, String label) {
		if (type == null || label == null) {
			throw new NullPointerException();
		}
		if (subtypeToLabel.containsKey(type) || labelToSubtype.containsKey(label)) {
			throw new IllegalArgumentException("types and labels must be unique");
		}
		labelToSubtype.put(label, type);
		subtypeToLabel.put(type, label);
		return this;
	}

	@Override
	public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
		if (type == null || !baseType.equals(type.getRawType())) {
			return null;
		}

		Map<String, TypeAdapter<?>> labelToDelegate = new LinkedHashMap<>();
		Map<Class<?>, TypeAdapter<?>> subtypeToDelegate = new LinkedHashMap<>();

		for (Map.Entry<String, Class<? extends T>> entry : labelToSubtype.entrySet()) {
			TypeAdapter<?> delegate = gson.getDelegateAdapter(this, TypeToken.get(entry.getValue()));
			labelToDelegate.put(entry.getKey(), delegate);
			subtypeToDelegate.put(entry.getValue(), delegate);
		}

		TypeAdapter<JsonElement> jsonElementAdapter = gson.getAdapter(JsonElement.class);

		return new TypeAdapter<R>() {

			@SuppressWarnings("unchecked")
			@Override
			public R read(JsonReader in) throws IOException {
				JsonElement jsonElement = jsonElementAdapter.read(in);
				JsonElement labelElement = jsonElement.getAsJsonObject().remove(typeFieldName);
				if (labelElement == null) {
					throw new JsonParseException("cannot deserialize " + baseType.getName()
							+ ": missing type discriminator field '" + typeFieldName + "'");
				}
				String label = labelElement.getAsString();
				TypeAdapter<R> delegate = (TypeAdapter<R>) labelToDelegate.get(label);
				if (delegate == null) {
					throw new JsonParseException("cannot deserialize " + baseType.getName()
							+ " subtype named '" + label + "'; did you forget to register a subtype?");
				}
				return delegate.fromJsonTree(jsonElement);
			}

			@SuppressWarnings("unchecked")
			@Override
			public void write(JsonWriter out, R value) throws IOException {
				Class<?> srcType = value.getClass();
				String label = subtypeToLabel.get(srcType);
				if (label == null) {
					throw new JsonParseException("cannot serialize " + srcType.getName()
							+ "; did you forget to register a subtype?");
				}
				TypeAdapter<R> delegate = (TypeAdapter<R>) subtypeToDelegate.get(srcType);
				JsonObject jsonObject = delegate.toJsonTree(value).getAsJsonObject();
				if (jsonObject.has(typeFieldName)) {
					throw new JsonParseException("cannot serialize " + srcType.getName()
							+ ": it already defines a field named '" + typeFieldName + "'");
				}
				JsonObject clone = new JsonObject();
				clone.add(typeFieldName, new JsonPrimitive(label));
				for (Map.Entry<String, JsonElement> e : jsonObject.entrySet()) {
					clone.add(e.getKey(), e.getValue());
				}
				jsonElementAdapter.write(out, clone);
			}
		}.nullSafe();
	}
}
