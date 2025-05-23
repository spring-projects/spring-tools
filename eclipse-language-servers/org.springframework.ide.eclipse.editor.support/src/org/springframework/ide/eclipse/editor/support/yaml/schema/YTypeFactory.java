/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.editor.support.hover.DescriptionProviders;
import org.springframework.ide.eclipse.editor.support.reconcile.ReconcileException;
import org.springframework.ide.eclipse.editor.support.reconcile.ReplacementQuickfix;
import org.springframework.ide.eclipse.editor.support.util.EnumValueParser;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springframework.ide.eclipse.editor.support.util.ValueParser;
import org.springframework.ide.eclipse.editor.support.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.eclipse.editor.support.yaml.schema.constraints.Constraint;
import org.springframework.ide.eclipse.editor.support.yaml.schema.constraints.Constraints;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
/**
 * Static utility method for creating YType objects representing either
 * 'array-like', 'map-like' or 'object-like' types which can be used
 * to build up a 'Yaml Schema'.
 *
 * @author Kris De Volder
 */
public class YTypeFactory {

	private static class Deprecation {
		final String errorMsg;
		final String replacement;
		final String quickfixMsg;
		public Deprecation(String errorMsg, String replacement, String quickfixMsg) {
			super();
			this.errorMsg = errorMsg;
			this.replacement = replacement;
			this.quickfixMsg = quickfixMsg;
		}

	}

	public YType yseq(YType el) {
		return new YSeqType(el);
	}

	public YType ymap(YType key, YType val) {
		return new YMapType(key, val);
	}

	public YBeanType ybean(String name, YTypedProperty... properties) {
		return new YBeanType(name, properties);
	}

	/**
	 * YTypeUtil instances capable of 'interpreting' the YType objects created by this
	 * YTypeFactory
	 */
	public final YTypeUtil TYPE_UTIL = new YTypeUtil() {

		@Override
		public boolean isSequencable(YType type) {
			return ((AbstractType)type).isSequenceable();
		}

		@Override
		public boolean isMap(YType type) {
			return ((AbstractType)type).isMap();
		}

		@Override
		public boolean isAtomic(YType type) {
			return ((AbstractType)type).isAtomic();
		}

		@Override
		public Map<String, YTypedProperty> getPropertiesMap(YType type) {
			return ((AbstractType)type).getPropertiesMap();
		}

		@Override
		public List<YTypedProperty> getProperties(YType type) {
			return ((AbstractType)type).getProperties();
		}

		@Override
		public YValueHint[] getHintValues(YType type) {
			return ((AbstractType)type).getHintValues();
		}

		@Override
		public YType getDomainType(YType type) {
			return ((AbstractType)type).getDomainType();
		}

		@Override
		public String niceTypeName(YType type) {
			return type.toString();
		}

		@Override
		public YType getKeyType(YType type) {
			return ((AbstractType)type).getKeyType();
		}

		@Override
		public boolean isBean(YType type) {
			return ((AbstractType)type).isBean();
		}

		@Override
		public ValueParser getValueParser(YType type) {
			return ((AbstractType)type).getParser();
		}
		@Override
		public List<Constraint> getConstraints(YType type) {
			return ((AbstractType)type).getConstraints();
		}

	};

	/////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Provides default implementations for all YType methods.
	 */
	public static abstract class AbstractType implements YType {

		private ValueParser parser;
		private List<YTypedProperty> propertyList = new ArrayList<>();
		private final List<YValueHint> hints = new ArrayList<>();
		private Map<String, YTypedProperty> cachedPropertyMap;
		private Supplier<Collection<YValueHint>> hintProvider;
		private List<Constraint> constraints = new ArrayList<>(2);

		public boolean isSequenceable() {
			return false;
		}

		public List<Constraint> getConstraints() {
			return constraints;
		}

		public boolean isBean() {
			return false;
		}

		public YType getKeyType() {
			return null;
		}

		public YType getDomainType() {
			return null;
		}

		public void addHintProvider(Supplier<Collection<YValueHint>> hintProvider) {
			this.hintProvider = hintProvider;
		}

		public YValueHint[] getHintValues() {
			Collection<YValueHint> providerHints = hintProvider != null ? hintProvider.get() : null;

			if (providerHints == null || providerHints.isEmpty()) {
				return hints.toArray(new YValueHint[hints.size()]);
			} else {
				// Only merge if there are Supplier hints to merge
				Set<YValueHint> mergedHints = new LinkedHashSet<>();

				// Add type hints first
				for (YValueHint val : hints) {
					mergedHints.add(val);
				}

				// merge the Supplier hints
				for (YValueHint val : providerHints) {
					mergedHints.add(val);
				}
				return mergedHints.toArray(new YValueHint[mergedHints.size()]);
			}
		}

		public final List<YTypedProperty> getProperties() {
			return Collections.unmodifiableList(propertyList);
		}

		public final Map<String, YTypedProperty> getPropertiesMap() {
			if (cachedPropertyMap==null) {
				cachedPropertyMap = new LinkedHashMap<>();
				for (YTypedProperty p : propertyList) {
					cachedPropertyMap.put(p.getName(), p);
				}
			}
			return Collections.unmodifiableMap(cachedPropertyMap);
		}

		public boolean isAtomic() {
			return false;
		}

		public boolean isMap() {
			return false;
		}

		public abstract String toString(); // force each sublcass to implement a (nice) toString method.

		public void addProperty(YTypedProperty p) {
			cachedPropertyMap = null;
			propertyList.add(p);
		}

		public void addProperty(String name, YType type, Supplier<HtmlSnippet> description) {
			YTypedPropertyImpl prop;
			addProperty(prop = new YTypedPropertyImpl(name, type));
			prop.setDescriptionProvider(description);
		}

		public void addProperty(String name, YType type) {
			addProperty(new YTypedPropertyImpl(name, type));
		}
		public void addHints(String... strings) {
			if (strings != null) {
				for (String value : strings) {
					BasicYValueHint hint = new BasicYValueHint(value);
					if (!hints.contains(hint)) {
						hints.add(hint);
					}
				}
			}
		}
		public void parseWith(ValueParser parser) {
			this.parser = parser;
		}
		public ValueParser getParser() {
			return parser;
		}
		public AbstractType require(Constraint dynamicConstraint) {
			this.constraints.add(dynamicConstraint);
			return this;
		}

		public void requireOneOf(String... properties) {
			this.constraints.add(Constraints.requireOneOf(properties));
		}
	}

	public static class YMapType extends AbstractType {

		private final YType key;
		private final YType val;

		private YMapType(YType key, YType val) {
			this.key = key;
			this.val = val;
		}

		@Override
		public String toString() {
			return "Map<"+key.toString()+", "+val.toString()+">";
		}

		@Override
		public boolean isMap() {
			return true;
		}

		@Override
		public YType getKeyType() {
			return key;
		}

		@Override
		public YType getDomainType() {
			return val;
		}
	}

	public static class YSeqType extends AbstractType {

		private YType el;

		private YSeqType(YType el) {
			this.el = el;
		}

		@Override
		public String toString() {
			return el.toString()+"[]";
		}

		@Override
		public boolean isSequenceable() {
			return true;
		}

		@Override
		public YType getDomainType() {
			return el;
		}
	}

	public static class YBeanType extends AbstractType {
		private final String name;

		public YBeanType(String name, YTypedProperty[] properties) {
			this.name = name;
			for (YTypedProperty p : properties) {
				addProperty(p);
			}
		}

		@Override
		public String toString() {
			return name;
		}

		public boolean isBean() {
			return true;
		}
	}

	public static class YAtomicType extends AbstractType {
		private final String name;
		private YAtomicType(String name) {
			this.name = name;
		}
		@Override
		public String toString() {
			return name;
		}
		@Override
		public boolean isAtomic() {
			return true;
		}
	}

	public class EnumTypeBuilder {

		private String name;
		private String[] values;

		private Map<String, Deprecation> deprecations = new HashMap<>();

		public EnumTypeBuilder(String name, String[] values) {
			this.name = name;
			this.values = values;
		}

		public YAtomicType build() {
			EnumValueParser basicParser = new EnumValueParser(name, values);
			YAtomicType t = yatomic(name);
			t.addHints(getNonDeprecatedValues());
			if (deprecations.isEmpty()) {
				t.parseWith(basicParser);
			} else {
				t.parseWith(ValueParser.of((String value) -> {
					basicParser.parse(value);
					Deprecation d = deprecations.get(value);
					if (d!=null) {
						throw new ReconcileException(d.errorMsg, YamlSchemaProblems.DEPRECATED_VALUE)
									.fixWith(new ReplacementQuickfix(d.quickfixMsg, d.replacement));
					}
					return value;
				}));
			}
			return t;
		}

		public EnumTypeBuilder deprecate(String value, String msg) {
			Assert.isLegal(ImmutableSet.copyOf(values).contains(value));
			deprecations.put(value, new Deprecation(msg, null, null));
			return this;
		}

		public EnumTypeBuilder deprecateWithReplacement(String value, String replacement) {
			Assert.isLegal(ImmutableSet.copyOf(values).contains(value));
			deprecations.put(value, new Deprecation(
				"The value '"+value+"' is deprecated in favor of '"+replacement+"'",
				replacement,
				"Replace deprecated value '"+value+"' by '"+replacement+"'"
			));
			return this;
		}

		private String[] getNonDeprecatedValues() {
			return Flux.fromArray(values)
				.filter((value) -> !deprecations.containsKey(value))
				.collectList()
				.map(l -> l.toArray(new String[l.size()]))
				.block();
		}

	}

	public static class YTypedPropertyImpl implements YTypedProperty {

		final private String name;
		final private YType type;
		private Supplier<HtmlSnippet> descriptionProvider = DescriptionProviders.NO_DESCRIPTION;
		private boolean isDeprecated;
		private String deprecationMessage;

		private YTypedPropertyImpl(String name, YType type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public YType getType() {
			return this.type;
		}

		@Override
		public String toString() {
			return name + ":" + type;
		}

		@Override
		public HtmlSnippet getDescription() {
			return descriptionProvider.get();
		}

		public void setDescriptionProvider(Supplier<HtmlSnippet> descriptionProvider) {
			this.descriptionProvider = descriptionProvider;
		}

		@Override
		public String getDeprecationMessage() {
			return this.deprecationMessage;
		}

		@Override
		public boolean isDeprecated() {
			return this.isDeprecated;
		}

	}

	public EnumTypeBuilder yenumBuilder(String name, String... values) {
		return new EnumTypeBuilder(name, values);
	}

	public YAtomicType yatomic(String name) {
		return new YAtomicType(name);
	}

	public YTypedPropertyImpl yprop(String name, YType type) {
		return new YTypedPropertyImpl(name, type);
	}

	public YAtomicType yenum(String name, String... values) {
		YAtomicType t = yatomic(name);
		t.addHints(values);
		t.parseWith(new EnumValueParser(name, values));
		return t;
	}
}
