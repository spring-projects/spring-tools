/*******************************************************************************
 * Copyright (c) 2017, 2018 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.schema.constraints;

import static org.springframework.ide.eclipse.editor.support.yaml.reconcile.YamlSchemaProblems.EXTRA_PROPERTY;
import static org.springframework.ide.eclipse.editor.support.yaml.reconcile.YamlSchemaProblems.missingProperty;
import static org.springframework.ide.eclipse.editor.support.yaml.reconcile.YamlSchemaProblems.problem;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.IDocument;
import org.springframework.ide.eclipse.editor.support.reconcile.IProblemCollector;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.eclipse.editor.support.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.eclipse.editor.support.yaml.schema.SchemaContextAware;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

import com.google.common.collect.ImmutableSet;

/**
 * Various static methods for constructing/composing {@link Constraint}s.
 *
 * @author Kris De Volder
 */
public class Constraints {

	public static Constraint requireOneOf(String... properties) {
		return new RequireOneOf(properties);
	}

	public static Constraint requireAtMostOneOf(String... properties) {
		return new RequireOneOf(properties).allowFewer(true);
	}

	static private class RequireOneOf implements Constraint {

		private final String[] _requiredProps;
		private boolean allowFewer = false;

		public RequireOneOf(String[] properties) {
			Assert.isLegal(properties.length > 1);
			this._requiredProps = properties;
		}

		public Constraint allowFewer(boolean b) {
			this.allowFewer = b;
			return this;
		}

		@Override
		public void verify(DynamicSchemaContext dc, Node parent, Node _map, YType type, IProblemCollector problems) {
			IDocument doc = dc.getDocument();
			Set<String> foundProps = dc.getDefinedProperties();
			if (_map instanceof MappingNode) {
				MappingNode map = (MappingNode) _map;
				List<String> requiredProps = Arrays.asList(_requiredProps);
				long foundPropsCount = requiredProps.stream()
					.filter(foundProps::contains)
					.count();
				if (foundPropsCount == 0) {
					if (!allowFewer) {
						problems.accept(missingProperty(
								"One of "+requiredProps+" is required for '"+type+"'", doc, parent, map));
					}
				} else if (foundPropsCount > 1) {
					//Mark each of the found keys as a violation:
					for (NodeTuple entry : map.getValue()) {
						String key = NodeUtil.asScalar(entry.getKeyNode());
						if (key != null && requiredProps.contains(key)) {
							problems.accept(problem(EXTRA_PROPERTY,
									"Only one of "+requiredProps+" should be defined for '"+type+"'",  entry.getKeyNode()));
						}
					}
				}
			}
		}
	}

	public static Constraint deprecated(Function<String, String> messageFormatter, String... _deprecatedNames) {
		Set<String> deprecatedNames = ImmutableSet.copyOf(_deprecatedNames);
		return (DynamicSchemaContext dc, Node parent, Node _map, YType type, IProblemCollector problems) -> {
			if (_map instanceof MappingNode) {
				MappingNode map = (MappingNode) _map;
				for (NodeTuple prop : map.getValue()) {
					Node keyNode = prop.getKeyNode();
					String name = NodeUtil.asScalar(keyNode);
					if (deprecatedNames.contains(name)) {
						problems.accept(YamlSchemaProblems.deprecatedProperty(messageFormatter.apply(name), keyNode));
					}
				}
			}
		};
	}

	/**
	 * Deprecated because you shouldn't need to use this method to create a {@link SchemaContextAware} Constraint.
	 * A Constraint itself is already implicitly aware of the {@link DynamicSchemaContext} (i.e. it already receives
	 * the {@link DynamicSchemaContext} as a parameter to its verify method.
	 * <p>
	 * So instead of using this method to get a hold of the {@link DynamicSchemaContext} simply use the context
	 * passed to your constraint instead.
	 */
	@Deprecated
	public static Constraint schemaContextAware(SchemaContextAware<Constraint> dispatcher) {
		return (DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) -> {
			dispatcher.withContext(dc).verify(dc, parent, node, type, problems);
		};
	}
}