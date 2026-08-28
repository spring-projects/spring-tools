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
package org.springframework.ide.vscode.commons.yaml.schema;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.ide.vscode.commons.util.PartialCollection;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.yaml.schema.constraints.Constraint;
import org.springframework.ide.vscode.commons.yaml.snippet.Snippet;
import org.springframework.ide.vscode.commons.yaml.snippet.TypeBasedSnippetProvider;

/**
 * An implementation of YTypeUtil provides implementations of various
 * methods operating on YTypes and interpreting them in some context
 * (e.g. the meaning of YType objects may depend on types resolved
 * from the current project's classpath).
 *
 * @author Kris De Volder
 */
public interface YTypeUtil {
	boolean isAtomic(YType type);
	boolean isMap(YType type);
	boolean isSequencable(YType type);
	boolean isBean(YType type);
	YType getDomainType(YType type);
	PartialCollection<YValueHint> getHintValues(YType yType, DynamicSchemaContext dc);
	String niceTypeName(YType type);
	YType getKeyType(YType type);
	SchemaContextAware<ValueParser> getValueParser(YType type);

	//TODO: only one of these two should be enough?
	List<YTypedProperty> getProperties(YType type);
	Map<String, YTypedProperty> getPropertiesMap(YType yType);

	/**
	 * Given a {@link DynamicSchemaContext} attempt to get a more specific type, as
	 * may be inferred by stuff present in the context. If not enough information is
	 * present in the context to narrow the type, then the type itself
	 * should be returned.
	 */
	YType inferMoreSpecificType(YType type, DynamicSchemaContext dc);
	List<Constraint> getConstraints(YType type);

	ISubCompletionEngine getCustomContentAssistant(YType type);

	/**
	 * Config option for type-bases complection enging. Snippets can be
	 * associated with schema types. These snippets will be suggested as
	 * additional completions based on the type of value expected in
	 * a context.
	 */
	TypeBasedSnippetProvider getSnippetProvider();

	/**
	 * Config option for type-based completion engine. This enables the
	 * 'tiered' proposals feature (so that optional properties are not
	 * suggested until required ones are all defined)
	 */
	boolean tieredOptionalPropertyProposals();
	/**
	 * Config option for type-based completion engine. This enables/disables
	 * whether engine should generate proposals for deprecated properties (true),
	 * or suppress them (false).
	 */
	boolean suggestDeprecatedProperties();
	
	/**
	 * If a type can be considered to be the union of several other types, then 
	 * this method optionally can be implemented to return a collection of these
	 * types. 
	 * <p>
	 * The default implementation returns a singleton colllection containing the type 
	 * itself because every type can at least be considered a union of itself with nothing else.
	 */
	Collection<YType> getUnionSubTypes(YType type);
	
	/**
	 * Determines whether this type is a 'true' union type. This means that 'getUnionSubTypes'
	 * does not simply return a collection of the type itself.
	 */
	default boolean isTrueUnion(YType type) {
		Collection<YType> subtypes = getUnionSubTypes(type);
		if (subtypes != null) {
			for (YType subType : subtypes) {
				if (subType.equals(type)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
}
