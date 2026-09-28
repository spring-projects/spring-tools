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
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

/**
 * Resolves the SQL dialect used to validate native {@code @Query} statements:
 * per-Java-package overrides ({@code sql-dialect-overrides}, read via
 * {@link BootJavaConfig}) combined with the project's classpath. A subpackage
 * inherits its nearest configured ancestor package's override.
 */
public class SqlDialectResolver {

	private static final Type OVERRIDES_MAP_TYPE = new TypeToken<Map<String, SqlType>>() {}.getType();

	/** Normalizes each raw value via {@link SqlType#fromSettingValue(String)}. */
	private static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(SqlType.class, (JsonDeserializer<SqlType>) (json, type, ctx) -> SqlType
					.fromSettingValue(json.isJsonPrimitive() ? json.getAsString() : null))
			.create();

	private final BootJavaConfig config;

	public SqlDialectResolver(BootJavaConfig config) {
		this.config = config;
	}

	/**
	 * The {@code sql-dialect-overrides} map (package name to dialect) - empty
	 * if unset. VSCode sends it as a JSON object; Eclipse's preference store
	 * is string-only, so it sends the same map JSON-encoded as a string -
	 * both are handled here.
	 */
	public Map<String, SqlType> getOverrides() {
		JsonObject obj = asJsonObject(config.getSqlDialectOverrides());
		return obj == null ? Map.of() : GSON.fromJson(obj, OVERRIDES_MAP_TYPE);
	}

	/**
	 * Walks up to {@code packageName}'s nearest configured ancestor (including
	 * itself); {@link SqlType#AUTO} if none is found in the hierarchy.
	 */
	public SqlType getOverride(String packageName) {
		Map<String, SqlType> overrides = getOverrides();
		if (overrides.isEmpty() || packageName == null) {
			return SqlType.AUTO;
		}
		String pkg = packageName;
		while (true) {
			SqlType value = overrides.get(pkg);
			if (value != null) {
				return value;
			}
			int dot = pkg.lastIndexOf('.');
			if (dot < 0) {
				return SqlType.AUTO;
			}
			pkg = pkg.substring(0, dot);
		}
	}

	/**
	 * The SQL dialects a project's classpath actually supports, in preference
	 * order. Zero entries means no recognized JDBC driver; more than one
	 * means the classpath is ambiguous. H2 only counts as evidence for
	 * {@link SqlType#POSTGRESQL} when no MySQL/MariaDB driver is present -
	 * it's a common test-scope addition alongside a "real" driver and
	 * shouldn't by itself make the classpath look ambiguous.
	 */
	public List<SqlType> applicableDialects(IJavaProject project) {
		boolean hasMysqlDriver = SpringProjectUtil.hasDependencyStartingWith(project, "mysql-connector", null)
				|| SpringProjectUtil.hasDependencyStartingWith(project, "mariadb-java-client", null);
		boolean hasPostgresDriver = SpringProjectUtil.hasDependencyStartingWith(project, "postgresql", null);
		boolean hasH2Driver = SpringProjectUtil.hasDependencyStartingWith(project, "h2", null);

		List<SqlType> result = new ArrayList<>();
		if (hasMysqlDriver) {
			result.add(SqlType.MYSQL);
		}
		if (hasPostgresDriver || (!hasMysqlDriver && hasH2Driver)) {
			result.add(SqlType.POSTGRESQL);
		}
		return result;
	}

	/** Combines the override (if any) with the classpath - {@code null} if neither applies. */
	public SqlType resolve(IJavaProject project, String packageName) {
		SqlType override = getOverride(packageName);
		if (override != SqlType.AUTO) {
			return override;
		}
		List<SqlType> applicable = applicableDialects(project);
		return applicable.isEmpty() ? null : applicable.get(0);
	}

	private static JsonObject asJsonObject(JsonElement raw) {
		if (raw == null || raw.isJsonNull()) {
			return null;
		}
		if (raw.isJsonObject()) {
			return raw.getAsJsonObject();
		}
		if (raw instanceof JsonPrimitive) {
			try {
				JsonElement parsed = JsonParser.parseString(raw.getAsString());
				return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

}
