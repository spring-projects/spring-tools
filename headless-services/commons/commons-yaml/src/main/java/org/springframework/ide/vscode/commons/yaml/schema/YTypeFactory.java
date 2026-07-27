/*******************************************************************************
 * Copyright (c) 2016, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileException;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReplacementQuickfix;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.CollectorUtil;
import org.springframework.ide.vscode.commons.util.EnumValueParser;
import org.springframework.ide.vscode.commons.util.PartialCollection;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.SimpleGlob;
import org.springframework.ide.vscode.commons.util.SimpleGlob.Match;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraint;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraints;
import org.springframework.ide.vscode.commons.yaml.snippet.TypeBasedSnippetProvider;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
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

	private boolean enableTieredOptionalPropertyProposals = true;
	private boolean suggestDeprecatedProperties = true;
	private TypeBasedSnippetProvider snippetProvider = null;

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

	public YContextSensitive contextAware(String name, SchemaContextAware<YType> guessType) {
		return new YContextSensitive(name, guessType);
	}

	public AbstractType yany(String name) {
		return new YAny(name);
	}

	public YSeqType yseq(YType el) {
		return new YSeqType(el);
	}

	public YType ymap(YType key, YType val) {
		return new YMapType(key, val);
	}

	public YBeanType ybean(String name, YTypedProperty... properties) {
		return new YBeanType(name, properties);
	}

	public YBeanUnionType yBeanUnion(String name, YBeanType[] types) {
		return (YBeanUnionType) yunion(name, types);
	}

	public YType yunion(String name, YType... types) {
		Assert.isLegal(types.length>1);
		if (Stream.of(types).allMatch(t -> t instanceof YBeanType)) {
			YBeanType[] beanTypes = new YBeanType[types.length];
			for (int i = 0; i < beanTypes.length; i++) {
				beanTypes[i] = (YBeanType) types[i];
			}
			return new YBeanUnionType(name, beanTypes);
		}
		ArrayList<YBeanType> beans = new ArrayList<>(types.length);
		ArrayList<YMapType> maps = new ArrayList<>(types.length);
		ArrayList<YAtomicType> atoms = new ArrayList<>(types.length);
		ArrayList<YSeqType> arrays = new ArrayList<>(types.length);
		for (YType t : types) {
			if (t instanceof YMapType) {
				maps.add((YMapType) t);
			} else if (t instanceof YAtomicType) {
				atoms.add((YAtomicType) t);
			} else if (t instanceof YSeqType) {
				arrays.add((YSeqType) t);
			} else if (t instanceof YBeanType) {
				beans.add((YBeanType) t);
			} else {
				throw new IllegalArgumentException("Union of this kind of types is not (yet) supported: "+t);
			}
		}
		if (atoms.size()==1 && maps.size()==1 && arrays.size()==0 && beans.size()==0) {
			return new YAtomAndMapUnion(name, atoms.get(0), maps.get(0));
		} else if (atoms.size()==0 && maps.size()==0 && arrays.size()==1 && beans.size()==1) {
			return new YBeanAndSequenceUnion(name, beans.get(0), arrays.get(0));
		} else if (atoms.size()==1 && arrays.size()==1 && maps.size()==0 && beans.size()==0) {
			return new YAtomAndSequenceUnion(name, atoms.get(0), arrays.get(0));
		}
		throw new IllegalArgumentException("Union of this kind of types is not (yet) supported: "+types);
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
		public PartialCollection<YValueHint> getHintValues(YType type, DynamicSchemaContext dc) {
			return ((AbstractType)type).getHintValues(dc);
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
		public SchemaContextAware<ValueParser> getValueParser(YType type) {
			return ((AbstractType)type).getParser();
		}

		@Override
		public YType inferMoreSpecificType(YType type, DynamicSchemaContext schemaContext) {
			YType better = ((AbstractType)type).inferMoreSpecificType(schemaContext);
			while (better!=null && better!=type) {
				type = better;
				better = ((AbstractType)type).inferMoreSpecificType(schemaContext);
			}
			//Can only get here if either 'better' is null or better==type
			return type;
		}

		@Override
		public List<Constraint> getConstraints(YType type) {
			return ((AbstractType)type).getConstraints();
		}

		@Override
		public ISubCompletionEngine getCustomContentAssistant(YType type) {
			return ((AbstractType)type).getCustomContentAssistant();
		}

		@Override
		public TypeBasedSnippetProvider getSnippetProvider() {
			return snippetProvider;
		}

		@Override
		public boolean tieredOptionalPropertyProposals() {
			return enableTieredOptionalPropertyProposals;
		}

		@Override
		public boolean suggestDeprecatedProperties() {
			return suggestDeprecatedProperties;
		}

		@Override
		public Collection<YType> getUnionSubTypes(YType type) {
			if (type instanceof AbstractUnionType) {
				return ((AbstractUnionType) type).getUnionSubTypes();
			}
			return ImmutableList.of(type);
		}
	};

	/////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Provides default implementations for all YType methods.
	 */
	public static abstract class AbstractType implements YType {

		private SchemaContextAware<ValueParser> parser;
		private List<YTypedProperty> propertyList = new ArrayList<>();
		private List<YValueHint> hints = new ArrayList<>();
		private Map<String, YTypedProperty> cachedPropertyMap;
		private SchemaContextAware<PartialCollection<YValueHint>> hintProvider;
			//TODO: SchemaContextAware now allows throwing exceptions so should be able to simplify the above to SchemaContextAware<Collection<YValueHint>>

		private List<Constraint> constraints = new ArrayList<>(2);
		private ISubCompletionEngine customContentAssistant = null;

		public boolean isSequenceable() {
			return false;
		}

		public ISubCompletionEngine getCustomContentAssistant() {
			return customContentAssistant;
		}

		public AbstractType setCustomContentAssistant(ISubCompletionEngine customContentAssistant) {
			this.customContentAssistant = customContentAssistant;
			return this;
		}

		public YType inferMoreSpecificType(DynamicSchemaContext dc) {
			return this;
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

		public AbstractType setHintProvider(Callable<Collection<YValueHint>> hintProvider) {
			setHintProvider((DynamicSchemaContext dc) -> PartialCollection.compute(hintProvider));
			return this;
		}

		public AbstractType setHintProvider(SchemaContextAware<PartialCollection<YValueHint>> hintProvider) {
			//TODO: SchemaContextAware now allows throwing exceptions so should be able to simplify the above to SchemaContextAware<Collection<YValueHint>>
			this.hintProvider = hintProvider;
			return this;
		}

		public PartialCollection<YValueHint> getHintValues(DynamicSchemaContext dc) {
			return getProviderHints(dc)
					.addAll(hints);
		}

		/**
		 * Prevents adding additional hints.
		 */
		public void sealHints() {
			hints = ImmutableList.copyOf(hints);
		}

		private PartialCollection<YValueHint> getProviderHints(DynamicSchemaContext dc) {
			if (hintProvider != null) {
				try {
					return hintProvider.withContext(dc);
				} catch (Exception e) {
					return PartialCollection.unknown(e);
				}
			}
			return PartialCollection.empty();
		}

		public List<Constraint> getConstraints() {
			return ImmutableList.copyOf(constraints);
		}

		public List<YTypedProperty> getProperties() {
			return Collections.unmodifiableList(propertyList);
		}

		public Map<String, YTypedProperty> getPropertiesMap() {
			if (cachedPropertyMap==null) {
				ImmutableMap.Builder<String, YTypedProperty> builder = ImmutableMap.builder();
				for (YTypedProperty p : propertyList) {
					builder.put(p.getName(), p);
				}
				cachedPropertyMap = builder.build();
			}
			return cachedPropertyMap;
		}

		public boolean isAtomic() {
			return false;
		}

		public boolean isMap() {
			return false;
		}

		@Override
		public abstract String toString(); // force each sublcass to implement a (nice) toString method.

		public void addProperty(YTypedProperty p) {
			Assert.isNotNull(p);
			cachedPropertyMap = null;
			propertyList.add(p);
		}

		public YTypedPropertyImpl addProperty(String name, YType type, Renderable description) {
			YTypedPropertyImpl prop;
			addProperty(prop = new YTypedPropertyImpl(name, type));
			prop.setDescriptionProvider(description);
			return prop;
		}

		public void addProperty(String name, YType type) {
			addProperty(new YTypedPropertyImpl(name, type));
		}
		public AbstractType addHints(String... strings) {
			return addHints(Arrays.stream(strings).map(s -> new BasicYValueHint(s)).toArray(BasicYValueHint[]::new));
		}

		public AbstractType addHints(YValueHint... extraHints) {
			for (YValueHint h : extraHints) {
				if (!hints.contains(h)) {
					hints.add(h);
				}
			}
			return this;
		}

		public void parseWith(SchemaContextAware<ValueParser> parser) {
			this.parser = parser;
		}

		public AbstractType parseWith(ValueParser parser) {
			parseWith((DynamicSchemaContext dc) -> parser);
			return this;
		}
		public SchemaContextAware<ValueParser> getParser() {
			return parser;
		}

		/**
		 * Modifies currently installed parser so it is guaranteed to accept at least given values.
		 * @return
		 */
		public AbstractType alsoAccept(String... _values) {
			if (parser!=null) {
				ImmutableSet<String> values = ImmutableSet.copyOf(_values);
				final SchemaContextAware<ValueParser> oldParserProvider = parser;
				parser = (dc) -> (s) -> {
					if (values.contains(s)) {
						return s;
					} else {
						ValueParser oldParser = oldParserProvider.safeWithContext(dc).orElse(null);
						if (oldParser!=null) {
							return oldParser.parse(s);
						}
						return s;
					}
				};
			}
			return this;
		}

		public AbstractType require(Constraint dynamicConstraint) {
			this.constraints.add(dynamicConstraint);
			return this;
		}

		public void requireOneOf(String... properties) {
			this.constraints.add(Constraints.requireOneOf(properties));
		}

		public String[] getPropertyNames() {
			return getProperties().stream()
					.map(YTypedProperty::getName)
					.collect(Collectors.toCollection(TreeSet::new))
					.toArray(new String[0]);
		}
	}

	/**
	 * Represents a type that depends on the DynamicSchemaContext
	 */
	public static class YContextSensitive extends AbstractType {

		private final SchemaContextAware<YType> typeGuesser;
		private final String name;

		private boolean isAtomic = true;
		private boolean isMap = true;
		private boolean isBean = true;
		private boolean isSeq = true;

		public YContextSensitive(String name, SchemaContextAware<YType> typeGuesser) {
			this.name = name;
			this.typeGuesser = typeGuesser;
		}

		@Override
		public YType inferMoreSpecificType(DynamicSchemaContext dc) {
			if (dc!=null) {
				return typeGuesser.safeWithContext(dc).orElse(this);
			}
			return this;
		}

		@Override
		public boolean isAtomic() {
			return isAtomic;
		}

		@Override
		public boolean isSequenceable() {
			return isSeq;
		}

		@Override
		public boolean isMap() {
			return isMap;
		}

		@Override
		public boolean isBean() {
			return isBean;
		}

		@Override
		public String toString() {
			return name;
		}

		/**
		 * Treat this type as an atomic type (i.e. it can't be a map or sequence), when not yet inferred to a
		 * more specific version of itself).
		 */
		public AbstractType treatAsAtomic() {
			this.isAtomic = true;
			this.isMap = false;
			this.isBean = false;
			this.isSeq = false;
			return this;
		}

		public AbstractType treatAsBean() {
			this.isAtomic = false;
			this.isMap = false;
			this.isBean = true;
			this.isSeq = false;
			return this;
		}
	}


	/**
	 * Represents a type that is completely unconstrained. Anything goes: A map, a sequence or some
	 * atomic value.
	 */
	public static class YAny extends AbstractType {
		private final String name;

		public YAny(String name) {
			this.name = name;
		}

		@Override
		public boolean isAtomic() {
			return true;
		}

		@Override
		public boolean isSequenceable() {
			return true;
		}

		@Override
		public boolean isMap() {
			return true;
		}

		@Override
		public String toString() {
			return name;
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

		public YSeqType notEmpty() {
			require((DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) -> {
				SequenceNode seq = (SequenceNode) node;
				if (seq.getValue().size()==0) {
					problems.accept(YamlSchemaProblems.schemaProblem("At least one '"+el+"' is required", node));
				}
			});
			return this;
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

		@Override
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

	/**
	 * Represents a union of several bean types. It is assumed one primary property
	 * exists in each of the sub-bean types that can be used to identify the type.
	 * In other words the primary property has a unique name so that when
	 * this property is being assigned a value we can infer from that which
	 * specific bean-type we are dealing with.
	 */
	public static class YBeanUnionType extends AbstractType {
		private final String name;
		private List<YBeanType> types;

		private Map<String, AbstractType> typesByPrimary;
		private ImmutableList<YTypedProperty> primaryProps;

		public YBeanUnionType(String name, YBeanType... types) {
			this.name = name;
			this.types = new ArrayList<>();
			for (YBeanType t : types) {
				addUnionMember(t);
			}
		}

		private void addUnionMember(YBeanType type) {
			types.add(type);
		}

		private String findPrimary(AbstractType t, List<YBeanType> types) {
			//Note: passing null dynamic context below is okay, assuming the properties in YBeanType
			// do not care about dynamic context.
			for (YTypedProperty p : t.getProperties()) {
				String name = p.getName();
				if (isUniqueFor(name, t, types)) {
					return name;
				}
			}
			Assert.isLegal(false, "Couldn't find a unique property key for "+t);
			return null; //unreachable, but compiler doesn't know.
		}
		private boolean isUniqueFor(String name, AbstractType t, List<YBeanType> types) {
			for (AbstractType other : types) {
				if (other!=t) {
					//Note: passing null dynamic context below is okay, assuming the properties in YBeanType
					// do not care about dynamic context.
					if (other.getPropertiesMap().containsKey(name)) {
						return false;
					}
				}
			}
			return true;
		}
		@Override
		public String toString() {
			return name;
		}
		@Override
		public boolean isBean() {
			return true;
		}

		@Override
		public List<YTypedProperty> getProperties() {
			//Reaching here means we couldn't guess the type from existing props.
			//We'll just return the primary properties, these are good to give as hints
			//then, since at least one of them should be added.
			return getPrimaryProps();
		}

		public synchronized Map<String, AbstractType> typesByPrimary() {
			if (typesByPrimary==null) {
				//To ensure that the map of 'typesByPrimary' is never stale, make the list of
				// types immutable at this point. The assumption here is that union can be
				// built up flexibly using mutation ops during initialization, but once it
				// starts being used it becomes immutable.
				types = ImmutableList.copyOf(types);
				ImmutableMap.Builder<String, AbstractType> builder = ImmutableMap.builder();
				for (YType _t : types) {
					AbstractType t = (AbstractType)_t;
					builder.put(findPrimary(t, types), t);
				}
				typesByPrimary = builder.build();
			}
			return typesByPrimary;
		}

		private List<YTypedProperty> getPrimaryProps() {
			if (primaryProps==null) {
				Builder<YTypedProperty> builder = ImmutableList.builder();
				for (Entry<String, AbstractType> entry : typesByPrimary().entrySet()) {
					builder.add(entry.getValue().getPropertiesMap().get(entry.getKey()));
				}
				primaryProps = builder.build();
			}
			return primaryProps;
		}

		@Override
		public YType inferMoreSpecificType(DynamicSchemaContext dc) {
			Set<String> existingProps = dc.getDefinedProperties();
			if (!existingProps.isEmpty()) {
				for (Entry<String, AbstractType> entry : typesByPrimary().entrySet()) {
					String primaryName = entry.getKey();
					if (existingProps.contains(primaryName)) {
						return entry.getValue();
					}
				}
			}
			return super.inferMoreSpecificType(dc);
		}
	}

	public static class AbstractUnionType extends AbstractType {
		protected final String name;
		protected final YType[] subtypes;
		
		public AbstractUnionType(String name, YType... subTypes) {
			this.name = name;
			this.subtypes = subTypes;
		}
		@Override
		public final String toString() {
			if (name!=null) {
				return name;
			} else {
				StringBuilder b = new StringBuilder("(");
				boolean first = true;
				for (YType t : subtypes) {
					if (!first) {
						b.append(" | ");
					}
					b.append(t);
					first = false;
				}
				b.append(")");
				return b.toString();
			}
		}
		
		public Collection<YType> getUnionSubTypes() {
			return ImmutableList.copyOf(subtypes);
		}
	}

	public class YAtomAndMapUnion extends AbstractUnionType {

		private YAtomicType atom;
		private YMapType map;

		public YAtomAndMapUnion(String name, YAtomicType atom, YMapType map) {
			super(name, atom, map);
			this.atom = atom;
			this.map = map;
		}

		@Override
		public boolean isAtomic() {
			return true;
		}

		@Override
		public boolean isMap() {
			return true;
		}

		@Override
		public YType inferMoreSpecificType(DynamicSchemaContext dc) {
			if (dc.isAtomic()) {
				return atom;
			} else if (dc.isMap()) {
				return map;
			}
			return super.inferMoreSpecificType(dc);
		}

		@Override
		public PartialCollection<YValueHint> getHintValues(DynamicSchemaContext dc) {
			return atom.getHintValues(dc).addAll(map.getHintValues(dc));
		}

	}
	
	public class YBeanAndSequenceUnion extends AbstractUnionType {

		private final YBeanType bean;
		private final YSeqType seq;

		public YBeanAndSequenceUnion(String name, YBeanType yBeanType, YSeqType ySeqType) {
			super(name, yBeanType, ySeqType);
			this.bean = yBeanType;
			this.seq = ySeqType;
		}

		@Override
		public YType inferMoreSpecificType(DynamicSchemaContext dc) {
			if (dc.isMap()) {
				return bean;
			} else if (dc.isSequence()) {
				return seq;
			}
			return super.inferMoreSpecificType(dc);
		}

		@Override
		public boolean isBean() {
			return true;
		}
		
		@Override
		public boolean isSequenceable() {
			return true;
		}
	}
	
	public class YAtomAndSequenceUnion extends AbstractUnionType {
		private final YAtomicType atomic;
		private final YSeqType seq;
		
		public YAtomAndSequenceUnion(String name, YAtomicType atomic, YSeqType seq) {
			super(name, atomic, seq);
			this.atomic = atomic;
			this.seq = seq;
		}
		@Override
		public YType inferMoreSpecificType(DynamicSchemaContext dc) {
			if (dc.isAtomic()) {
				return atomic;
			} else if (dc.isSequence()) {
				return seq;
			}
			return super.inferMoreSpecificType(dc);
		}

		@Override
		public boolean isAtomic() {
			return true;
		}
		
		@Override
		public boolean isSequenceable() {
			return true;
		}
	}

	public static class YTypedPropertyImpl implements YTypedProperty, Cloneable {

		final private String name;
		final private YType type;
		private Renderable description = Renderables.NO_DESCRIPTION;
		private boolean isRequired;
		private boolean isDeprecated;
		private boolean isPrimary;
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
		public Renderable getDescription() {
			return description;
		}

		public YTypedPropertyImpl setDescriptionProvider(Renderable description) {
			this.description = description;
			return this;
		}

		public YTypedPropertyImpl isRequired(boolean b) {
			this.isRequired = b;
			return this;
		}

		@Override
		public boolean isRequired() {
			return isRequired;
		}

		public void isDeprecated(boolean isDeprecated) {
			this.isDeprecated = isDeprecated;
		}
		public YTypedPropertyImpl isDeprecated(String deprecationMessage) {
			this.isDeprecated = deprecationMessage!=null;
			this.deprecationMessage = deprecationMessage;
			return this;
		}

		@Override
		public String getDeprecationMessage() {
			return this.deprecationMessage;
		}

		@Override
		public boolean isDeprecated() {
			return this.isDeprecated;
		}


		public YTypedPropertyImpl isPrimary(boolean primary) {
			this.isPrimary = primary;
			this.isRequired = primary;
			return this;
		}

		public YTypedPropertyImpl isPrimary(boolean primary, boolean required) {
			this.isPrimary = primary;
			this.isRequired = required;
			return this;
		}

		@Override
		public boolean isPrimary() {
			return isPrimary;
		}

		public YTypedPropertyImpl copy() {
			try {
				return (YTypedPropertyImpl) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	public YAtomicType yatomic(String name) {
		return new YAtomicType(name);
	}

	public YTypedPropertyImpl yprop(String name, YType type) {
		return new YTypedPropertyImpl(name, type);
	}

	public YTypedPropertyImpl yprop(YTypedProperty prop) {
		return ((YTypedPropertyImpl)prop).copy();
	}

	public YAtomicType yenumFromHints(String name, SchemaContextAware<BiFunction<String, Collection<String>, String>> errorMessageFormatter, SchemaContextAware<PartialCollection<YValueHint>> values) {
		YAtomicType t = yatomic(name);
		t.setHintProvider(values);
		t.parseWith((DynamicSchemaContext dc) -> {
			PartialCollection<YValueHint> hints = PartialCollection.fromCallable(() -> values.withContext(dc));
			return new EnumValueParser(name, hints.map(h -> h.getValue())) {
				@Override
				protected String createErrorMessage(String parseString, Collection<String> values) {
					try {
						return errorMessageFormatter.withContext(dc).apply(parseString, values);
					} catch (Exception e) {
						return super.createErrorMessage(parseString, values);
					}
				}
			};
		});
		return t;
	}

	public YAtomicType yenumFromDynamicValues(String name,
			SchemaContextAware<BiFunction<String, Collection<String>, String>> errorMessageFormatter,
			SchemaContextAware<PartialCollection<String>> values
	) {
		return yenumFromHints(name,
				//Error message formatter:
				errorMessageFormatter,
				//Hints provider:
				(dc) -> hints(values.withContext(dc))
		);
	}

	public YAtomicType yenumFromDynamicValues(String name, SchemaContextAware<PartialCollection<String>> values) {
		return yenumFromHints(name,
				//Error message formatter:
				(dc) -> (parseString, validValues) -> "'"+parseString+"' is an unknown '"+name+"'. Valid values are: "+validValues,
				//Hints provider:
				(dc) -> hints(values.withContext(dc))
		);
	}

	public EnumTypeBuilder yenumBuilder(String name, String... values) {
		return new EnumTypeBuilder(name, values);
	}

	public YAtomicType yenum(String name, SchemaContextAware<BiFunction<String, Collection<String>, String>> errorMessageFormatter, SchemaContextAware<Collection<String>> values) {
		YAtomicType t = yatomic(name);
		t.setHintProvider((dc) -> {
			return PartialCollection.compute(() -> values.withContext(dc))
					.map(BasicYValueHint::new);
		});
		t.parseWith((DynamicSchemaContext dc) -> {
			EnumValueParser enumParser = new EnumValueParser(name, values.withContext(dc)) {
				@Override
				protected String createErrorMessage(String parseString, Collection<String> values) {
					try {
						return errorMessageFormatter.withContext(dc).apply(parseString, values);
					} catch (Exception e) {
						return super.createErrorMessage(parseString, values);
					}
				}
			};
			return enumParser;
		});
		return t;

	}

	public YAtomicType yenum(String name, BiFunction<String, Collection<String>, String> errorMessageFormatter, SchemaContextAware<Collection<String>> values) {
		return yenum(name, (dc) -> errorMessageFormatter,  values);
	}

	/**
	 * Like an enum, checks a given reference matches one of a list of known values. Except that the meaning of 'matching' is based on glob pattern matching 
	 * instead of simple string equality. 
	 * <p>
	 * See: https://github.com/spring-projects/sts4/issues/639
	 */
	public AbstractType yGlobEnum(String name, BiFunction<String, Collection<String>, String> errorMessageFormatter , SchemaContextAware<Collection<String>> values) {
		return yGlobEnum(name, (dc) -> errorMessageFormatter,  values);
	}

	public YAtomicType yGlobEnum(String name, SchemaContextAware<BiFunction<String, Collection<String>, String>> errorMessageFormatter, SchemaContextAware<Collection<String>> values) {
		YAtomicType t = yatomic(name);
		t.setHintProvider((dc) -> {
			return PartialCollection.compute(() -> values.withContext(dc))
					.map(BasicYValueHint::new);
		});
		t.parseWith((DynamicSchemaContext dc) -> {
			EnumValueParser enumParser = new EnumValueParser(name, values.withContext(dc)) {
				@Override
				protected String createErrorMessage(String parseString, Collection<String> values) {
					try {
						return errorMessageFormatter.withContext(dc).apply(parseString, values);
					} catch (Exception e) {
						return super.createErrorMessage(parseString, values);
					}
				}
				
				protected boolean hasMatchingValue(String stringOrGlob, Collection<String> values) {
					SimpleGlob glob = SimpleGlob.create(stringOrGlob);
					//Note we accept 'Match.UNKNOWN' as if it is a proper match. This is the conservative thing to do 
					// in this situation to avoid false positives (i.e. we only report an error if we are *sure* that no
					// value matches.
					return values.stream().anyMatch(s -> glob.match(s)!=Match.FAIL);
				}
			};
			return enumParser;
		});
		return t;

	}

	public static Collection<String> values(Collection<YValueHint> hints) {
		return hints == null ? null : hints.stream().map(YValueHint::getValue).collect(Collectors.toList());
	}

	public YAtomicType yenum(String name, String... values) {
		return new EnumTypeBuilder(name, values).build();
	}

	public static Callable<Collection<String>> valuesFromHintProvider(Callable<Collection<YValueHint>> hintProvider) {
		Callable<Collection<String>> values = () -> {
			Collection<YValueHint> hints = hintProvider.call();
			if (hints != null) {
				ImmutableSet.Builder<String> builder = ImmutableSet.builder();
				for (YValueHint hint : hints ) {
					builder.add(hint.getValue());
				}
				return builder.build();
			}
			return null;
		};
		return values;
	}

	public static YValueHint hint(String value, String label) {
		return new BasicYValueHint(value, label);
	}

	public static YValueHint hint(String value) {
		return new BasicYValueHint(value);
	}

	public static Collection<YValueHint> hints(Collection<String> values) {
		return values.stream()
				.map(YTypeFactory::hint)
				.collect(CollectorUtil.toMultiset());
	}

	public static PartialCollection<YValueHint> hints(PartialCollection<String> values) {
		if (values!=null) {
			return values.map(YTypeFactory::hint);
		}
		return PartialCollection.unknown();
	}

	public YTypeFactory enableTieredProposals(boolean enable) {
		this.enableTieredOptionalPropertyProposals = enable;
		return this;
	}

	public YTypeFactory suggestDeprecatedProperties(boolean enable) {
		this.suggestDeprecatedProperties = enable;
		return this;
	}

	public YTypeFactory setSnippetProvider(TypeBasedSnippetProvider snippetProvider) {
		this.snippetProvider = snippetProvider;
		return this;
	}

}
