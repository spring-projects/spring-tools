/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util.text;

/**
 * Central place to define constants for the known language-ids that we care
 * about.
 *
 * @author Kris De Volder
 */
public class LanguageId {

	public static final LanguageId PLAINTEXT = of("plaintext");
	public static final LanguageId CONCOURSE_TASK = of("concourse-task-yaml");
	public static final LanguageId CONCOURSE_PIPELINE = of("concourse-pipeline-yaml");
	public static final LanguageId CF_MANIFEST = of("manifest-yaml");
	public static final LanguageId JAVA = of("java");
	public static final LanguageId CLASS = of("class");
	public static final LanguageId KOTLIN = of("kotlin");
	public static final LanguageId YAML = of("yaml");
	public static final LanguageId XML = of("xml");
	
	public static final LanguageId BOSH_DEPLOYMENT = of("bosh-deployment-manifest");
	public static final LanguageId BOSH_CLOUD_CONFIG = of("bosh-cloud-config");
	public static final LanguageId BOOT_PROPERTIES = of("spring-boot-properties");
	public static final LanguageId BOOT_PROPERTIES_YAML = of("spring-boot-properties-yaml");
	public static final LanguageId SPRING_FACTORIES = of("spring-factories");
	public static final LanguageId JPA_QUERY_PROPERTIES = of("jpa-query-properties");

	private final String id;

	private LanguageId(String id) {
		this.id = id;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LanguageId other = (LanguageId) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "LanguageId ["+ id + "]";
	}

	public String getId() {
		return id;
	}

	public static LanguageId of(String languageId) {
		return new LanguageId(languageId);
	}
}