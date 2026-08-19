/*******************************************************************************
 * Copyright (c) 2017, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.spel;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.TypeReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.parser.spel.SpelLexer;
import org.springframework.ide.vscode.parser.spel.SpelParser;
import org.springframework.ide.vscode.parser.spel.SpelParser.BeanReferenceContext;
import org.springframework.ide.vscode.parser.spel.SpelParserBaseListener;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author Udayani V
 */
public class SpelDefinitionProvider implements IJavaLocationLinksProvider {

	protected static Logger logger = LoggerFactory.getLogger(SpelDefinitionProvider.class);

	private final SpringMetamodelIndex springIndex;

	private final CompilationUnitCache cuCache;

	private final AnnotationParamSpelExtractor[] spelExtractors = AnnotationParamSpelExtractor.SPEL_EXTRACTORS;

	public record TokenData(String text, int start, int end) {};

	public SpelDefinitionProvider(SpringMetamodelIndex springIndex, CompilationUnitCache cuCache) {
		this.springIndex = springIndex;
		this.cuCache = cuCache;
	}

	@Override
	public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project,
			TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		if (n instanceof StringLiteral || n instanceof TextBlock) {
			ASTNode parent = ASTUtils.getNearestAnnotationParent(n);
			if (parent instanceof Annotation a) {
				// the extractors know which annotations and which of their attributes contain
				// SpEL expressions, so no additional filtering on the annotation type here
				return getLocationLinks(project, offset, a);
			}
		}
		return Collections.emptyList();
	}

	private List<LocationLink> getLocationLinks(IJavaProject project, int offset, Annotation a) {
		List<LocationLink> locationLink = new ArrayList<>();
		Arrays.stream(spelExtractors).flatMap(e -> {
			if (a instanceof SingleMemberAnnotation singleMember)
				return e.getSpelRegions(singleMember).stream();
			else if (a instanceof NormalAnnotation normal)
				return e.getSpelRegions(normal).stream();
			return Stream.<EmbeddedLanguageSnippet>empty();
		})
		.filter(snippet -> {
			IRegion snippetRegion = snippet.getTotalRange();
			return snippetRegion.getStart() <= (offset) && (offset) <= snippetRegion.getEnd();
		}).forEach(snippet -> {
			List<TokenData> beanReferenceTokens = computeTokens(snippet, offset);
			if (beanReferenceTokens != null && beanReferenceTokens.size() > 0) {
				locationLink.addAll(findLocationLinksForBeanRef(project, offset, beanReferenceTokens));
			}

			Optional<Tuple2<String, String>> result = parseAndExtractMethodClassPairFromSpel(snippet, offset);
			result.ifPresent(tuple -> {
				locationLink.addAll(findLocationLinksForMethodRef(tuple.getT1(), tuple.getT2(), project));
			});
		});
		return locationLink;
	}

	private List<LocationLink> findLocationLinksForBeanRef(IJavaProject project, int offset,
			List<TokenData> beanReferenceTokens) {
		return beanReferenceTokens.stream().flatMap(t -> findBeansWithName(project, t.text()).stream())
				.collect(Collectors.toList());
	}

	private List<LocationLink> findLocationLinksForMethodRef(String methodName, String className,
			IJavaProject project) {
		try {
			if (className.startsWith("T(") && className.endsWith(")")) {
				String classFqName = className.substring(2, className.length() - 1);
				return findMethodInTypeHierarchy(project, classFqName, methodName);
			} else if (className.startsWith("@")) {
				String beanName = className.substring(1);
				for (Bean bean : this.springIndex.getBeansWithName(project.getElementName(), beanName)) {
					List<LocationLink> links = findMethodInTypeHierarchy(project, bean.getType(), methodName);
					if (links.isEmpty() && bean.getLocation() != null) {
						// the type of the bean might not be resolvable, in that case look at
						// the document that defines the bean at least
						links = findMethodPositionInDoc(URI.create(bean.getLocation().getUri()), null, methodName,
								project);
					}
					if (!links.isEmpty()) {
						return links;
					}
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return Collections.emptyList();
	}

	/**
	 * Looks for the method in the given type and, if the method isn't declared there, in
	 * the super types of that type. The declaration closest to the given type wins, so
	 * that an overridden method leads to the override and not to the super declaration.
	 */
	private List<LocationLink> findMethodInTypeHierarchy(IJavaProject project, String typeFqName, String methodName) {
		String fqName = withoutTypeParameters(typeFqName);
		if (fqName == null || fqName.isBlank()) {
			return Collections.emptyList();
		}

		Optional<URI> source = SourceLinks.source(project, fqName);
		if (source.isEmpty()) {
			return Collections.emptyList();
		}

		List<LocationLink> links = findMethodPositionInDoc(source.get(), fqName, methodName, project);
		if (!links.isEmpty()) {
			return links;
		}

		// only when the method isn't declared in the type itself, walk up to its super
		// types. The hierarchy comes from the type bindings of the compilation unit that
		// was looked at already, no need to query the classpath index for it.
		for (String superType : findSuperTypeFqNames(source.get(), fqName, project)) {
			Optional<URI> superTypeSource = SourceLinks.source(project, superType);
			if (superTypeSource.isPresent()) {
				links = findMethodPositionInDoc(superTypeSource.get(), superType, methodName, project);
				if (!links.isEmpty()) {
					return links;
				}
			}
		}
		return Collections.emptyList();
	}

	/**
	 * The super types of the given type, the closest ones first.
	 */
	private List<String> findSuperTypeFqNames(URI docUri, String typeFqName, IJavaProject project) {
		return cuCache.withCompilationUnit(project, docUri, cu -> {
			if (cu == null) {
				return Collections.<String>emptyList();
			}

			ITypeBinding binding = findTypeBinding(cu, typeFqName);
			if (binding == null) {
				return Collections.<String>emptyList();
			}

			List<String> superTypes = new ArrayList<>();
			for (Iterator<String> itr = ASTUtils.getHierarchyTypesFqNamesBreadthFirstIterator(binding); itr.hasNext();) {
				String superTypeFqName = itr.next();
				if (!typeFqName.equals(superTypeFqName)) {
					superTypes.add(superTypeFqName);
				}
			}
			return superTypes;
		});
	}

	private static ITypeBinding findTypeBinding(CompilationUnit cu, String typeFqName) {
		ITypeBinding[] binding = new ITypeBinding[1];

		cu.accept(new ASTVisitor() {

			private boolean visitType(AbstractTypeDeclaration node) {
				if (binding[0] == null && hasSimpleNameOf(node, typeFqName)) {
					binding[0] = node.resolveBinding();
				}
				return binding[0] == null;
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				return visitType(node);
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				return visitType(node);
			}

			@Override
			public boolean visit(RecordDeclaration node) {
				return visitType(node);
			}

		});
		return binding[0];
	}

	private static String withoutTypeParameters(String typeFqName) {
		if (typeFqName == null) {
			return null;
		}
		int idx = typeFqName.indexOf('<');
		return idx < 0 ? typeFqName : typeFqName.substring(0, idx);
	}

	/**
	 * @param typeFqName the type the method is expected to be declared in, or null to
	 *                   accept a matching method declaration anywhere in the document
	 */
	private List<LocationLink> findMethodPositionInDoc(URI docUrl, String typeFqName, String methodName,
			IJavaProject project) {

		return cuCache.withCompilationUnit(project, docUrl, cu -> {
			List<LocationLink> locationLinks = new ArrayList<>();
			try {
				if (cu != null) {
					TextDocument document = new TextDocument(docUrl.toString(), LanguageId.JAVA);
					document.setText(cuCache.fetchContent(docUrl));
					cu.accept(new ASTVisitor() {

						@Override
						public boolean visit(MethodDeclaration node) {
							SimpleName nameNode = node.getName();
							if (nameNode.getIdentifier().equals(methodName) && isDeclaredIn(node, typeFqName)) {
								int start = nameNode.getStartPosition();
								int end = start + nameNode.getLength();
								DocumentRegion region = new DocumentRegion(document, start, end);
								try {
									Range docRange = document.toRange(region);
									locationLinks.add(new LocationLink(document.getUri(), docRange, docRange));
								} catch (BadLocationException e) {
									logger.error("", e);
								}
							}
							return super.visit(node);
						}
					});
				}
			} catch (URISyntaxException e) {
				logger.error("Error parsing the document url: " + docUrl);
			} catch (Exception e) {
				logger.error("error finding method location in doc '", e);
			}
			return locationLinks;
		});
	}

	/**
	 * A document can contain more than one type, so make sure the method really belongs
	 * to the type that is being looked at.
	 */
	private static boolean isDeclaredIn(MethodDeclaration method, String typeFqName) {
		if (typeFqName == null) {
			return true;
		}

		for (ASTNode parent = method.getParent(); parent != null; parent = parent.getParent()) {
			if (parent instanceof AnonymousClassDeclaration) {
				// a method of an anonymous class is not a member of the enclosing type
				return false;
			}
			if (parent instanceof AbstractTypeDeclaration type) {
				return hasSimpleNameOf(type, typeFqName);
			}
		}
		return false;
	}

	private static boolean hasSimpleNameOf(AbstractTypeDeclaration type, String typeFqName) {
		String simpleName = typeFqName.substring(typeFqName.lastIndexOf('.') + 1);
		// nested types are separated by '$' within fully qualified names
		simpleName = simpleName.substring(simpleName.lastIndexOf('$') + 1);

		return simpleName.equals(type.getName().getIdentifier());
	}

	private List<LocationLink> findBeansWithName(IJavaProject project, String beanName) {
		Bean[] beans = this.springIndex.getBeansWithName(project.getElementName(), beanName);
		return Arrays.stream(beans).map(bean -> {
			return new LocationLink(bean.getLocation().getUri(), bean.getLocation().getRange(),
					bean.getLocation().getRange());
		}).collect(Collectors.toList());
	}

	private List<TokenData> computeTokens(EmbeddedLanguageSnippet snippet, int offset) {
		SpelLexer lexer = new SpelLexer(CharStreams.fromString(snippet.getText()));
		CommonTokenStream antlrTokens = new CommonTokenStream(lexer);
		SpelParser parser = new SpelParser(antlrTokens);

		List<TokenData> beanReferenceTokens = new ArrayList<>();

		lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
		parser.removeErrorListener(ConsoleErrorListener.INSTANCE);

		parser.addParseListener(new SpelParserBaseListener() {

			@Override
			public void exitBeanReference(BeanReferenceContext ctx) {
				if (ctx.IDENTIFIER() != null) {
					addTokenData(ctx.IDENTIFIER().getSymbol(), offset);
				}
				if (ctx.STRING_LITERAL() != null) {
					addTokenData(ctx.STRING_LITERAL().getSymbol(), offset);
				}
			}

			private void addTokenData(Token sym, int offset) {
				List<IRegion> symbolRegions = snippet.toJavaRanges(new Region(sym.getStartIndex(), sym.getText().length()));
				int start = symbolRegions.get(0).getStart();
				int end = symbolRegions.get(symbolRegions.size() - 1).getEnd();
				if (isOffsetWithinToken(start, end, offset)) {
					beanReferenceTokens.add(new TokenData(sym.getText(), start, end));
				}
			}

			private boolean isOffsetWithinToken(int tokenStartIndex, int tokenEndIndex, int offset) {
				return tokenStartIndex <= (offset) && (offset) <= tokenEndIndex;
			}

		});

		parser.spelExpr();

		return beanReferenceTokens;
	}

	private Optional<Tuple2<String, String>> parseAndExtractMethodClassPairFromSpel(EmbeddedLanguageSnippet snippet, int offset) {
		SpelExpressionParser parser = new SpelExpressionParser();
		try {
			org.springframework.expression.Expression expression = parser.parseExpression(snippet.getText());

			SpelExpression spelExpressionAST = (SpelExpression) expression;
			SpelNode rootNode = spelExpressionAST.getAST();
			return extractMethodClassPairFromSpelNodes(rootNode, null, snippet, offset);
		} catch (ParseException e) {
			// invalid or incomplete SpEL expressions are a normal situation while editing,
			// the reconciler takes care of reporting those to the user
			logger.debug("cannot parse SpEL expression", e);
		}
		return Optional.empty();
	}

	private Optional<Tuple2<String, String>> extractMethodClassPairFromSpelNodes(SpelNode node, SpelNode parent,
			EmbeddedLanguageSnippet snippet, int offset) {
		if (node instanceof MethodReference && checkOffsetInMethodName(node, snippet.toJavaOffset(0), offset)) {
			MethodReference methodRef = (MethodReference) node;
			String methodName = methodRef.getName();
			String className = extractClassNameFromParent(parent);
	        if (className != null) {
	            return Optional.of(Tuples.of(methodName, className));
	        }
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			Optional<Tuple2<String, String>> result = extractMethodClassPairFromSpelNodes(node.getChild(i), node,
					snippet, offset);
			if (result.isPresent()) {
				return result;
			}
		}
		return Optional.empty();
	}
	
	private String extractClassNameFromParent(SpelNode parent) {
		if (parent != null) {
			if (parent instanceof PropertyOrFieldReference) {
				return ((PropertyOrFieldReference) parent).getName();
			} else if (parent instanceof TypeReference) {
				return ((TypeReference) parent).toStringAST();
			} else if (parent instanceof CompoundExpression) {
				for (int i = 0; i < parent.getChildCount(); i++) {
					SpelNode child = parent.getChild(i);
					if (child instanceof PropertyOrFieldReference || child instanceof BeanReference
							|| child instanceof TypeReference) {
						return child.toStringAST();
					}
				}
			}
		}
		return null;
	}

	private boolean checkOffsetInMethodName(SpelNode node, int nodeOffset, int offset) {
		int start = node.getStartPosition() + nodeOffset;
		int end = node.getEndPosition() + nodeOffset;
		return start <= (offset) && (offset) <= end;
	}

}
