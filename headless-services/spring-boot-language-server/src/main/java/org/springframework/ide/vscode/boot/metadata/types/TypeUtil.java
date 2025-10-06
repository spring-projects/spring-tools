/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.metadata.types;

import static org.springframework.ide.vscode.commons.util.ArrayUtils.firstElement;
import static org.springframework.ide.vscode.commons.util.ArrayUtils.lastElement;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.ResourceHintProvider;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry.ValueProviderStrategy;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.boot.metadata.util.DeprecationUtil;
import org.springframework.ide.vscode.boot.metadata.util.PropertyDocUtils;
import org.springframework.ide.vscode.boot.properties.reconcile.BootEnumValueParser;
import org.springframework.ide.vscode.commons.java.Flags;
import org.springframework.ide.vscode.commons.java.IField;
import org.springframework.ide.vscode.commons.java.IJavaElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IJavaType;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.util.AlwaysFailingParser;
import org.springframework.ide.vscode.commons.util.ArrayUtils;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.LazyProvider;
import org.springframework.ide.vscode.commons.util.MimeTypes;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

/**
 * Utilities to work with types represented as Strings as returned by
 * Spring config metadata apis.
 *
 * @author Kris De Volder
 */
public class TypeUtil {

	private static final Logger log = LoggerFactory.getLogger(TypeUtil.class);

	private static abstract class RadixableParser implements ValueParser {
		protected abstract Object parse(String str, int radix);

		@Override
		public Object parse(String str) {
			if (str.startsWith("0")) {
				if (str.startsWith("0x")||str.startsWith("0X")) {
					return parse(str.substring(2), 16);
				} else if (str.startsWith("0b") || str.startsWith("0B")) {
					return parse(str.substring(2), 2);
				} else {
					return parse(str, 8);
				}
			}
			return parse(str, 10);
		}

	}

	private static final Object OBJECT_TYPE_NAME = Object.class.getName();
	private static final String STRING_TYPE_NAME = String.class.getName();
	private static final String MAP_TYPE_NAME = Map.class.getName();
	private static final String SET_TYPE_NAME = Set.class.getName();
	private static final String LIST_TYPE_NAME = List.class.getName();
	private static final String INET_ADDRESS_TYPE_NAME = InetAddress.class.getName();
	private static final String DURATION_TYPE_NAME = Duration.class.getName();
	private static final String CLASS_TYPE_NAME = Class.class.getName();

	public enum BeanPropertyNameMode {
		HYPHENATED(true,false), //bean property name in hyphenated form. E.g 'some-property-name'
		CAMEL_CASE(false,true), //bean property name in camelCase. E.g. 'somePropertyName'
		ALIASED(true,true);     //use both as aliases of one another.

		private final boolean includesHyphenated;
		private final boolean includesCamelCase;

		BeanPropertyNameMode(boolean hyphenated, boolean camelCase) {
			this.includesCamelCase = camelCase;
			this.includesHyphenated = hyphenated;
		}

		public boolean includesHyphenated() {
			return includesHyphenated;
		}

		public boolean includesCamelCase() {
			return includesCamelCase;
		}
	}

	public enum EnumCaseMode {
		LOWER_CASE, //convert enum names to lower case
		ORIGNAL,    //keep orignal enum name
		ALIASED     //use both lower-cased and original names as aliases of one another
	}

	private IJavaProject javaProject;
	private SourceLinks sourceLinks;

	public TypeUtil(SourceLinks sourceLinks, IJavaProject jp) {
		//Note javaProject is allowed to be null, but only in unit testing context
		// (This is so some tests can be run without an explicit jp needing to be created)
		this.javaProject = jp;
		this.sourceLinks = sourceLinks;
	}


	public TypeUtil(SourceLinks sourceLinks, Optional<IJavaProject> maybeProject) {
		this(sourceLinks, maybeProject.orElse(null));
	}

	private static final Map<String, String> PRIMITIVE_TYPE_NAMES = new HashMap<>();
	private static final Map<String, Type> PRIMITIVE_TO_BOX_TYPE = new HashMap<>();
	static {
		PRIMITIVE_TYPE_NAMES.put("java.lang.Boolean", "boolean");

		PRIMITIVE_TYPE_NAMES.put("java.lang.Byte", "byte");
		PRIMITIVE_TYPE_NAMES.put("java.lang.Short", "short");
		PRIMITIVE_TYPE_NAMES.put("java.lang.Integer","int");
		PRIMITIVE_TYPE_NAMES.put("java.lang.Long", "long");

		PRIMITIVE_TYPE_NAMES.put("java.lang.Double", "double");
		PRIMITIVE_TYPE_NAMES.put("java.lang.Float", "float");

		PRIMITIVE_TYPE_NAMES.put("java.lang.Character", "char");

		for (Entry<String, String> e : PRIMITIVE_TYPE_NAMES.entrySet()) {
			PRIMITIVE_TO_BOX_TYPE.put(e.getValue(), new Type(e.getKey(), null));
		}
	}

	public static final Type INTEGER_TYPE = new Type("java.lang.Integer", null);

	private static final Set<String> ASSIGNABLE_TYPES = new HashSet<>(Arrays.asList(
			"java.lang.Boolean",
			"java.lang.String",
			"java.lang.Short",
			"java.lang.Integer",
			"java.lang.Long",
			"java.lang.Double",
			"java.lang.Float",
			"java.lang.Character",
			"java.lang.Byte",
			INET_ADDRESS_TYPE_NAME,
			DURATION_TYPE_NAME,
			CLASS_TYPE_NAME,
			"java.lang.String[]"
	));

	private static final Set<String> ATOMIC_TYPES = new HashSet<>(PRIMITIVE_TYPE_NAMES.keySet());
	static {
		ATOMIC_TYPES.add(INET_ADDRESS_TYPE_NAME);
		ATOMIC_TYPES.add(DURATION_TYPE_NAME);
		ATOMIC_TYPES.add(STRING_TYPE_NAME);
		ATOMIC_TYPES.add(CLASS_TYPE_NAME);
	}

	private static final Map<String, String[]> TYPE_VALUES = new HashMap<>();
	static {
		TYPE_VALUES.put("java.lang.Boolean", new String[] { "true", "false" });
	}

	private static final Map<String,ValueParser> VALUE_PARSERS = new HashMap<>();
	static {
		VALUE_PARSERS.put(Byte.class.getName(), new RadixableParser() {
			@Override
			public Object parse(String str, int radix) {
				return Byte.parseByte(str, radix);
			}
		});
		VALUE_PARSERS.put(Integer.class.getName(), new RadixableParser() {
			@Override
			public Object parse(String str, int radix) {
				return Integer.parseInt(str, radix);
			}
		});
		VALUE_PARSERS.put(Long.class.getName(), new RadixableParser() {
			@Override
			public Object parse(String str, int radix) {
				return Long.parseLong(str, radix);
			}
		});
		VALUE_PARSERS.put(Short.class.getName(), new RadixableParser() {
			@Override
			public Object parse(String str, int radix) {
				return Short.parseShort(str, radix);
			}
		});
		VALUE_PARSERS.put(Double.class.getName(), new ValueParser() {
			@Override
			public Object parse(String str) {
				return Double.parseDouble(str);
			}
		});
		VALUE_PARSERS.put(Float.class.getName(), new ValueParser() {
			@Override
			public Object parse(String str) {
				return Float.parseFloat(str);
			}
		});
		VALUE_PARSERS.put(Boolean.class.getName(), new ValueParser() {
			@Override
			public Object parse(String str) {
				//The 'more obvious' implementation is too liberal and accepts anything as okay.
				//return Boolean.parseBoolean(str);
				str = str.toLowerCase();
				if (str.equals("true")) {
					return true;
				} else if (str.equals("false")) {
					return false;
				}
				throw new IllegalArgumentException("Value should be 'true' or 'false'");
			}
		});
		VALUE_PARSERS.put(DURATION_TYPE_NAME, (s) -> {
			try {
				return DurationStyle.detectAndParse(s);
			} catch (IllegalArgumentException e) {
				throw new ValueParseException(e.getMessage());
			}
		});
	}

	public ValueParser getValueParser(Type type) {
		if (type!=null) {
			ValueParser simpleParser = VALUE_PARSERS.get(type.getErasure());
			if (simpleParser!=null) {
				return simpleParser;
			}
			Collection<StsValueHint> enumValues = getAllowedValues(type, EnumCaseMode.ALIASED);
			if (enumValues!=null) {
				//Note, technically if 'enumValues is empty array' this means something different
				// from when it is null. An empty array means a type that has no values, so
				// assigning anything to it is an error.
				return new BootEnumValueParser(niceTypeName(type), getBareValues(enumValues));
			}
			if (isMap(type)) {
				//Trying to parse map types from scalars is not possible. Thus we
				// provide a parser that allows throws
				return new AlwaysFailingParser(niceTypeName(type));
			}
			if (isSequencable(type)) {
				//Trying to parse list from scalars is possible if the domain type is parseable. Spring boot
				// will try to interpret the string as a comma-separated list
				Type elType = getDomainType(type);
				if (elType!=null) {
					ValueParser elParser = getValueParser(elType);
					if (elParser!=null) {
						return new DelimitedStringParser(elParser);
					}
				}
			}
		}
		return null;
	}

	private String[] getBareValues(Collection<StsValueHint> hints) {
		if (hints!=null) {
			String[] values = new String[hints.size()];
			int i = 0;
			for (StsValueHint h : hints) {
				values[i++] = h.getValue();
			}
			return values;
		}
		return null;
	}

	/**
	 * @return An array of allowed values for a given type. If an array is returned then
	 * *only* values in the array are valid and using any other value constitutes an error.
	 * This may return null if allowedValues list is unknown or the type is not characterizable
	 * as a simple enumaration of allowed values.
	 * @param caseMode determines whether Enum values are returned in 'lower case form', 'orignal form',
	 * or 'aliased' (meaning both forms are returned).
	 */
	public Collection<StsValueHint> getAllowedValues(Type enumType, EnumCaseMode caseMode) {
		if (enumType!=null) {
			try {
				String[] values = TYPE_VALUES.get(enumType.getErasure());
				if (values!=null) {
					if (caseMode==EnumCaseMode.ALIASED) {
						ImmutableSet.Builder<String> aliased = ImmutableSet.builder();
						aliased.add(values);
						for (int i = 0; i < values.length; i++) {
							aliased.add(values[i].toUpperCase());
						}
						return aliased.build().stream().map(StsValueHint::create).collect(Collectors.toList());
					} else {
						return Arrays.stream(values).map(StsValueHint::create).collect(Collectors.toList());
					}
				}
				IType type = findType(enumType.getErasure());
				if (type!=null && type.isEnum()) {
					ImmutableList.Builder<StsValueHint> enums = ImmutableList.builder();
					boolean addOriginal = caseMode == EnumCaseMode.ORIGNAL || caseMode == EnumCaseMode.ALIASED;
					boolean addLowerCased = caseMode == EnumCaseMode.LOWER_CASE || caseMode == EnumCaseMode.ALIASED;

					type.getFields().filter(f -> f.isEnumConstant()).forEach(f -> {
						String rawName = f.getElementName();
						if (addOriginal) {
							enums.add(StsValueHint.create(sourceLinks, rawName, javaProject, f));
						}
						if (addLowerCased) {
							enums.add(StsValueHint.create(sourceLinks, StringUtil.upperCaseToHyphens(rawName), javaProject, f));
						}
					});
					return enums.build();
				}
			} catch (Exception e) {
				log.error("", e);
			}
		}
		return null;
	}

	public String niceTypeName(Type _type) {
		StringBuilder buf = new StringBuilder();
		niceTypeName(_type, buf);
		return buf.toString();
	}

	public void niceTypeName(Type type, StringBuilder buf) {
		if (type==null) {
			buf.append("null");
			return;
		}
		String typeStr = type.getErasure();
		String primTypeName = PRIMITIVE_TYPE_NAMES.get(typeStr);
		if (primTypeName!=null) {
			buf.append(primTypeName);
		} else if (typeStr.startsWith("java.lang.")) {
			buf.append(typeStr.substring("java.lang.".length()));
		} else if (typeStr.startsWith("java.util.")) {
			buf.append(typeStr.substring("java.util.".length()));
		} else {
			buf.append(typeStr);
		}
		if (isEnum(type)) {
			Collection<StsValueHint> values = getAllowedValues(type, EnumCaseMode.ORIGNAL);
			if (values!=null && !values.isEmpty()) {
				buf.append("[");
				int i = 0;
				for (StsValueHint hint : values) {
					if (i>0) {
						buf.append(", ");
					}
					buf.append(hint.getValue());
					i++;
					if (i>=4) {
						break;
					}
				}
				if (i<values.size()) {
					buf.append(", ...");
				}
				buf.append("]");
			}
		} else if (type.isGeneric()) {
			Type[] params = type.getParams();
			buf.append("<");
			for (int i = 0; i < params.length; i++) {
				if (i>0) {
					buf.append(", ");
				}
				niceTypeName(params[i], buf);
			}
			buf.append(">");
		}
	}


	/**
	 * @return true if it is reasonable to navigate given type with '.' notation. This returns true
	 * by default except for some specific cases we assume are not 'dotable' such as Primitive types
	 * and String
	 */
	public boolean isDotable(Type type) {
		String typeName = type.getErasure();
		if (typeName.equals("java.lang.Object")) {
			//special case. Treat as 'non dotable' type. This mainly for stuff like logging.level
			// declared as Map<String,Object> so it would 'eat' the dots into the key.
			// also it makes sense to treat Object as 'non-dotable' since we cannot determine properties
			// for such an abstract type (as Object itself has no setters).
			return false;
		}
		return !isAtomic(type);
	}

	public static boolean isObject(Type type) {
		return type!=null && OBJECT_TYPE_NAME.equals(type.getErasure());
	}

	public static boolean isString(Type type) {
		return type!=null && STRING_TYPE_NAME.equals(type.getErasure());
	}

	public boolean isAtomic(Type type) {
		if (type!=null) {
			String typeName = type.getErasure();
			return ATOMIC_TYPES.contains(typeName) || isEnum(type);
		}
		return false;
	}

	/**
	 * Check if it is valid to use the notation <name>[<index>]=<value> in property file
	 * for properties of this type.
	 * <p>
	 * Note it is also possible to use 'bracket' notation with other types such as Map's for example.
	 * This method returns true only for types that support/expect using of integer indexes.
	 */
	public boolean isIndexable(Type type) {
		//Note array types where once not considered 'indexable'
		//see: STS-4031

		//However...
		//Seems that in Boot 1.3 arrays are now 'Indexable' and funcion much equivalnt to list (even including 'autogrowing' them).
		//This is actually more logical too.
		//So '[' notation in props file can be used for either list or arrays (at least in recent versions of boot).
		//Note also 'Set' are now considered bracketable. See: https://www.pivotaltracker.com/story/show/154644992
		return isArray(type) || isCollection(LIST_TYPE_NAME, type) || isCollection(SET_TYPE_NAME, type);
	}

	/**
	 * Check if type can be treated / represented as a sequence node in .yml file
	 */
	public boolean isSequencable(Type type) {
		return isIndexable(type);
	}
	
	public boolean isObjectOrSequence(Type type) {
		return OBJECT_TYPE_NAME.equals(type.getErasure());
	}

	private static boolean isArray(Type type) {
		return type!=null && type.getErasure().endsWith("[]");
	}

	public boolean isMap(Type type) {
		if (type!=null) {
			String erasure = type.getErasure();
			if (MAP_TYPE_NAME.equals(erasure)) {
				//quick / easy case. No looking for types and hierarchies required.
				return true;
			}
			try {
				IType erasureType = findType(erasure);
				return isAssignableFrom(MAP_TYPE_NAME, erasureType);
			} catch (Exception e) {
				//type not resolveable
			}
		}
		return false;
	}

	private boolean isCollection(String collectionTypeName, Type type) {
		if (type!=null) {
			String erasure = type.getErasure();
			if (collectionTypeName.equals(erasure)) {
				//quick / easy case. No looking for types and hierarchies required.
				return true;
			}
			try {
				IType erasureType = findType(erasure);
				return isAssignableFrom(collectionTypeName, erasureType);
			} catch (Exception e) {
				//type not resolveable 
			}
		}
		return false;
	}


	private boolean isAssignableFrom(String superTypeName, IType erasureType) {
		Set<String> seen = new HashSet<>();
		return searchSuperTypes(seen, erasureType, superTypeName);
	}


	private boolean searchSuperTypes(Set<String> seen, IType searchIn, String fqTargetType) {
		if (searchIn!=null) {
			String fqName = searchIn.getFullyQualifiedName();
			if (fqName.equals(fqTargetType)) {
				return true;
			}
			if (seen.add(fqName)) {
				String[] itfs = searchIn.getSuperInterfaceNames();
				if (itfs!=null) {
					for (String itfName : itfs) {
						IType itf = findType(itfName);
						if (searchSuperTypes(seen, itf, fqTargetType)) {
							return true;
						}
					}
				}
				String klassName = searchIn.getSuperclassName();
				IType klass = findType(klassName);
				if (searchSuperTypes(seen, klass, fqTargetType)) {
					return true;
				}
			}
		}
		return false;
	}


	/**
	 * Get domain type for a map or list generic type.
	 */
	public static Type getDomainType(Type type) {
		if (isArray(type)) {
			return getArrayDomainType(type);
		} else {
			return lastElement(type.getParams());
		}
	}

	private static Type getArrayDomainType(Type type) {
		if (type!=null) {
			String fullName = type.getErasure();
			Assert.isLegal(fullName.endsWith("[]"));
			String elName = fullName.substring(0, fullName.length()-2);
			return normalizePrimitiveType(new Type(elName, null));
		}
		return null;
	}

	/**
	 * Convert a type which is a 'primitive' type like 'int', 'long' etc. to its
	 * corresponding 'Boxed' type. If the type isn't a primitive type then
	 * just return it unchanged.
	 */
	private static Type normalizePrimitiveType(Type type) {
		if (type!=null) {
			String name = type.getErasure();
			Type boxType = PRIMITIVE_TO_BOX_TYPE.get(name);
			if (boxType!=null) {
				return boxType;
			}
		}
		return type;
	}

	public Type getKeyType(Type mapOrArrayType) {
		if (isSequencable(mapOrArrayType)) {
			return INTEGER_TYPE;
		} else {
			//assumed to be a map
			return firstElement(mapOrArrayType.getParams());
		}
	}

	public Type getMapKeyType(Type mapType) {
		return isMap(mapType) ? getKeyType(mapType) : null;
	}

	public boolean isAssignableType(Type type) {
		return ASSIGNABLE_TYPES.contains(type.getErasure())
				|| isEnum(type)
				|| isAssignableCollection(type);
	}

	private boolean isAssignableCollection(Type type) {
		if (isIndexable(type)) {
			Type domainType = getDomainType(type);
			return isAtomic(domainType);
		}
		return false;
	}

	public boolean isEnum(Type type) {
		try {
			if (!isArray(type)) {
				IType eclipseType = findType(type.getErasure());
				if (eclipseType!=null) {
					return eclipseType.isEnum();
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return false;
	}
	
	public IType getSuperType(IType type) {
		if (type!=null) {
			return findType(type.getSuperclassName());
		}
		return null;
	}

	private IType findType(String typeName) {
		try {
			if (javaProject!=null && typeName!=null) {
				/*
				 * Java project expects inner type separator to be '$' while properties index
				 * has '.' as the separator. Replace '.' with '$' to find inner type
				 */
				String fqName = switchInnerTypeSeparator(typeName);
				return javaProject.getIndex().findType(fqName);
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	private static String switchInnerTypeSeparator(String name) {
		if (name.indexOf('$') < 0) {
			boolean foundDeclaringType = false;
			String[] tokens = name.split("\\.");
			if (tokens.length > 0) {
				StringBuilder result = new StringBuilder();
				result.append(tokens[0]);
				for (int i = 1; i < tokens.length; i++) {
					if (!foundDeclaringType) {
						result.append('.');
						result.append(tokens[i]);
						if (!tokens[i].isEmpty() && Character.isUpperCase(tokens[i].charAt(0))) {
							foundDeclaringType = true;
						}
					} else {
						result.append('$');
						result.append(tokens[i]);
					}
				}
				return result.toString();
			}
		}
		return name;
	}

	private IType findType(Type beanType) {
		return findType(beanType.getErasure());
	}

	private static final Map<String, ValueProviderStrategy> VALUE_HINTERS = new HashMap<>();
	private static final Object JL_OBJECT = Object.class.getName();
	private static final String CONSTRUCTOR_BINDING = "org.springframework.boot.context.properties.ConstructorBinding";

	static {
		valueHints("java.nio.charset.Charset", new LazyProvider<String[]>() {
			@Override
			protected String[] compute() {
				Set<String> charsets = Charset.availableCharsets().keySet();
				return charsets.toArray(new String[charsets.size()]);
			}
		});
		valueHints("java.util.Locale", new LazyProvider<String[]>() {
			@Override
			protected String[] compute() {
				Locale[] locales = SimpleDateFormat.getAvailableLocales();
				String[] names = new String[locales.length];
				for (int i = 0; i < names.length; i++) {
					names[i] = locales[i].toString();
				}
				return names;
			}
		});
		valueHints("org.springframework.util.MimeType", new LazyProvider<String[]>() {
			@Override
			protected String[] compute() {
				return MimeTypes.getKnownMimeTypes();
			}
		});
		valueHints("org.springframework.core.io.Resource", new ResourceHintProvider());
	}

	public SourceLinks getSourceLinks() {
		return sourceLinks;
	}

	/**
	 * Determine properties that are setable on object of given type.
	 * <p>
	 * Note that this may return both null or an empty list, but they mean
	 * different things. Null means that the properties on the object are not known,
	 * and therefore reconciling should not check property validity. On the other hand
	 * returning an empty list means that there are no properties. In this case,
	 * accessing properties is invalid and reconciler should show an error message
	 * for any property access.
	 *
	 * @return A list of known properties or null if the list of properties is unknown.
	 */
	public List<TypedProperty> getProperties(Type type, EnumCaseMode enumMode, BeanPropertyNameMode beanMode) {
		if (type==null) {
			return null;
		}
		if (!isDotable(type)) {
			//If dot navigation is not valid then really this is just like saying the type has no properties.
			return Collections.emptyList();
		}
		if (isMap(type)) {
			Type keyType = getKeyType(type);
			if (keyType!=null) {
				Collection<StsValueHint> keyHints = getAllowedValues(keyType, enumMode);
				if (CollectionUtil.hasElements(keyHints)) {
					Type valueType = getDomainType(type);
					ArrayList<TypedProperty> properties = new ArrayList<>(keyHints.size());
					for (StsValueHint hint : keyHints) {
						String propName = hint.getValue();
						properties.add(new TypedProperty(propName, valueType, hint.getDescription(), hint.getDeprecation()));
					}
					return properties;
				}
			}
		} else {
			String typename = type.getErasure();
			IType typeFromIndex = findType(typename);

			//TODO: handle type parameters.
			if (typeFromIndex != null) {
				IJavaProject project = getJavaProject();
				ArrayList<TypedProperty> properties = new ArrayList<>();
				Optional<IMethod> constructorBinding = findConstructorBinding(typeFromIndex);
				if (constructorBinding.isPresent()) {
					IMethod c = constructorBinding.get();
					List<IJavaType> types = c.parameters().collect(Collectors.toList());
					List<String> params = c.getParameterNames();
					int len = Math.min(params.size(), types.size());
					for (int i = 0; i < len; i++) {
						Deprecation deprecation = null;
						properties.add(new TypedProperty(
								StringUtil.camelCaseToHyphens(params.get(i)), 
								Type.fromJavaType(types.get(i)), 
								deprecation
						));
					}
					return properties;
				} else if (typeFromIndex.isRecord()) {
					typeFromIndex.getFields().forEach(f -> properties.add(new TypedProperty(
							StringUtil.camelCaseToHyphens(f.getElementName()),
							Type.fromJavaType(f.type()),
							DeprecationUtil.extract(f)
					)));
				} else {
					getGetterMethods(typeFromIndex).forEach(m -> {
						Deprecation deprecation = DeprecationUtil.extract(m);
						Type propType = null;
						try {
							propType = Type.fromJavaType(m.getReturnType());
						} catch (Exception e) {
							log.error("", e);
						}
						if (beanMode.includesHyphenated()) {
							properties.add(new TypedProperty(getterOrSetterNameToProperty(m.getElementName()), propType,
									Renderables.lazy(() -> PropertyDocUtils.documentJavaElement(sourceLinks, project, m)), deprecation));
						}
						if (beanMode.includesCamelCase()) {
							properties.add(new TypedProperty(getterOrSetterNameToCamelName(m.getElementName()), propType,
									Renderables.lazy(() -> PropertyDocUtils.documentJavaElement(sourceLinks, project, m)), deprecation));
						}
					});
				}
				return properties;
			}
		}
		return null;
	}

	private Optional<IMethod> findConstructorBinding(IType type) {
		return type.getMethods()
		.filter(m -> m.isConstructor())
		.filter(m -> m.getAnnotations()
				.anyMatch(annotation -> 
					CONSTRUCTOR_BINDING.equals(annotation.getElementName()))
		)
		.findFirst();
	}


	/**
	 * Registers a strategy for providing value hints with a given typeName.
	 */
	public static void valueHints(String typeName, ValueProviderStrategy Supplier) {
		Assert.isLegal(!VALUE_HINTERS.containsKey(typeName)); //Only one value hinter per type is supported at the moment
		ATOMIC_TYPES.add(typeName); //valueHints typically implies that the type should be treated as atomic as well.
		ASSIGNABLE_TYPES.add(typeName); //valueHints typically implies that the type should be treated as atomic as well.
		VALUE_HINTERS.put(typeName, Supplier);
	}

	/**
	 * Registers a strategy for providing value hints with a given typeName.
	 */
	public static void valueHints(String typeName, Supplier<String[]> Supplier) {
		valueHints(typeName, new ValueProviderStrategy() {
			@Override
			public Flux<StsValueHint> getValues(IJavaProject javaProject, String query) {
				String[] values = Supplier.get();
				if (ArrayUtils.hasElements(values)) {
					return Flux.fromArray(values)
					.map(StsValueHint::create);
				}
				return Flux.empty();
			}
		});
	}

	private String getterOrSetterNameToProperty(String name) {
		String camelName = getterOrSetterNameToCamelName(name);
		return StringUtil.camelCaseToHyphens(camelName);
	}

	public String getterOrSetterNameToCamelName(String name) {
		Assert.isLegal(name.startsWith("set") || name.startsWith("get") || name.startsWith("is"));
		int prefixLen = name.startsWith("is") ? 2 : 3;
		String camelName = Character.toLowerCase(name.charAt(prefixLen)) + name.substring(prefixLen+1);
		return camelName;
	}

	private Stream<IMethod> getGetterMethods(IType eclipseType) {
		if (eclipseType != null && eclipseType.isClass() && !JL_OBJECT.equals(eclipseType.getFullyQualifiedName())) {
			Stream<IMethod> getters = eclipseType.getMethods().filter(m -> {
				if (!isStatic(m) && isPublic(m)) {
					String mname = m.getElementName();
					if ((mname.startsWith("get") && mname.length() >= 4)
							|| (mname.startsWith("is") && mname.length() >= 3)) {
						// Need at least x chars or the property name will be
						// empty.
						if (m.parameters().count() == 0) {
							return true;
						}
					}
				}
				return false;
			});
			IType superType = findType(eclipseType.getSuperclassName());
			Stream<IMethod> superGetters = getGetterMethods(superType);
			return Stream.concat(getters, superGetters);
		}
		return Stream.empty();
	}

//	private List<IMethod> getSetterMethods(IType eclipseType) {
//		try {
//			if (eclipseType!=null && eclipseType.isClass()) {
//				IMethod[] allMethods = eclipseType.getMethods();
//				if (ArrayUtils.hasElements(allMethods)) {
//					ArrayList<IMethod> setters = new ArrayList<IMethod>();
//					for (IMethod m : allMethods) {
//						String mname = m.getElementName();
//						if (mname.startsWith("set") && mname.length()>=4) {
//							//Need at least 4 chars or the property name will be empty.
//							String sig = m.getSignature();
//							int numParams = Signature.getParameterCount(sig);
//							if (numParams==1) {
//								setters.add(m);
//							}
//						}
//					}
//					return setters;
//				}
//			}
//		} catch (Exception e) {
//			BootActivator.log(e);
//		}
//		return null;
//	}

	private boolean isStatic(IMethod m) {
		try {
			return Flags.isStatic(m.getFlags());
		} catch (Exception e) {
			//Couldn't determine if it was public or not... let's assume it was NOT
			// (will result in potentially more CA completions)
			log.error("", e);
			return false;
		}
	}

	private boolean isPublic(IMethod m) {
		try {
			return m.getDeclaringType().isInterface()
				|| Flags.isPublic(m.getFlags());
		} catch (Exception e) {
			//Couldn't determine if it was public or not... let's assume it WAS
			// (will result in potentially more CA completions)
			log.error("", e);
			return true;
		}
	}

	public Map<String, TypedProperty> getPropertiesMap(Type type, EnumCaseMode enumMode, BeanPropertyNameMode beanMode) {
		//TODO: optimize, produce directly as a map instead of
		// first creating list and then coverting it.
		List<TypedProperty> list = getProperties(type, enumMode, beanMode);
		if (list!=null) {
			Map<String, TypedProperty> map = new HashMap<>();
			for (TypedProperty p : list) {
				map.put(p.getName(), p);
			}
			return map;
		}
		return null;
	}

	/**
	 * Maybe ne null in some contexts. In such context functionality will be limited because
	 * types can not be resolved.
	 */
	public IJavaProject getJavaProject() {
		return javaProject;
	}

	public IField getField(Type beanType, String propName) {
		IType type = findType(beanType);
		return getExactField(type, propName);
	}

	protected IField getExactField(IType type, String fieldName) {
		IField f = type.getField(StringUtil.hyphensToCamelCase(fieldName, false));
		if (f!=null && f.exists()) {
			return f;
		}
		return null;
	}

	public IField getEnumConstant(Type enumType, String propName) {
		IType type = findType(enumType);
		//1: if propname is already spelled exactly...
		IField f = getExactField(type, propName);
		if (f!=null) return f;

		//2: most likely enum constant is upper-case form of propname
		String fieldName = StringUtil.hyphensToUpperCase(propName);
		return getExactField(type, fieldName);
	}


	public IMethod getSetter(Type beanType, String propName) {
		try {
			String setterName = "set" + StringUtil.hyphensToCamelCase(propName, true);
			IType type = findType(beanType);
			return type.getMethods().filter(m -> setterName.equals(m.getElementName())).findFirst().orElse(null);
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public IJavaElement getGetter(Type beanType, String propName) {
		String getterName = "get" + StringUtil.hyphensToCamelCase(propName, true);
		IType type = findType(beanType);

		IMethod m = type.getMethod(getterName, Stream.empty());
		if (m != null && m.exists()) {
			return m;
		}

		getterName = "is" + StringUtil.hyphensToCamelCase(propName, true);
		m = type.getMethod(getterName, Stream.empty());
		if (m != null && m.exists()) {
			return m;
		}

		return null;
	}

	public static String deprecatedPropertyMessage(String name, String contextType, String replace, String reason) {
		StringBuilder msg = new StringBuilder("Property '"+name+"'");
		if (StringUtil.hasText(contextType)) {
			msg.append(" of type '"+contextType+"'");
		}
		boolean hasReplace = StringUtil.hasText(replace);
		boolean hasReason = StringUtil.hasText(reason);
		if (!hasReplace && !hasReason) {
			msg.append(" is Deprecated!");
		} else {
			msg.append(" is Deprecated: ");
			if (hasReplace) {
				msg.append("Use '"+ replace +"' instead.");
				if (hasReason) {
					msg.append(" Reason: ");
				}
			}
			if (hasReason) {
				msg.append(reason);
			}
		}
		return msg.toString();
	}

	public Collection<StsValueHint> getHintValues(Type type, String query, EnumCaseMode enumCaseMode) {
		if (type!=null) {
			Collection<StsValueHint> allowed = getAllowedValues(type, enumCaseMode);
			if (allowed!=null) {
				return allowed;
			}
			ValueProviderStrategy valueHinter = VALUE_HINTERS.get(type.getErasure());
			if (valueHinter!=null) {
				return valueHinter.getValuesNow(javaProject, query);
			}
		}
		return null;
	}

	/**
	 * Determine the dimensionality of a collection-like type (i.e. a Map or List). The dimensionality
	 * is essentialy how many succesive 'indexing' operations need to be applied before reasing the actual elements.
	 * <p>
	 * For examle:
	 * List<String> -> 1
	 * List<List<String>> -> 2
	 * List<List<List<String>>> -> 2
	 * Map<*,List<String>> -> 2
	 */
	public int getDimensionality(Type type) {
		int dim = 0;
		while (isSequencable(type) || this.isMap(type)) {
			dim++;
			type = getDomainType(type);
		}
		return dim;
	}

	public static boolean isClass(Type type) {
		if (type!=null) {
			return CLASS_TYPE_NAME.equals(type.getErasure());
		}
		return false;
	}


	public boolean isBracketable(Type type) {
		return isMap(type) || isIndexable(type);
	}


	public Type subsituteKey(Type type, String handleKeyAs) {
		try {
			if (handleKeyAs!=null && isMap(type)) {
				Type valueType = getDomainType(type);
				Type keyType = TypeParser.parse(handleKeyAs);
				return new Type(MAP_TYPE_NAME, new Type[] {keyType, valueType});
			}
		} catch (Exception e) {
			log.error("Invalid type", e);
		}
		return type;
	}


	///////////////////////////////////////////////////////////////////////////////////////////////////////
	// Addapting our interface so it is compatible with YTypeUtil
	//
	// This allows our types to be used by the more generic stuff from the 'editor.support' plugin.
	//
	// Note, it may be possible to avoid having these 'adaptor' methods by making YTypeUtil a paramerized
	// type. I.e something like "interface YTypeUtil<T extends YType>.
	// Paramterizations like that tend to propagate fire and wide in the code and make for complicated
	// signatures. For now using these bredging methods is simpler if perhaps a bit more error prone.

//	@Override
//	public boolean isAtomic(YType type) {
//		return isAtomic((Type)type);
//	}
//
//	@Override
//	public boolean isMap(YType type) {
//		return isMap((Type)type);
//	}
//
//	@Override
//	public boolean isSequencable(YType type) {
//		return isSequencable((Type)type);
//	}
//
//	@Override
//	public YType getDomainType(YType type) {
//		return getDomainType((Type)type);
//	}
//
//	@Override
//	public String[] getHintValues(YType type) {
//		return getAllowedValues((Type) type, EnumCaseMode.ALIASED);
//	}
//
//	@SuppressWarnings("unchecked")
//	@Override
//	public List<YTypedProperty> getProperties(YType type) {
//		//Dirty hack, passing this through a raw type to bypass the java type system
//		//complaining the List<TypedProperty> is not compatible with List<YTypedProperty>
//		//This dirty and 'illegal' conversion is okay because the list is only used for reading.
//		@SuppressWarnings("rawtypes")
//		List props = getProperties((Type)type, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
//		return Collections.unmodifiableList(props);
//	}
//
//	@SuppressWarnings("unchecked")
//	@Override
//	public Map<String, YTypedProperty> getPropertiesMap(YType type) {
//		//Dirty hack, see comment in getProperties(YType)
//		@SuppressWarnings("rawtypes")
//		Map map = getPropertiesMap((Type)type, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
//		return Collections.unmodifiableMap(map);
//	}
//
//	@Override
//	public String niceTypeName(YType type) {
//		return niceTypeName((Type)type);
//	}
//
//	@Override
//	public YType getKeyType(YType type) {
//		return getKeyType((Type)type);
//	}

}