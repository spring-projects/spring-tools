/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.maven;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/**
 * Light-weight counterpart to rewrite-maven's {@code UpgradeDependencyVersion}/
 * {@code UpgradeParentVersion}: rewrites a matching parent, imported BOM, or dependency
 * version to {@code newVersion}, treating the pom as plain XML rather than parsing it as
 * a Maven model - the caller is expected to have already validated the version, so there's
 * no need to re-resolve the ancestor/BOM chain or hit the network.
 * <p>
 * Out of scope: a version expressed via a {@code ${property}} defined in a different
 * (reactor-local parent) pom file, and removing now-redundant explicit version overrides.
 */
public class LightUpgradeDependencyVersion extends Recipe {

	private static final XPathMatcher PARENT_MATCHER = new XPathMatcher("/project/parent");
	private static final XPathMatcher DEPENDENCY_MATCHER = new XPathMatcher("//dependency");

	@Option(displayName = "Group",
			description = "The first part of a dependency coordinate, as a glob expression.",
			example = "org.springframework.boot")
	private final String groupId;

	@Option(displayName = "Artifact",
			description = "The second part of a dependency coordinate, as a glob expression.",
			example = "*")
	private final String artifactId;

	@Option(displayName = "New version",
			description = "The exact version to upgrade to.",
			example = "3.4.5")
	private final String newVersion;

	public LightUpgradeDependencyVersion(String groupId, String artifactId, String newVersion) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.newVersion = newVersion;
	}

	@Override
	public String getDisplayName() {
		return "Light upgrade dependency version";
	}

	@Override
	public String getDescription() {
		return "Rewrites the version of a matching Maven parent, imported BOM, or dependency "
				+ "(managed or direct) to an already-validated version, without parsing the pom as "
				+ "a Maven model.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return new XmlIsoVisitor<ExecutionContext>() {

			// ${property}-valued versions live under <properties>, not the tag itself - record
			// the names here and fix them up in a follow-up pass once the main walk completes.
			private final Set<String> propertiesToUpdate = new LinkedHashSet<>();

			@Override
			public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
				Xml.Document result = super.visitDocument(document, ctx);
				if (!propertiesToUpdate.isEmpty()) {
					Optional<Xml.Tag> properties = result.getRoot().getChild("properties");
					if (properties.isPresent()) {
						for (String propertyName : propertiesToUpdate) {
							Optional<Xml.Tag> propertyTag = properties.get().getChild(propertyName);
							if (propertyTag.isPresent()) {
								result = (Xml.Document) new ChangeTagValueVisitor<ExecutionContext>(propertyTag.get(), newVersion).visitNonNull(result, ctx);
							}
						}
					}
				}
				return result;
			}

			@Override
			public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
				Xml.Tag t = super.visitTag(tag, ctx);
				if ((PARENT_MATCHER.matches(getCursor()) || DEPENDENCY_MATCHER.matches(getCursor())) && matches(t)) {
					t = upgradeVersion(t, ctx);
				}
				return t;
			}

			private Xml.Tag upgradeVersion(Xml.Tag gavTag, ExecutionContext ctx) {
				Optional<Xml.Tag> versionTag = gavTag.getChild("version");
				if (versionTag.isEmpty()) {
					return gavTag;
				}
				String value = versionTag.get().getValue().orElse("");
				if (value.startsWith("${") && value.endsWith("}")) {
					propertiesToUpdate.add(value.substring(2, value.length() - 1));
					return gavTag;
				}
				// Scoped to gavTag, not the whole document - each match only needs its own subtree walked.
				return (Xml.Tag) new ChangeTagValueVisitor<ExecutionContext>(versionTag.get(), newVersion).visitNonNull(gavTag, ctx);
			}

			private boolean matches(Xml.Tag gavTag) {
				return StringUtils.matchesGlob(gavTag.getChildValue("groupId").orElse(""), groupId)
						&& StringUtils.matchesGlob(gavTag.getChildValue("artifactId").orElse(""), artifactId);
			}
		};
	}

}
