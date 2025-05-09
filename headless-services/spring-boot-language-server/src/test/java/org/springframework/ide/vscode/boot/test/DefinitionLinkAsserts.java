/*******************************************************************************
 * Copyright (c) 2018, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.ide.vscode.boot.java.links.JavaDocumentUriProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.languageserver.testharness.Editor;

import com.google.common.collect.ImmutableList;

public class DefinitionLinkAsserts {

	private JavaDocumentUriProvider javaDocumentUriProvider;
	private CompilationUnitCache cuCache;
	
	public interface JavaLocationProvider {
		Location getLocation(CompilationUnitCache cuCache, JavaDocumentUriProvider javaDocumentUriProvider, IJavaProject project) throws Exception;
	}

	public static JavaMethod method(String fqClassName, String methodName, String... params) {
		return new JavaMethod(fqClassName, methodName, params);
	}

	public static JavaField field(String fqClassName, String name) {
		return new JavaField(fqClassName, name);
	}
	
	public static JavaType type(String fqName) {
		return new JavaType(fqName);
	}

	public static class JavaMethod implements JavaLocationProvider {
		public final String fqName;
		public final String methodName;
		public final String[] params;
		public JavaMethod(String fqClassName, String methodName, String... params) {
			this.fqName = fqClassName;
			this.methodName = methodName;
			this.params = params;
		}
		@Override
		public String toString() {
			return "JavaMethod [fqName=" + fqName + ", methodName=" + methodName + ", params=" + Arrays.toString(params)
					+ "]";
		}
		
		public Location getLocation(CompilationUnitCache cuCache, JavaDocumentUriProvider javaDocumentUriProvider, IJavaProject project) throws Exception {
			Location loc = new Location();
			Optional<URI> sourceUriOpt = SourceLinks.source(project, fqName);
			if (sourceUriOpt.isPresent()) {

				URI docUri = javaDocumentUriProvider.docUri(project, fqName);
				loc.setUri(docUri.toASCIIString());

				URI sourceUri = sourceUriOpt.get();
				Range r = cuCache.withCompilationUnit(project, sourceUri, (cu) -> {
					try {
						AtomicReference<Range> range = new AtomicReference<>(null);
						TextDocument doc = new TextDocument(sourceUri.toASCIIString(), LanguageId.JAVA);
						doc.setText(cuCache.fetchContent(sourceUri));
						String typeName = fqName.substring(fqName.lastIndexOf('.') + 1);
						String[] nameTokens = typeName.split("\\$");

						cu.accept(new ASTVisitor() {

							@Override
							public boolean visit(MethodDeclaration node) {
								SimpleName nameNode = node.getName();
								if (nameNode.getIdentifier().equals(methodName) && isType(node.getParent(), nameTokens, nameTokens.length)) {
									if (node.parameters().size() != params.length) {
										return false;
									}
									int i = 0;
									for (Object _p : node.parameters()) {
										if (_p instanceof SingleVariableDeclaration) {
											SingleVariableDeclaration p = (SingleVariableDeclaration) _p;
											String fqName = p.getType().resolveBinding().getErasure().getQualifiedName();
											if (!fqName.equals(params[i++])) {
												return false;
											}
										} else {
											return false;
										}
									}
									try {
										range.set(doc.toRange(nameNode.getStartPosition(), nameNode.getLength()));
									} catch (BadLocationException e) {
										throw new IllegalStateException(e);
									}
								}
								return false;
							}

						});
						return range.get();
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				});
				if (r == null) {
					throw new IllegalStateException("Couldn't find " + toString());
				}
				loc.setRange(r);
			}
			return loc;
		}

	}

	public static class JavaField implements JavaLocationProvider {
		public final String fqName;
		public final String fieldName;
		public JavaField(String fqName, String fieldName) {
			super();
			this.fqName = fqName;
			this.fieldName = fieldName;
		}
		@Override
		public String toString() {
			return "JavaField [fqName=" + fqName + ", fieldName=" + fieldName + "]";
		}
		
		public Location getLocation(CompilationUnitCache cuCache, JavaDocumentUriProvider javaDocumentUriProvider, IJavaProject project) throws Exception {
			Location loc = new Location();

			Optional<URI> sourceUriOpt = SourceLinks.source(project, fqName);
			if (sourceUriOpt.isPresent()) {

				URI sourceUri = sourceUriOpt.get();

				URI docUri = javaDocumentUriProvider.docUri(project, fqName);
				loc.setUri(docUri.toASCIIString());
				String typeName = fqName.substring(fqName.lastIndexOf('.') + 1);
				String[] nameTokens = typeName.split("\\$");

				Range r = cuCache.withCompilationUnit(project, sourceUri, (cu) -> {
					try {
						AtomicReference<Range> range = new AtomicReference<>(null);
						TextDocument doc = new TextDocument(sourceUri.toASCIIString(), LanguageId.JAVA);
						doc.setText(cuCache.fetchContent(sourceUri));
						cu.accept(new ASTVisitor() {

							@Override
							public boolean visit(EnumConstantDeclaration node) {
								if (isType(node.getParent(), nameTokens, nameTokens.length)) {
									SimpleName nameNode = node.getName();
									if (nameNode.getIdentifier().equals(fieldName)) {
										try {
											range.set(doc.toRange(nameNode.getStartPosition(), nameNode.getLength()));
											return false;
										} catch (BadLocationException e) {
											throw new IllegalStateException(e);
										}
									}
								}
								return true;
							}

							@SuppressWarnings("unchecked")
							@Override
							public boolean visit(FieldDeclaration node) {
								if (isType(node.getParent(), nameTokens, nameTokens.length)) {
									for (VariableDeclarationFragment f : (List<VariableDeclarationFragment>) node.fragments()) {
										SimpleName nameNode = f.getName();
										if (fieldName.equals(nameNode.getIdentifier())) {
											try {
												range.set(doc.toRange(nameNode.getStartPosition(), nameNode.getLength()));
												return false;
											} catch (BadLocationException e) {
												throw new IllegalStateException(e);
											}
										}
 									}
								}
								return super.visit(node);
							}

							@Override
							public boolean visit(RecordDeclaration node) {
								if (isType(node, nameTokens, nameTokens.length)) {
									for (Object o : node.recordComponents()) {
										if (o instanceof SingleVariableDeclaration) {
											SingleVariableDeclaration rc = (SingleVariableDeclaration) o;
											if (fieldName.equals(new String(rc.getName().getIdentifier()))) {
												try {
													range.set(doc.toRange(rc.getName().getStartPosition(), rc.getName().getLength()));
													return false;
												} catch (BadLocationException e) {
													throw new IllegalStateException(e);
												}
											}
										}
									}
								}
								return super.visit(node);
							}
							

						});
						return range.get();
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				});
				if (r == null) {
					throw new IllegalStateException("Couldn't find " + toString());
				}
				loc.setRange(r);
			}
			return loc;
		}

	}
	
	public static class JavaType implements JavaLocationProvider {
		private String fqName;

		public JavaType(String fqName) {
			this.fqName = fqName;
		}
		
		public Location getLocation(CompilationUnitCache cuCache, JavaDocumentUriProvider javaDocumentUriProvider, IJavaProject project) throws Exception {
			Location loc = new Location();
			Optional<URI> sourceUriOpt = SourceLinks.source(project, fqName);
			if (sourceUriOpt.isPresent()) {

				URI docUri = javaDocumentUriProvider.docUri(project, fqName);
				loc.setUri(docUri.toASCIIString());

				String typeName = fqName.substring(fqName.lastIndexOf('.') + 1);
				URI sourceUri = sourceUriOpt.get();
				Range r = cuCache.withCompilationUnit(project, sourceUri, (cu) -> {
					try {
						TextDocument doc = new TextDocument(sourceUri.toASCIIString(), LanguageId.JAVA);
						doc.setText(cuCache.fetchContent(sourceUri));
						AtomicReference<Range> range = new AtomicReference<>(null);
						String[] nameTokens = typeName.split("\\$");
						AtomicInteger index = new AtomicInteger(0);
						cu.accept(new ASTVisitor() {

							private boolean proceessTypeNode(TextDocument doc, String typeName,
									AtomicReference<Range> range, AbstractTypeDeclaration node) {
								SimpleName nameNode = node.getName();
								if (nameNode.getIdentifier().equals(nameTokens[index.get()])) {
									if (index.incrementAndGet() == nameTokens.length) {
										try {
											range.set(doc.toRange(nameNode.getStartPosition(), nameNode.getLength()));
											return false;
										} catch (BadLocationException e) {
											throw new IllegalStateException(e);
										}
									}
									return true;
								}
								return false;
							}

							@Override
							public boolean visit(TypeDeclaration node) {
								return proceessTypeNode(doc, typeName, range, node);
							}

							@Override
							public boolean visit(EnumDeclaration node) {
								return proceessTypeNode(doc, typeName, range, node);
							}

							@Override
							public boolean visit(RecordDeclaration node) {
								return proceessTypeNode(doc, typeName, range, node);
							}
							

						});
						return range.get();
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				});
				if (r == null) {
					throw new IllegalStateException("Couldn't find " + fqName);
				}
				loc.setRange(r);
			}
			return loc;
		}
		
	}

	public DefinitionLinkAsserts(JavaDocumentUriProvider javaDocumentUriProvider, CompilationUnitCache cuCache) {
		this.javaDocumentUriProvider = javaDocumentUriProvider;
		this.cuCache = cuCache;

	}

	public void assertLinkTargets(Editor editor, String hoverOver, IJavaProject project, Range highlightRange, JavaLocationProvider... javaElements) throws Exception {
		List<LocationLink> expectedLocations = new ArrayList<>();
		for (JavaLocationProvider jlp : javaElements) {
			Location l = jlp.getLocation(cuCache, javaDocumentUriProvider, project);
			expectedLocations.add(new LocationLink(l.getUri(), l.getRange(), l.getRange(), highlightRange));
		}

		editor.assertDefinitionLinkTargets(hoverOver, expectedLocations);
	}
	
	public void assertLinkTargets(Editor editor, Position pos, IJavaProject project, Range highlightRange, JavaLocationProvider... javaElements) throws Exception {
		List<LocationLink> expectedLocations = new ArrayList<>();
		for (JavaLocationProvider jlp : javaElements) {
			Location l = jlp.getLocation(cuCache, javaDocumentUriProvider, project);
			expectedLocations.add(new LocationLink(l.getUri(), l.getRange(), l.getRange(), highlightRange));
		}

		editor.assertDefinitionLinkTargets(pos, expectedLocations);
	}


	public void assertLinkTargets(Editor editor, String hoverOver, IJavaProject project, Range highlightRange, String typeFqName) throws Exception {

		Location l = type(typeFqName).getLocation(cuCache, javaDocumentUriProvider, project);
		
		LocationLink link = new LocationLink(l.getUri(), l.getRange(), l.getRange(), highlightRange);

		editor.assertDefinitionLinkTargets(hoverOver, ImmutableList.of(link));
	}
	
	private static boolean isType(ASTNode node, String[] typeTokens, int length) {
		if (length <= 0) {
			return !(node instanceof AbstractTypeDeclaration);
		} else if (node instanceof AbstractTypeDeclaration) {
			AbstractTypeDeclaration typeDeclaration = (AbstractTypeDeclaration) node;
			if (typeDeclaration.getName().getIdentifier().equals(typeTokens[length - 1])) {
				return isType(typeDeclaration.getParent(), typeTokens, length - 1);
			}
		}
		return false;
	}

}
