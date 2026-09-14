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
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Persists each project's structure baseline history to disk, one file per project, so it survives
 * a language server restart. Mirrors the conventions of the on-disk symbol cache
 * ({@code boot.index.cache.IndexCacheOnDiscDeltaBased}): a directory under {@code ~/.sts4}.
 *
 * <p>The file name is the project name plus a short hash of its location URI, not the project name
 * alone - project names are not unique across a whole machine, only within whatever single
 * workspace happens to have them open at the time, so two unrelated projects that both happen to be
 * called (say) "demo" would otherwise silently share - and corrupt - each other's baseline history
 * the moment either one is opened on its own in a later session. The hash only has to disambiguate
 * a handful of same-named projects on one machine, so a short, truncated one is plenty; it is not a
 * security boundary, just a good-enough tiebreaker. Changing this naming scheme itself orphans
 * previously-persisted files (they simply stop being found), which is fine for the same reason a
 * {@link #SCHEMA_VERSION} bump is: a baseline is cheap to re-capture and never precious data.
 *
 * <p>Never throws on a missing, corrupt, or otherwise unreadable file - callers get an empty
 * history as if none had ever been captured, and the language server keeps running.
 *
 * @author Martin Lippert
 */
public class StructureBaselineStorage {

	private static final Logger log = LoggerFactory.getLogger(StructureBaselineStorage.class);

	/**
	 * Bumped whenever the shape <i>or the meaning</i> of {@link StructureSnapshot} changes in a way
	 * that makes an older file unusable; such files are discarded rather than risking a broken read
	 * or misleading diffs.
	 * <p>
	 * Version 2 added the content hash to the nodes: older baselines carry no hashes, so comparing
	 * them against a freshly indexed tree would report every node as modified.
	 * <p>
	 * Version 3 changed what a stored baseline means - it now always represents the state of the
	 * commit it names, because it is only ever captured while no source changes are pending (see
	 * {@link GitBaselineTracker}). Baselines written before that could have been captured from a
	 * dirty working tree while still naming {@code HEAD}, and would keep hiding those changes until
	 * the next commit. Discarding them settles every project into a correct state at once: a clean
	 * project re-captures within one poll, a dirty one correctly waits for its next commit.
	 * <p>
	 * Version 4 changed a project's persisted content from a single {@link StructureSnapshot} to a
	 * history of them (newest first), and added the commit message to each. Discarding older files
	 * loses nothing but the history itself - the current baseline they held is simply re-captured
	 * (or re-derived from the next commit) exactly as an empty history would be.
	 */
	private static final int SCHEMA_VERSION = 4;

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

	/**
	 * Persists the project's whole retained baseline history (newest first). The caller is
	 * responsible for capping its size - this class stores exactly what it is given.
	 */
	public void save(IJavaProject project, List<StructureSnapshot> history) {
		File file = fileFor(project);
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			gson.toJson(new PersistedBaseline(SCHEMA_VERSION, history), writer);
		} catch (IOException | JsonIOException e) {
			log.warn("failed to persist structure baseline history for project: " + project.getElementName(), e);
		}
	}

	/**
	 * @return the persisted baseline history for the project (newest first), or an empty list if
	 *         there is none, or it couldn't be read (missing file, corrupt content, or a schema
	 *         version mismatch)
	 */
	public List<StructureSnapshot> load(IJavaProject project) {
		File file = fileFor(project);
		if (!file.isFile()) {
			return List.of();
		}

		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			PersistedBaseline persisted = gson.fromJson(reader, PersistedBaseline.class);

			if (persisted == null || persisted.schemaVersion() != SCHEMA_VERSION || persisted.history() == null) {
				log.info("discarding structure baseline history on disk for project '{}' - schema version mismatch or empty", project.getElementName());
				return List.of();
			}

			return persisted.history();
		} catch (IOException | JsonSyntaxException e) {
			log.warn("failed to read persisted structure baseline history for project: " + project.getElementName(), e);
			return List.of();
		}
	}

	/**
	 * Removes the persisted baseline for the project, if any. A no-op (not an error) if there is
	 * none.
	 */
	public void delete(IJavaProject project) {
		File file = fileFor(project);
		if (file.isFile() && !file.delete()) {
			log.warn("failed to delete persisted structure baseline for project: " + project.getElementName());
		}
	}

	/**
	 * The project name alone would be ambiguous (see this class' description), so the file name is
	 * the name plus a short hash of the project's location URI - stable across restarts (the same
	 * project, opened again, resolves to the same file) while still distinguishing two same-named
	 * projects living at different locations.
	 */
	private File fileFor(IJavaProject project) {
		String projectName = project.getElementName();
		if (projectName == null || projectName.indexOf('/') >= 0 || projectName.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("invalid project name for structure baseline storage: " + projectName);
		}
		String locationHash = DigestUtils.md5Hex(project.getLocationUri().normalize().toString()).substring(0, 12);
		return new File(directory, projectName + "-" + locationHash + ".json");
	}

	private static record PersistedBaseline(int schemaVersion, List<StructureSnapshot> history) {}

}
