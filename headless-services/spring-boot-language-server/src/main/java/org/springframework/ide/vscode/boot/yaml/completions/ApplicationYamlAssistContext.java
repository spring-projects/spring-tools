/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.yaml.completions;

import static org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal.DEEMP_EXISTS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.common.InformationTemplates;
import org.springframework.ide.vscode.boot.common.PropertyCompletionFactory;
import org.springframework.ide.vscode.boot.common.RelaxedNameConfig;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation;
import org.springframework.ide.vscode.boot.java.links.JavaElementLocationProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.IndexNavigator;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndex;
import org.springframework.ide.vscode.boot.metadata.hints.HintProvider;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.boot.metadata.hints.ValueHintHoverInfo;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.BeanPropertyNameMode;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.EnumCaseMode;
import org.springframework.ide.vscode.boot.metadata.types.TypedProperty;
import org.springframework.ide.vscode.boot.metadata.util.PropertyDocUtils;
import org.springframework.ide.vscode.boot.properties.hover.PropertiesDefinitionCalculator;
import org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlASTReconciler;
import org.springframework.ide.vscode.commons.java.IField;
import org.springframework.ide.vscode.commons.java.IJavaElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IMember;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.LazyProposalApplier;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.FuzzyMap.Match;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.yaml.completion.AbstractYamlAssistContext;
import org.springframework.ide.vscode.commons.yaml.completion.TopLevelAssistContext;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContext;
import org.springframework.ide.vscode.commons.yaml.completion.YamlPathEdits;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.vscode.commons.yaml.util.YamlIndentUtil;
import org.springframework.ide.vscode.commons.yaml.util.YamlUtil;

import com.google.common.collect.ImmutableList;

/**
 * Represents a context insied a "application.yml" file relative to which we can provide
 * content assistance.
 */
public abstract class ApplicationYamlAssistContext extends AbstractYamlAssistContext {

	private static Logger log = LoggerFactory.getLogger(ApplicationYamlAssistContext.class);

	protected final RelaxedNameConfig conf;

// This may prove useful later but we don't need it for now
	//	/**
	//	 * AssistContextKind is an classification of the different kinds of
	//	 * syntactic context that CA can be invoked from.
	//	 */
	//	public static enum Kind {
	//		SKEY_KEY, /* CA called from a SKeyNode and node.isInKey(cursor)==true */
	//		SKEY_VALUE, /* CA called from a SKeyNode and node.isInKey(cursor)==false */
	//		SRAW /* CA called from a SRawNode */
	//	}
	//	protected final Kind contextKind;

	public final TypeUtil typeUtil;

	public final JavaElementLocationProvider javaElementLocationProvider;

	public ApplicationYamlAssistContext(YamlDocument doc, int documentSelector, YamlPath contextPath, TypeUtil typeUtil, RelaxedNameConfig conf, JavaElementLocationProvider javaElementLocationProvider) {
		super(doc, documentSelector, contextPath);
		this.typeUtil = typeUtil;
		this.conf = conf;
		this.javaElementLocationProvider = javaElementLocationProvider;
	}

	/**
	 * Computes the text that should be appended at the end of a completion
	 * proposal depending on what type of value is expected.
	 */
	protected String appendTextFor(Type type) {
		//Note that proper indentation after each \n" is added automatically
		//so the strings created here do not need to contain indentation spaces
		if (typeUtil.isMap(type)) {
			//ready to enter nested map key on next line
			return "\n"+YamlIndentUtil.INDENT_STR;
		} if (typeUtil.isSequencable(type)) {
			//ready to enter sequence element on next line
			return "\n- ";
		} else if (typeUtil.isAtomic(type)) {
			//ready to enter whatever on the same line
			return " ";
		} else {
			//Assume its some kind of pojo bean
			return "\n"+YamlIndentUtil.INDENT_STR;
		}
	}

	/**
	 * @return the type expected at this context, may return null if unknown.
	 */
	protected abstract Type getType();

	public static ApplicationYamlAssistContext subdocument(YamlDocument doc, int documentSelector, SpringPropertyIndex index, PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf, JavaElementLocationProvider javaElementLocationProvider) {
		return new IndexContext(doc, documentSelector, YamlPath.EMPTY, index, IndexNavigator.with(index.getProperties()), completionFactory, typeUtil, conf, javaElementLocationProvider);
	}

	private static class TypeContext extends ApplicationYamlAssistContext {

		private PropertyCompletionFactory completionFactory;
		private Type type;
		private ApplicationYamlAssistContext parent;
		private HintProvider hints;

		public TypeContext(ApplicationYamlAssistContext parent, YamlPath contextPath, Type type,
				PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf, 
				HintProvider hints, String handleKeyAs,
				JavaElementLocationProvider javaElementLocationProvider
		) {
			super(parent.getDocument(), parent.documentSelector, contextPath, typeUtil, conf, javaElementLocationProvider);
			this.parent = parent;
			this.completionFactory = completionFactory;
			this.type = typeUtil.subsituteKey(type, handleKeyAs);
			this.hints = hints;
		}

		private HintProvider getHintProvider() {
			return hints;
		}

		@Override
		public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
			String query = getPrefix(doc, node, offset);
			EnumCaseMode enumCaseMode = enumCaseMode(query);
			BeanPropertyNameMode beanMode = conf.getBeanMode();
			List<ICompletionProposal> valueCompletions = getValueCompletions(doc, offset, query, enumCaseMode);
			if (!valueCompletions.isEmpty()) {
				return valueCompletions;
			}
			return getKeyCompletions(doc, offset, query, enumCaseMode, beanMode);
		}

		private EnumCaseMode enumCaseMode(String query) {
			if (query.isEmpty()) {
				return conf.getEnumMode();
			} else {
				return EnumCaseMode.ALIASED; // will match candidates from both lower and original based on what user typed
			}
		}

		public List<ICompletionProposal> getKeyCompletions(YamlDocument doc, int offset, String query,
				EnumCaseMode enumCaseMode, BeanPropertyNameMode beanMode) throws Exception {
			int queryOffset = offset - query.length();
			List<TypedProperty> properties = getProperties(query, enumCaseMode, beanMode);
			if (CollectionUtil.hasElements(properties)) {
				ArrayList<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>(properties.size());
				SNode contextNode = getContextNode();
				Set<String> definedProps = getDefinedProperties(contextNode);
				for (TypedProperty p : properties) {
					String name = p.getName();
					if (ApplicationYamlASTReconciler.needsEscaping(name)) {
						name = "["+name+"]";
					}
					double score = FuzzyMatcher.matchScore(query, name);
					if (score!=0) {
						YamlPath relativePath = YamlPath.fromSimpleProperty(name);
						YamlPathEdits edits = new YamlPathEdits(doc);
						if (!definedProps.contains(name)) {
							//property not yet defined
							Type type = p.getType();
							edits.delete(queryOffset, query);
							edits.createPathInPlace(contextNode, relativePath, queryOffset, () -> appendTextFor(type));
							proposals.add(completionFactory.beanProperty(doc.getDocument(),
									contextPath.toPropString(), getType(),
									query, p, score, edits, typeUtil)
							);
						} else {
							//property already defined
							// instead of filtering, navigate to the place where its defined.
							deleteQueryAndLine(doc, query, queryOffset, edits);
							//Cast to SChildBearingNode cannot fail because otherwise definedProps would be the empty set.
							edits.createPath((SChildBearingNode) contextNode, relativePath, "");
							proposals.add(
								completionFactory.beanProperty(doc.getDocument(),
									contextPath.toPropString(), getType(),
									query, p, score, edits, typeUtil)
								.deemphasize(DEEMP_EXISTS) //deemphasize because it already exists
							);
						}
					}
				}
				return proposals;
			}
			return Collections.emptyList();
		}

		protected List<TypedProperty> getProperties(String query, EnumCaseMode enumCaseMode, BeanPropertyNameMode beanMode) {
			ArrayList<TypedProperty> props = new ArrayList<>();
			List<TypedProperty> fromType = typeUtil.getProperties(type, enumCaseMode, beanMode);
			if (CollectionUtil.hasElements(fromType)) {
				props.addAll(fromType);
			}
			HintProvider hints = getHintProvider();
			if (hints!=null) {
				List<TypedProperty> fromHints = hints.getPropertyHints(query);
				if (CollectionUtil.hasElements(fromHints)) {
					props.addAll(fromHints);
				}
			}
			return props;
		}

		private Set<String> getDefinedProperties(SNode contextNode) {
			try {
				if (contextNode instanceof SChildBearingNode) {
					List<SNode> children = ((SChildBearingNode)contextNode).getChildren();
					if (CollectionUtil.hasElements(children)) {
						Set<String> keys = new HashSet<String>(children.size());
						for (SNode c : children) {
							if (c instanceof SKeyNode) {
								keys.add(((SKeyNode) c).getKey());
							}
						}
						return keys;
					}
				}
			} catch (Exception e) {
				log.error("", e);
			}
			return Collections.emptySet();
		}

		private List<ICompletionProposal> getValueCompletions(YamlDocument doc, int offset, String query, EnumCaseMode enumCaseMode) {
			Collection<StsValueHint> hints = getHintValues(query, doc, offset, enumCaseMode);
			if (hints!=null) {
				ArrayList<ICompletionProposal> completions = new ArrayList<ICompletionProposal>();
				for (StsValueHint hint : hints) {
					String value = hint.getValue();
					double score = FuzzyMatcher.matchScore(query, value);
					if (score!=0 && !value.equals(query)) {
						DocumentEdits edits = new DocumentEdits(doc.getDocument(), false);
						int valueStart = offset-query.length();
						edits.delete(valueStart, offset);
						if (doc.getChar(valueStart-1)==':') {
							edits.insert(offset, " ");
						}
						edits.insert(offset, YamlUtil.stringEscape(value));
						completions.add(completionFactory.valueProposal(value, query, typeUtil.niceTypeName(type), score, edits, ValueHintHoverInfo.create(hint)));
					}
				}
				return completions;
			}
			return Collections.emptyList();
		}

		@Override
		public Renderable getValueHoverInfo(YamlDocument doc, DocumentRegion valueRegion) {
			String value = valueRegion.toString();

			if (TypeUtil.isClass(type)) {
				//Special case. We want hovers/hyperlinks even if the class is not a valid hint (as long as it is a class)
				StsValueHint hint = StsValueHint.className(value.toString(), typeUtil);
				if (hint!=null) {
					return hint.getDescription();
				}
			}

			Collection<StsValueHint> hints = getHintValues(value, doc, valueRegion.getEnd(), EnumCaseMode.ALIASED);
			//The hints where found by fuzzy match so they may not actually match exactly!
			for (StsValueHint h : hints) {
				if (value.equals(h.getValue())) {
					return h.getDescription();
				}
			}
			return getHoverInfo();
		}

		protected Collection<StsValueHint> getHintValues(
				String query,
				YamlDocument doc, int offset,
				EnumCaseMode enumCaseMode
		) {
			Collection<StsValueHint> allHints = new ArrayList<>();
			{
				Collection<StsValueHint> hints = typeUtil.getHintValues(type, query, enumCaseMode);
				if (CollectionUtil.hasElements(hints)) {
					allHints.addAll(hints);
				}
			}
			{
				HintProvider hintProvider = getHintProvider();
				if (hintProvider!=null) {
					allHints.addAll(hintProvider.getValueHints(query));
				}
			}
			return allHints;
		}

		@Override
		public YamlAssistContext traverse(YamlPathSegment s) {
			if (s.getType()==YamlPathSegmentType.VAL_AT_KEY) {
				if (typeUtil.isSequencable(type) || typeUtil.isMap(type)) {
					return contextWith(s, TypeUtil.getDomainType(type));
				}
				String key = s.toPropString();
				Map<String, TypedProperty> subproperties = typeUtil.getPropertiesMap(type, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
				if (subproperties!=null) {
					return contextWith(s, TypedProperty.typeOf(subproperties.get(key)));
				}
			} else if (s.getType()==YamlPathSegmentType.VAL_AT_INDEX) {
				if (typeUtil.isSequencable(type)) {
					return contextWith(s, TypeUtil.getDomainType(type));
				}
			}
			return null;
		}

		private AbstractYamlAssistContext contextWith(YamlPathSegment s, Type nextType) {
			if (nextType!=null) {
				return new TypeContext(this, contextPath.append(s), nextType, completionFactory, typeUtil, conf,
						new YamlPath(s).traverse(hints), null, javaElementLocationProvider);
			}
			return null;
		}


		@Override
		public String toString() {
			return "TypeContext("+contextPath.toPropString()+"::"+type+")";
		}


		@Override
		public Renderable getHoverInfo() {
			if (parent instanceof IndexContext) {
				//this context is in fact an 'alias' of its parent, representing the
				// point in the context hierarchy where a we transition from navigating
				// the index to navigating type/bean properties
				return parent.getHoverInfo();
			} else {
				String id = contextPath.toPropString();
				String propName = contextPath.getBeanPropertyName();
				String typeString = typeUtil.niceTypeName(getType());
				Type parentType = parent.getType();
				return InformationTemplates.createHover(id, typeString, null,
						getDescription(typeUtil, parentType, propName), getDeprecation(typeUtil, parentType, propName));
			}
		}

		@Override
		protected Type getType() {
			return type;
		}


		@Override
		public Renderable getHoverInfo(YamlPathSegment lastSegment) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public List<Location> getDefinitionsForPropertyKey() {
			if (parent instanceof IndexContext) {
				//this context is in fact an 'alias' of its parent, representing the
				// point in the context hierarchy where a we transition from navigating
				// the index to navigating type/bean properties
				return parent.getDefinitionsForPropertyKey();
			} else {
				String propName = contextPath.getBeanPropertyName();
				Type parentType = parent.getType();
				IJavaProject javaProject = typeUtil.getJavaProject();
				Type keyType = typeUtil.getMapKeyType(parentType);
				if (keyType != null) {
					String keyValue = contextPath.getLastSegment().toPropString();
					return PropertiesDefinitionCalculator.getValueDefinitionLocations(javaElementLocationProvider, typeUtil, keyType, keyValue);
				} else {
					final IType javaType = javaProject.getIndex().findType(parentType.getErasure());
					if (javaType != null) {
						if (javaType.isRecord()) {
							IField field = PropertiesDefinitionCalculator.getPropertyField(javaType, propName);
							if (field != null) {
								Location location = javaElementLocationProvider.findLocation(javaProject, field);
								if (location != null) {
									return ImmutableList.of(location);
								}
							}
						} else {
							IMember m = typeUtil.getNameAnnotatedField(javaType, propName)
									.map(IMember.class::cast)
									.orElseGet(() -> (IMember) PropertiesDefinitionCalculator.getPropertyMethod(typeUtil, javaType, propName));
							if (m != null) {
								Location location = javaElementLocationProvider.findLocation(javaProject, m);
								if (location != null) {
									return ImmutableList.of(location);
								}
							}
						}
					}
				}
				return ImmutableList.of();
			}
		}

		@Override
		public List<Location> getDefinitionsForPropertyValue(DocumentRegion valueRegion) {
			return PropertiesDefinitionCalculator.getValueDefinitionLocations(javaElementLocationProvider, typeUtil, type, valueRegion.toString().trim());
		}

	}

	private static class IndexContext extends ApplicationYamlAssistContext {

		final private SpringPropertyIndex index;
		final private IndexNavigator indexNav;
		final PropertyCompletionFactory completionFactory;

		public IndexContext(YamlDocument doc, int documentSelector, YamlPath contextPath, SpringPropertyIndex index, IndexNavigator indexNav,
				PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf, JavaElementLocationProvider javaElementLocationProvider) {
			super(doc, documentSelector, contextPath, typeUtil, conf, javaElementLocationProvider);
			this.index = index;
			this.indexNav = indexNav;
			this.completionFactory = completionFactory;
		}

		@Override
		public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
			String query = getPrefix(doc, node, offset);
			Collection<Match<PropertyInfo>> matchingProps = indexNav.findMatching(query);
			if (!matchingProps.isEmpty()) {
				ArrayList<ICompletionProposal> completions = new ArrayList<ICompletionProposal>();
				matchingProps.stream().forEach(match -> {
					try {
						DocumentEdits edits = createEdits(doc, node, offset, query, match);
						AbstractScoreableProposal completion = completionFactory.property(
								doc.getDocument(), edits, match, typeUtil
						);
						String prefix = indexNav.getPrefix();
						if (StringUtil.hasText(prefix)) {
							completion = completion.dropLabelPrefix(prefix.length()+1);
						}
						if (getContextRoot(doc).exists(YamlPath.fromProperty(match.data.getId()))) {
							completion.deemphasize(DEEMP_EXISTS);
						}
						synchronized (completions) {
							completions.add(completion);
						}
					} catch (Exception e) {
						log.error("{}", e);
					}
				});
				return completions;
			}
			return Collections.emptyList();
		}

		protected DocumentEdits createEdits(final YamlDocument file,
				SNode node, final int offset, final String query, final Match<PropertyInfo> match)
				throws Exception {
			//Edits created lazyly as they are somwehat expensive to compute and mostly
			// we need only the edits for the one proposal that user picks.
			return LazyProposalApplier.from(() -> {
				YamlPathEdits edits = new YamlPathEdits(file);

				int queryOffset = offset-query.length();
				edits.delete(queryOffset, query);

				YamlPath propertyPath = YamlPath.fromProperty(match.data.getId());
				YamlPath relativePath = propertyPath.dropFirst(contextPath.size());
				YamlPathSegment nextSegment = relativePath.getSegment(0);
				SNode contextNode = getContextNode();
				//To determine if this completion is 'in place' or needs to be inserted
				// elsewhere in the tree, we check whether a node already exists in our
				// context. If it doesn't we can create it as any child of the context
				// so that includes, right at place the user is typing now.
				SNode existingNode = contextNode.traverse(nextSegment);
				if (existingNode==null) {
					edits.createPathInPlace(contextNode, relativePath, queryOffset, () -> appendTextFor(TypeParser.parse(match.data.getType())));
				} else {
					String wholeLine = file.getLineTextAtOffset(queryOffset);
					if (wholeLine.trim().equals(query.trim())) {
						edits.deleteLineBackwardAtOffset(queryOffset);
					}
					edits.createPath(getContextRoot(file), YamlPath.fromProperty(match.data.getId()), () -> appendTextFor(TypeParser.parse(match.data.getType())));
				}
				return edits;
			});
		}

		@Override
		public AbstractYamlAssistContext traverse(YamlPathSegment s) {
			if (s.getType()==YamlPathSegmentType.VAL_AT_KEY) {
				String key = s.toPropString();
				IndexNavigator subIndex = indexNav.selectSubProperty(key);
				if (subIndex.isEmpty()) {
					//Nothing found for actual key... maybe its a 'camelCased' alias of real key?
					String keyAlias = StringUtil.camelCaseToHyphens(key);
					if (!keyAlias.equals(key)) { // no point checking alias is the same (likely key was not camelCased)
						IndexNavigator aliasedSubIndex = indexNav.selectSubProperty(keyAlias);
						if (!aliasedSubIndex.isEmpty()) {
							subIndex = aliasedSubIndex;
						}
					}
				}
				if (subIndex.getExtensionCandidate()!=null) {
					return new IndexContext(getDocument(), documentSelector, contextPath.append(s), index, subIndex, completionFactory, typeUtil, conf, javaElementLocationProvider);
				} else if (subIndex.getExactMatch()!=null) {
					IndexContext asIndexContext = new IndexContext(getDocument(), documentSelector, contextPath.append(s), index, subIndex, completionFactory, typeUtil, conf, javaElementLocationProvider);
					PropertyInfo prop = subIndex.getExactMatch();
					return new TypeContext(asIndexContext, contextPath.append(s), TypeParser.parse(prop.getType()), completionFactory, typeUtil, conf, prop.getHints(typeUtil), prop.getHandleKeyAs(), javaElementLocationProvider);
				}
			}
			//Unsuported navigation => no context for assist
			return null;
		}

		@Override
		public String toString() {
			return "YamlAssistIndexContext("+indexNav+")";
		}

		@Override
		protected Type getType() {
			PropertyInfo match = indexNav.getExactMatch();
			if (match!=null) {
				return TypeParser.parse(match.getType());
			}
			return null;
		}

		@Override
		public Renderable getHoverInfo() {
			PropertyInfo prop = indexNav.getExactMatch();
			if (prop!=null) {
				return  InformationTemplates.createHover(prop);
//				return new PropertyRenderableProvider(typeUtil.getJavaProject(), prop).getRenderable();
			}
			return null;
		}

		@Override
		public List<Location> getDefinitionsForPropertyKey() {
			PropertyInfo prop = indexNav.getExactMatch();
			if (prop != null) {
				IJavaProject project = typeUtil.getJavaProject();
				Collection<IMember> elements = PropertiesDefinitionCalculator.getPropertyJavaElements(typeUtil, project, prop);
				return PropertiesDefinitionCalculator.getLocations(javaElementLocationProvider, project, elements);
			} else {
				//handle finding property source directly by property key
				Collection<PropertyInfo.PropertySource> sources = index.getGroupSources(indexNav.getPrefix());
				if (sources!=null && !sources.isEmpty()) {
					IJavaProject project = typeUtil.getJavaProject();
					Collection<IMember> elements = PropertiesDefinitionCalculator.getPropertySourceJavaElements(typeUtil, project, sources);
					return PropertiesDefinitionCalculator.getLocations(javaElementLocationProvider, project, elements);
				}
			}
			return ImmutableList.of();
		}

		@Override
		public List<Location> getDefinitionsForPropertyValue(DocumentRegion valueRegion) {
			// Shouldn't be reaching this point. TypeContext should be supplying the value definition
			return super.getDefinitionsForPropertyValue(valueRegion);
		}

		@Override
		public Renderable getHoverInfo(YamlPathSegment lastSegment) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Renderable getValueHoverInfo(YamlDocument doc, DocumentRegion documentRegion) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	@Override
	public Renderable getHoverInfo(YamlPathSegment s) {
		//ApplicationYamlAssistContext implements getHoverInfo directly. so this is not needed.
		return null;
	}

	public static YamlAssistContext global(YamlDocument doc, final SpringPropertyIndex index, final PropertyCompletionFactory completionFactory, final TypeUtil typeUtil, final RelaxedNameConfig conf, JavaElementLocationProvider javaElementLocationProvider) {
		return new TopLevelAssistContext() {
			@Override
			protected YamlAssistContext getDocumentContext(int documentSelector) {
				return subdocument(doc, documentSelector, index, completionFactory, typeUtil, conf, javaElementLocationProvider);
			}

			@Override
			public YamlDocument getDocument() {
				return doc;
			}
		};
	}

	private static Deprecation getDeprecation(TypeUtil typeUtil, Type parentType, String propName) {
		Map<String, TypedProperty> props = typeUtil.getPropertiesMap(parentType, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
		if (props!=null) {
			TypedProperty prop = props.get(propName);
			if (prop!=null) {
				return prop.getDeprecation();
			}
		}
		return null;
	}

	private static Renderable getDescription(TypeUtil typeUtil, Type parentType, String propName) {
		try {
			List<IJavaElement> jes = getAllJavaElements(typeUtil, parentType, propName);
			if (jes != null) {
				for (IJavaElement je : jes) {
					if (je instanceof IMember) {
						SourceLinks sourceLinks = typeUtil.getSourceLinks();
						IJavaProject project = typeUtil.getJavaProject();
						return PropertyDocUtils.documentJavaElement(sourceLinks, project, je);
					}
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Renderables.NO_DESCRIPTION;
	}

	private static List<IJavaElement> getAllJavaElements(TypeUtil typeUtil, Type parentType, String propName) {
		if (propName!=null) {
			Type beanType = parentType;
			if (typeUtil.isMap(beanType)) {
				Type keyType = typeUtil.getKeyType(beanType);
				if (keyType!=null && typeUtil.isEnum(keyType)) {
					IField field = typeUtil.getEnumConstant(keyType, propName);
					if (field!=null) {
						return ImmutableList.of(field);
					}
				}
			} else {
				ArrayList<IJavaElement> elements = new ArrayList<IJavaElement>(3);
				maybeAdd(elements, typeUtil.getField(beanType, propName));
				maybeAdd(elements, typeUtil.getSetter(beanType, propName));
				maybeAdd(elements, typeUtil.getGetter(beanType, propName));
				if (!elements.isEmpty()) {
					return elements;
				}
			}
		}
		return ImmutableList.of();
	}

	private static void maybeAdd(ArrayList<IJavaElement> elements, IJavaElement e) {
		if (e!=null) {
			elements.add(e);
		}
	}


}
