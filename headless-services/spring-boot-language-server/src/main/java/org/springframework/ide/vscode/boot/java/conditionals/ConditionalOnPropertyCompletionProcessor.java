/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeCompletionProvider;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeProposal;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.FuzzyMap;

/**
 * @author Martin Lippert
 */
public class ConditionalOnPropertyCompletionProcessor implements AnnotationAttributeCompletionProvider {
	
	public enum Mode {
		PREFIX, PROPERTY
	}

	private final SpringPropertyIndexProvider indexProvider;
	private final ProjectBasedPropertyIndexProvider adHocIndexProvider;
	private final Mode mode;
	
	public ConditionalOnPropertyCompletionProcessor(SpringPropertyIndexProvider indexProvider,
			ProjectBasedPropertyIndexProvider adHocIndexProvider,
			Mode mode) {
		this.indexProvider = indexProvider;
		this.adHocIndexProvider = adHocIndexProvider;
		this.mode = mode;
	}

	@Override
	public List<AnnotationAttributeProposal> getCompletionCandidates(IJavaProject project, ASTNode node) {
		if (Mode.PROPERTY == this.mode) {
			String prefix = getPrefixAttributeValue(node);
			return findProperties(project, prefix);
		}
		else if (Mode.PREFIX == this.mode) {
			return findPrefixes(project);
		}
		else {
			return Collections.emptyList();
		}
	}

	private List<AnnotationAttributeProposal> findProperties(IJavaProject project, String prefix) {
		List<AnnotationAttributeProposal> result = new ArrayList<>();
		Set<String> propertyKeys = new HashSet<>();
		
		// First the 'real' properties, Then also add 'ad-hoc' properties
		addPropertyProposals(indexProvider.getIndex(project).getProperties(), prefix, result, propertyKeys);
		addPropertyProposals(adHocIndexProvider.getIndex(project), prefix, result, propertyKeys);

		result.sort((p1, p2) -> p1.getLabel().compareTo(p2.getLabel()));

		return result;
	}
	
	private List<AnnotationAttributeProposal> findPrefixes(IJavaProject project) {
		Set<AnnotationAttributeProposal> prefixes = new TreeSet<>((p1, p2) -> p1.getLabel().compareTo(p2.getLabel()));

		// First the 'real' properties, then also add 'ad-hoc' properties
		addPrefixProposals(indexProvider.getIndex(project).getProperties(), prefixes);
		addPrefixProposals(adHocIndexProvider.getIndex(project), prefixes);

		return new ArrayList<>(prefixes);
	}
	
	private void addPropertyProposals(FuzzyMap<PropertyInfo> properties, String prefix, List<AnnotationAttributeProposal> result, Set<String> propertyKeys) {
		properties.forEach(propertyInfo -> {
			String propID = propertyInfo.getId();

			// avoid duplicate property keys
			if (!propertyKeys.add(propID)) {
				return;
			}
			
			if (prefix != null) {				
				if (prefix.length() > 0
					&& prefix.length() < propID.length()
					&& propID.startsWith(prefix)) {
				
					String remainingValue = propID.substring(prefix.length() + 1);
					result.add(new AnnotationAttributeProposal(propID, propID, remainingValue));
				}
			}
			else {
				result.add(new AnnotationAttributeProposal(propID));
			}
		});
	}
	
	private void addPrefixProposals(FuzzyMap<PropertyInfo> properties, Set<AnnotationAttributeProposal> prefixes) {
		properties.forEach(propertyInfo -> {
			String prefix = getPrefix(propertyInfo.getId());
			while (prefix != null) {
				prefixes.add(new AnnotationAttributeProposal(prefix));
				prefix = getPrefix(prefix);
			}
		});
	}
	
	private String getPrefix(String key) {
		int index = key.lastIndexOf('.');
		if (index >= 0) {
			return key.substring(0, index);
		}
		else {
			return null;
		}
	}
	
	private String getPrefixAttributeValue(ASTNode node) {
		ASTNode annotationNode = ASTUtils.getNearestAnnotationParent(node);
		if (annotationNode != null && annotationNode instanceof NormalAnnotation) {
			NormalAnnotation annotation = (NormalAnnotation) annotationNode;
			
			List<?> values = annotation.values();
			for (Object value : values) {
				if (value instanceof MemberValuePair) {
					MemberValuePair valuePair = (MemberValuePair) value;
					String valuePairName = valuePair.getName() != null ? valuePair.getName().toString() : null;
					
					if (valuePairName != null && "prefix".equals(valuePairName)
							&& valuePair.getValue() != null && valuePair.getValue() instanceof StringLiteral) {
						StringLiteral prefixLiteral = (StringLiteral) valuePair.getValue();
						String valuePairValue = ASTUtils.getLiteralValue(prefixLiteral);
						return valuePairValue;
					}
				}
				
			}
		}
		
		return null;
	}

}