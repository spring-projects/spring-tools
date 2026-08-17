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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Identifies the exact position at which a stereotype is defined inside a jMolecules stereotype
 * catalog file. The catalog itself knows the file a stereotype originates from, but not the position
 * within that file, so clients could only open the catalog file at its very beginning.
 *
 * @author Martin Lippert
 */
public class StereotypeDefinitionLocator {

	private static final Logger log = LoggerFactory.getLogger(StereotypeDefinitionLocator.class);

	private static final String STEREOTYPES_MEMBER = "stereotypes";
	private static final String FILE_PROTOCOL = "file";

	private final Map<String, ParsedCatalogFile> cache = new ConcurrentHashMap<>();

	/**
	 * Returns the range of the definition of the stereotype with the given identifier within the
	 * given catalog file, or an empty optional if that catalog file doesn't define the stereotype
	 * (or cannot be parsed).
	 */
	public Optional<Range> findDefinition(URL catalogFile, String stereotypeIdentifier) {
		if (catalogFile == null || stereotypeIdentifier == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(definitionRanges(catalogFile).get(stereotypeIdentifier));
	}

	private Map<String, Range> definitionRanges(URL catalogFile) {
		String key = catalogFile.toString();
		long lastModified = lastModified(catalogFile);

		ParsedCatalogFile cached = cache.get(key);
		if (cached != null && cached.lastModified() == lastModified) {
			return cached.definitionRanges();
		}

		Map<String, Range> definitionRanges = parse(catalogFile);
		cache.put(key, new ParsedCatalogFile(lastModified, definitionRanges));

		return definitionRanges;
	}

	private static Map<String, Range> parse(URL catalogFile) {
		Map<String, Range> definitionRanges = new HashMap<>();

		// a reader (instead of the raw stream) is used deliberately, the parser counts columns in
		// characters then, which is what LSP positions are based on as well
		try (Reader reader = new InputStreamReader(catalogFile.openStream(), StandardCharsets.UTF_8);
				JsonParser parser = new JsonFactory().createParser(reader)) {

			if (parser.nextToken() != JsonToken.START_OBJECT) {
				return definitionRanges;
			}

			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				boolean stereotypes = STEREOTYPES_MEMBER.equals(parser.currentName());

				if (parser.nextToken() == JsonToken.START_OBJECT && stereotypes) {
					collectDefinitionRanges(parser, definitionRanges);
				}
				else {
					parser.skipChildren();
				}
			}

		} catch (Exception e) {
			log.error("error identifying stereotype definition positions in catalog file: " + catalogFile, e);
			return Collections.emptyMap();
		}

		return definitionRanges;
	}

	private static void collectDefinitionRanges(JsonParser parser, Map<String, Range> definitionRanges) throws IOException {
		while (parser.nextToken() == JsonToken.FIELD_NAME) {
			String identifier = parser.currentName();
			JsonLocation start = parser.currentTokenLocation();

			parser.nextToken();
			parser.skipChildren();

			JsonLocation end = parser.currentLocation();

			definitionRanges.put(identifier, new Range(position(start), position(end)));
		}
	}

	/**
	 * Jackson reports 1-based lines and columns, LSP positions are 0-based. The location of the
	 * current token points at its first character, the current location of the parser points right
	 * behind the character that was consumed last - which matches the exclusive end of an LSP range.
	 */
	private static Position position(JsonLocation location) {
		return new Position(Math.max(0, location.getLineNr() - 1), Math.max(0, location.getColumnNr() - 1));
	}

	private static long lastModified(URL catalogFile) {
		try {
			if (FILE_PROTOCOL.equals(catalogFile.getProtocol())) {
				return new File(catalogFile.toURI()).lastModified();
			}

			URLConnection connection = catalogFile.openConnection();
			connection.setUseCaches(true);
			return connection.getLastModified();

		} catch (Exception e) {
			log.error("error looking up last modified timestamp of catalog file: " + catalogFile, e);
			return 0;
		}
	}

	private static record ParsedCatalogFile(long lastModified, Map<String, Range> definitionRanges) {}

}
