/*******************************************************************************
 * Copyright (c) 2018, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.data.providers.DataRepositoryCompletionProvider;
import org.springframework.ide.vscode.boot.java.data.providers.DataRepositoryQueryStartCompletionProvider;
import org.springframework.ide.vscode.boot.java.data.providers.DataRepositoryStandardCompletionProvider;
import org.springframework.ide.vscode.boot.java.data.providers.prefixsensitive.DataRepositoryPrefixSensitiveCompletionProvider;
import org.springframework.ide.vscode.boot.java.handlers.CompletionProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class DataRepositoryCompletionProcessor implements CompletionProvider {

	private List<DataRepositoryCompletionProvider> completionProviders;

	public DataRepositoryCompletionProcessor() {
		this.completionProviders = List.of(
				new DataRepositoryStandardCompletionProvider(),
				new DataRepositoryQueryStartCompletionProvider(),
				new DataRepositoryPrefixSensitiveCompletionProvider()
		);
	}

	@Override
	public void provideCompletions(ASTNode node, int offset, TextDocument doc, Collection<ICompletionProposal> completions) {
		TypeDeclaration type = ASTUtils.findDeclaringType(node);
		DataRepositoryDefinition repo = getDataRepositoryDefinition(type);
		if (repo != null && repo.getDomainType() != null){
			String prefix = "";
			try {
				IRegion line = doc.getLineInformationOfOffset(offset);
				prefix = doc.get(line.getOffset(), offset - line.getOffset()).trim();
			} catch (BadLocationException e) {
				// ignore if there is a problem computing the prefix, continue without prefix
			}
			for(DataRepositoryCompletionProvider provider : completionProviders){
				provider.addProposals(completions, doc, offset, prefix, repo, node);
			}
		}
	}

	private DataRepositoryDefinition getDataRepositoryDefinition(TypeDeclaration type) {
		if (type != null) {
			ITypeBinding resolvedType = type.resolveBinding();
			return getDataRepositoryDefinition(type, resolvedType);
		}

		return null;
	}

	private DataRepositoryDefinition getDataRepositoryDefinition(TypeDeclaration type, ITypeBinding resolvedType) {
		if (resolvedType != null) {

			// interface analysis
			ITypeBinding[] interfaces = resolvedType.getInterfaces();
			for (ITypeBinding resolvedInterface : interfaces) {
				String simplifiedType = null;
				if (resolvedInterface.isParameterizedType()) {
					simplifiedType = resolvedInterface.getBinaryName();
				}
				else {
					simplifiedType = resolvedType.getQualifiedName();
				}

				if (Constants.REPOSITORY_TYPE.equals(simplifiedType)) {
					return createDataRepositoryDefinitionFromType(resolvedInterface);
				}
				else {
					DataRepositoryDefinition repo = getDataRepositoryDefinition(type, resolvedInterface);
					if (repo != null) {
						return repo;
					}
				}
			}

			// super type analysis
			ITypeBinding superclass = resolvedType.getSuperclass();
			if (superclass != null) {
				return getDataRepositoryDefinition(type, superclass);
			}
		}
		return null;
	}

	private DataRepositoryDefinition createDataRepositoryDefinitionFromType(ITypeBinding resolvedInterface) {
		DomainType domainType = null;
		if (resolvedInterface.isParameterizedType()) {
			ITypeBinding[] typeParameters = resolvedInterface.getTypeArguments();
			if (typeParameters != null && typeParameters.length > 0) {
				domainType = new DomainType(typeParameters[0]);
			}
		}
		return new DataRepositoryDefinition(new SimpleType(resolvedInterface),domainType);
	}
}
