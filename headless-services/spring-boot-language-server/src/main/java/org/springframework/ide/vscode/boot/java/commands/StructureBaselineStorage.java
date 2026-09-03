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
package org.springframework.ide.vscode.boot.java.commands;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Persists structure baselines to disk, one file per project, so they survive a language server
 * restart. Mirrors the conventions of the on-disk symbol cache
 * ({@code boot.index.cache.IndexCacheOnDiscDeltaBased}): a directory under {@code ~/.sts4}, keyed
 * purely by project name, project names rejected outright if they'd escape that directory.
 *
 * <p>Never throws on a missing, corrupt, or otherwise unreadable file - callers get {@code null}
 * as if no baseline had ever been captured, and the language server keeps running.
 *
 * @author Martin Lippert
 */
public class StructureBaselineStorage {

	private static final Logger log = LoggerFactory.getLogger(StructureBaselineStorage.class);

	/**
	 * Bumped whenever the shape of {@link StructureSnapshot} (or anything it references) changes
	 * in a way that could break deserializing an older file; such files are discarded rather than
	 * risking a broken read.
	 */
	private static final int SCHEMA_VERSION = 1;

	private final File directory;
	private final Gson gson = new GsonBuilder()
			.registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
				@Override
				public void write(JsonWriter out, Instant value) throws IOException {
					out.value(value == null ? null : value.toString());
				}

				@Override
				public Instant read(JsonReader in) throws IOException {
					return Instant.parse(in.nextString());
				}
			})
			.create();

	public StructureBaselineStorage(File directory) {
		this.directory = directory;

		if (!this.directory.exists() && !this.directory.mkdirs() && !this.directory.exists()) {
			log.warn("structure baseline directory does not exist and cannot be created: " + this.directory);
		}
	}

	public void save(String projectName, StructureSnapshot snapshot) {
		File file = fileFor(projectName);
		try (FileWriter writer = new FileWriter(file)) {
			gson.toJson(new PersistedBaseline(SCHEMA_VERSION, snapshot), writer);
		} catch (IOException | JsonIOException e) {
			log.warn("failed to persist structure baseline for project: " + projectName, e);
		}
	}

	/**
	 * @return the persisted baseline for the project, or {@code null} if there is none, or it
	 *         couldn't be read (missing file, corrupt content, or a schema version mismatch)
	 */
	public StructureSnapshot load(String projectName) {
		File file = fileFor(projectName);
		if (!file.isFile()) {
			return null;
		}

		try (FileReader reader = new FileReader(file)) {
			PersistedBaseline persisted = gson.fromJson(reader, PersistedBaseline.class);

			if (persisted == null || persisted.schemaVersion() != SCHEMA_VERSION || persisted.snapshot() == null) {
				log.info("discarding structure baseline on disk for project '{}' - schema version mismatch or empty", projectName);
				return null;
			}

			return persisted.snapshot();
		} catch (IOException | JsonSyntaxException e) {
			log.warn("failed to read persisted structure baseline for project: " + projectName, e);
			return null;
		}
	}

	private File fileFor(String projectName) {
		if (projectName == null || projectName.indexOf('/') >= 0 || projectName.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("invalid project name for structure baseline storage: " + projectName);
		}
		return new File(directory, projectName + ".json");
	}

	private static record PersistedBaseline(int schemaVersion, StructureSnapshot snapshot) {}

}
