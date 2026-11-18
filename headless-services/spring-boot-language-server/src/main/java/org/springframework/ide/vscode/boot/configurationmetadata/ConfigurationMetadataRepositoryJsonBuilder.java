/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ide.vscode.boot.configurationmetadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ide.eclipse.org.json.JSONException;

/**
 * Load a {@link ConfigurationMetadataRepository} from the content of arbitrary
 * resource(s).
 *
 * @author Stephane Nicoll
 * @since 1.3.0
 */
public final class ConfigurationMetadataRepositoryJsonBuilder {

	/**
	 * UTF-8 Charset.
	 */
	public static final Charset UTF_8 = Charset.forName("UTF-8");

	private Charset defaultCharset = UTF_8;

	private final JsonReader reader = new JsonReader();

	private final List<RawConfigurationMetadata> rawDatas = new ArrayList<>();

	private ConfigurationMetadataRepositoryJsonBuilder(Charset defaultCharset) {
		this.defaultCharset = defaultCharset;
	}

	/**
	 * Add the content of a {@link ConfigurationMetadataRepository} defined by the
	 * specified {@link InputStream} json document using the default charset. If this
	 * metadata repository holds items that were loaded previously, these are ignored.
	 * <p>
	 * Leaves the stream open when done.
	 * @param origin optional information object to help identify where the inputstream came from
	 * @param inputStream the source input stream
	 * @return this builder
	 * @throws IOException in case of I/O errors
	 */
	public ConfigurationMetadataRepositoryJsonBuilder withJsonResource(
			Object origin, InputStream inputStream) throws IOException {
		return withJsonResource(origin, inputStream, this.defaultCharset);
	}

	/**
	 * Add the content of a {@link ConfigurationMetadataRepository} defined by the
	 * specified {@link InputStream} json document using the specified {@link Charset}. If
	 * this metadata repository holds items that were loaded previously, these are
	 * ignored.
	 * <p>
	 * Leaves the stream open when done.
	 * @param origin optional information object to help identify where the inputstream came from
	 * @param inputStream the source input stream
	 * @param charset the charset of the input
	 * @return this builder
	 * @throws IOException in case of I/O errors
	 */
	public ConfigurationMetadataRepositoryJsonBuilder withJsonResource(
			Object origin, InputStream inputStream, Charset charset) throws IOException {
		if (inputStream == null) {
			throw new IllegalArgumentException("InputStream must not be null.");
		}
		this.rawDatas.add(parseRaw(origin, inputStream, charset));
		return this;
	}

	/**
	 * Build a {@link ConfigurationMetadataRepository} with the current state of this
	 * builder.
	 * @return this builder
	 */
	public ConfigurationMetadataRepository build() {
		SimpleConfigurationMetadataRepository result = new SimpleConfigurationMetadataRepository();
		result.include(create(rawDatas));
		return result;
	}

	private RawConfigurationMetadata parseRaw(Object origin, InputStream in, Charset charset)
			throws IOException {
		try {
			return this.reader.read(origin, in, charset);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(
					"Failed to read configuration " + "metadata", ex);
		}
		catch (JSONException ex) {
			throw new IllegalArgumentException(
					"Invalid configuration " + "metadata document", ex);
		}
	}

	private SimpleConfigurationMetadataRepository create(
			Iterable<RawConfigurationMetadata> metadatas) {
		SimpleConfigurationMetadataRepository repository = new SimpleConfigurationMetadataRepository();

		for (RawConfigurationMetadata metadata : metadatas) {
			repository.add(metadata.getSources());
		}
		for (RawConfigurationMetadata metadata : metadatas) {
			for (ConfigurationMetadataItem item : metadata.getItems()) {
				ConfigurationMetadataSource source = getSource(metadata, item);
				repository.add(item, source);
			}
		}
		// getAllProperties() builds a new map on every call, and adding hints doesn't change the properties
		Map<String, ConfigurationMetadataProperty> allProperties = repository.getAllProperties();
		for (RawConfigurationMetadata metadata : metadatas) {
			for (ConfigurationMetadataHint hint : metadata.getHints()) {
				ConfigurationMetadataProperty property = allProperties.get(hint.getId());
				if (property != null) {
					addValueHints(property, hint);
				}
				else {
					String id = hint.resolveId();
					property = allProperties.get(id);
					if (property != null) {
						if (hint.isMapKeyHints()) {
							addMapHints(property, hint);
						}
						else {
							addValueHints(property, hint);
						}
					}
				}
			}
		}
		return repository;
	}

	private void addValueHints(ConfigurationMetadataProperty property,
			ConfigurationMetadataHint hint) {
		addAll(property.getHints().getValueHints(), hint.getValueHints());
		property.getHints().getValueProviders().addAll(hint.getValueProviders());
	}

	private void addMapHints(ConfigurationMetadataProperty property,
			ConfigurationMetadataHint hint) {
		addAll(property.getHints().getKeyHints(), hint.getValueHints());
		property.getHints().getKeyProviders().addAll(hint.getValueProviders());
	}

	/**
	 * Add a bunch of hints to a list, but guard against duplicates.
	 */
	private void addAll(List<ValueHint> existing, List<ValueHint> toAdd) {
		if (existing.isEmpty()) {
			existing.addAll(toAdd);
		} else if (toAdd.isEmpty()) {
			//nothing to add
		} else {
			Set<Object> existingValues = existing
					.stream()
					.map((hint) -> ""+hint.getValue())
					.collect(Collectors.toSet());
			for (ValueHint hint : toAdd) {
				if (!existingValues.contains(""+hint.getValue())) {
					existing.add(hint);
				}
			}
		}
	}

	private ConfigurationMetadataSource getSource(RawConfigurationMetadata metadata,
			ConfigurationMetadataItem item) {
		if (item.getSourceType() != null) {
			return metadata.getSource(item.getSourceType());
		}
		return null;
	}

	/**
	 * Create a new builder instance using {@link #UTF_8} as the default charset and the
	 * specified json resource.
	 * @param inputStreams the source input streams
	 * @return a new {@link ConfigurationMetadataRepositoryJsonBuilder} instance.
	 * @throws IOException on error
	 */
	public static ConfigurationMetadataRepositoryJsonBuilder create(
			InputStream... inputStreams) throws IOException {
		ConfigurationMetadataRepositoryJsonBuilder builder = create();
		for (InputStream inputStream : inputStreams) {
			builder = builder.withJsonResource(null, inputStream);
		}
		return builder;
	}

	/**
	 * Create a new builder instance using {@link #UTF_8} as the default charset.
	 * @return a new {@link ConfigurationMetadataRepositoryJsonBuilder} instance.
	 */
	public static ConfigurationMetadataRepositoryJsonBuilder create() {
		return create(UTF_8);
	}

	/**
	 * Create a new builder instance using the specified default {@link Charset}.
	 * @param defaultCharset the default charset to use
	 * @return a new {@link ConfigurationMetadataRepositoryJsonBuilder} instance.
	 */
	public static ConfigurationMetadataRepositoryJsonBuilder create(
			Charset defaultCharset) {
		return new ConfigurationMetadataRepositoryJsonBuilder(defaultCharset);
	}

}
