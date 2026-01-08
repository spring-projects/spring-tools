/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.links;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IField;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IMember;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IType;

public class DefaultJavaElementLocationProvider implements JavaElementLocationProvider {

	private static final Logger log = LoggerFactory.getLogger(DefaultJavaElementLocationProvider.class);

	private CompilationUnitCache cuCache;
	private JavaDocumentUriProvider javaDocUriProvider;

	public DefaultJavaElementLocationProvider(CompilationUnitCache cuCache, JavaDocumentUriProvider javaDocUriProvider) {
		this.cuCache = cuCache;
		this.javaDocUriProvider = javaDocUriProvider;
	}

	@Override
	public Location findLocation(IJavaProject project, IMember member) {
		Location loc = new Location();
		String fqName = member.getDeclaringType().getFullyQualifiedName();
		URI docUri = javaDocUriProvider.docUri(project, fqName);
		if (docUri != null) {
			loc.setUri(docUri.toASCIIString());
			Optional<URI> uriOpt = SourceLinks.source(project, fqName);
			if (uriOpt.isPresent()) {
					String memberBindingKey = member.getBindingKey();

					try {
						URI uri = uriOpt.get();
						Range r = cuCache.withCompilationUnit(project, uri, (cu) -> {
							AtomicReference<Range> range = new AtomicReference<>(null);
							if (cu == null) {
								return new Range(new Position(0, 0), new Position(0, 0));
							}
							cu.accept(new ASTVisitor() {

								private Range nameRange(SimpleName nameNode) {
									int startOffset = nameNode.getStartPosition();
									int endOffset = nameNode.getLength() + startOffset;

									// Line -1 because for CU lines are starting from 1
									return new Range(
											new Position(cu.getLineNumber(startOffset) - 1, cu.getColumnNumber(startOffset)),
											new Position(cu.getLineNumber(endOffset) - 1, cu.getColumnNumber(endOffset)));
								}

								@Override
								public boolean visit(MethodDeclaration node) {
									if (member instanceof IMethod) {
										String bindingKey = node.resolveBinding().getKey();
										if (matchMethodBindingKeys(memberBindingKey, bindingKey)) {
											range.set(nameRange(node.getName()));
											return false;
										}
									}
									return true;
								}

								@Override
								public boolean visit(EnumConstantDeclaration node) {
									if (member instanceof IField) {
										String bindingKey = node.resolveVariable().getKey();
										if (memberBindingKey.equals(bindingKey)) {
											range.set(nameRange(node.getName()));
											return false;
										}
									}
									return true;
								}

								@Override
								public boolean visit(EnumDeclaration node) {
									if (member instanceof IType) {
										String bindingKey = node.resolveBinding().getKey();
										if (memberBindingKey.equals(bindingKey)) {
											range.set(nameRange(node.getName()));
											return false;
										}
									}
									return true;
								}

								@Override
								public boolean visit(TypeDeclaration node) {
									if (member instanceof IType) {
										String bindingKey = node.resolveBinding().getKey();
										if (memberBindingKey.equals(bindingKey)) {
											range.set(nameRange(node.getName()));
											return false;
										}
									}
									return true;
								}

								@Override
								public boolean visit(RecordDeclaration node) {
									for (Object o : node.recordComponents()) {
										if (o instanceof SingleVariableDeclaration) {
											SingleVariableDeclaration rc = (SingleVariableDeclaration) o;
											if (memberBindingKey.equals(rc.resolveBinding().getKey())) {
												range.set(nameRange(rc.getName()));
												return false;
											}
										}
									}
									return true;
								}

								@Override
								public boolean visit(FieldDeclaration node) {
									for (Object o : node.fragments()) {
										if (o instanceof VariableDeclarationFragment f) {
											IVariableBinding binding = f.resolveBinding();
											if (memberBindingKey.equals(binding.getKey())) {
												range.set(nameRange(f.getName()));
												return false;
											}
										}
									}
									return true;
								}

							});
							return range.get();
						});
						if (r == null) {
							throw new IllegalStateException("Couldn't find " + member);
						}
						loc.setRange(r);
					} catch (Throwable t) {
						log.error("", t);
					}

				}
			}
		return loc;
	}

	private static boolean matchMethodBindingKeys(String key1, String key2) {
		return removeReturnTypeFromMethodKeyBinding(key1).equals(removeReturnTypeFromMethodKeyBinding(key2));
	}

	private static String removeReturnTypeFromMethodKeyBinding(String key) {
		int idx = key.lastIndexOf(')');
		return idx >= 0 ? key.substring(0, idx + 1) : key;
	}

}
