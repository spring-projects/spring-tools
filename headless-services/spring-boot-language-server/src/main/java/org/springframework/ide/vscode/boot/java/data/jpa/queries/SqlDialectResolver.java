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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;

/**
 * Resolves the SQL dialect used to validate native {@code @Query} statements:
 * a global {@code spring-boot.ls.problem-parameters.data-query.sql-dialect} setting (read via
 * {@link BootJavaConfig}, same as every other global setting - no per-project
 * override, no separate client round trip) combined with what the project's
 * classpath itself indicates.
 */
public class SqlDialectResolver {

	public static final String AUTO_SETTING_VALUE = "auto";

	/**
	 * A settable value for {@code spring-boot.ls.problem-parameters.data-query.sql-dialect}
	 * ({@link BootJavaConfig#getSqlDialect()}): either
	 * {@link #AUTO_SETTING_VALUE} (no override - infer from the classpath)
	 * or a concrete {@link SqlType}'s {@link SqlType#getSettingValue()}.
	 */
	public record DialectOption(String label, String settingValue) {}

	private final BootJavaConfig config;

	public SqlDialectResolver(BootJavaConfig config) {
		this.config = config;
	}

	public Optional<SqlType> getOverride() {
		String value = config.getSqlDialect();
		for (SqlType type : SqlType.values()) {
			if (type.getSettingValue().equalsIgnoreCase(value)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

	/**
	 * The SQL dialects a project's classpath actually supports, in
	 * preference order (i.e. the order {@link QueryJdtAstReconciler} would
	 * pick from when there is no explicit override). Zero entries means no
	 * recognized JDBC driver was found at all; more than one means the
	 * classpath is ambiguous (both a MySQL/MariaDB and a PostgreSQL driver
	 * present).
	 * <p>
	 * H2 is only counted as evidence for {@link SqlType#POSTGRESQL} when no
	 * MySQL/MariaDB driver is present - it's a common test-scope addition
	 * alongside a "real" driver and shouldn't by itself make the classpath
	 * look ambiguous.
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

	/**
	 * Combines the global override (if any) with the classpath-applicable
	 * dialects into the reconciler to actually use - {@code null} if there's
	 * no override and the classpath has no recognized JDBC driver either.
	 */
	public SqlType resolve(IJavaProject project) {
		return getOverride().orElseGet(() -> {
			List<SqlType> applicable = applicableDialects(project);
			return applicable.isEmpty() ? null : applicable.get(0);
		});
	}

}
