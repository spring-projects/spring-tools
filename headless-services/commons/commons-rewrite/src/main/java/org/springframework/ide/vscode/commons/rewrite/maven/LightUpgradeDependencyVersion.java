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
 * A light-weight counterpart to rewrite-maven's {@code UpgradeDependencyVersion} and
 * {@code UpgradeParentVersion}: rewrites the version of a matching Maven parent,
 * imported BOM, or dependency (managed or direct) to {@code newVersion}.
 * <p>
 * Deliberately does not use OpenRewrite's Maven model (`MavenParser`/`Pom.resolve()`)
 * the way those two recipes do - the caller is expected to have already validated
 * {@code newVersion} against real Maven metadata, so there's no need to re-resolve the
 * project's ancestor/BOM chain or hit the network again (which is what those recipes do
 * internally to validate/select a version). This only needs to find and rewrite a
 * handful of version tags in the project's own pom.xml, treated as plain XML.
 * <p>
 * Out of scope: a version expressed via a `${property}` defined in a <i>different</i>
 * (reactor-local parent) pom file, and removing now-redundant explicit version
 * overrides that duplicate the newly-bumped managed version - both are edge/polish
 * cases relative to the dominant case of a single-module, Initializr-generated project.
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

			// Version tags expressed as a `${property}` reference can't be fixed up in place
			// (the property they refer to lives elsewhere in the document, under
			// `<properties>`) - so matches found while walking parent/dependency tags are
			// recorded here and applied in a follow-up pass over `<properties>` once the
			// main walk completes. A fresh visitor instance (and so a fresh, empty set) is
			// handed out per source file by the recipe scheduler, so no per-file reset is needed.
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
				// Scoped to `gavTag` itself, not the whole document: a document can contain
				// many matching dependencies, and each only needs its own small subtree walked.
				return (Xml.Tag) new ChangeTagValueVisitor<ExecutionContext>(versionTag.get(), newVersion).visitNonNull(gavTag, ctx);
			}

			private boolean matches(Xml.Tag gavTag) {
				return StringUtils.matchesGlob(gavTag.getChildValue("groupId").orElse(""), groupId)
						&& StringUtils.matchesGlob(gavTag.getChildValue("artifactId").orElse(""), artifactId);
			}
		};
	}

}
