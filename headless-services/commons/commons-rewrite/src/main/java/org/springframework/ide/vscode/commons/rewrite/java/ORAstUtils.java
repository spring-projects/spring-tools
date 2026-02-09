/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Parser.Input;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaParser.Builder;
import org.openrewrite.java.JavaParsingException;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.UpdateSourcePositions;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.CompilationUnit;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Range;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.tree.ParsingEventListener;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class ORAstUtils {
		
	private static final Logger log = LoggerFactory.getLogger(ORAstUtils.class);
	
//	private static class ParentMarker implements Marker {
//		
//		private UUID uuid;
//		private J parent;
//		
//		public ParentMarker(J parent) {
//			this.uuid = Tree.randomId();
//			this.parent = parent;
//		}
//		
//		@Override
//		public UUID getId() {
//			return uuid;
//		}
//		
//		public J getParent() {
//			return parent;
//		}
//		
//		public J getGrandParent() {
//			if (parent != null) {
//				return parent.getMarkers().findFirst(ParentMarker.class).map(m -> m.getParent()).orElse(null);
//			}
//			return null;
//		}
//		
//		public <T> T getFirstAnsector(Class<T> clazz) {
//			if (clazz.isInstance(parent)) {
//				return clazz.cast(parent);
//			} else if (parent != null) {
//				return parent.getMarkers().findFirst(ParentMarker.class).map(m -> m.getFirstAnsector(clazz)).orElse(null);
//			}
//			return null;
//		}
//
//		@Override
//		public <T extends Tree> T withId(UUID id) {
//			this.uuid = id;
//			return (T) this;
//		}
//	}
//	
//	private static class AncestersMarker implements Marker {
//		
//		private UUID uuid;
//		private List<J> ancesters = List.of();
//		
//		public AncestersMarker(List<J> ancesters) {
//			this.uuid = Tree.randomId();
//			this.ancesters = ancesters;
//		}
//
//		@Override
//		public UUID getId() {
//			return uuid;
//		}
//		
//		@SuppressWarnings("unchecked")
//		public <T> T getFirstAnsector(Class<T> clazz) {
//			if (ancesters != null) {
//				for (J node : ancesters) {
//					if (clazz.isInstance(node)) {
//						return (T) node;
//					}
//				}
//			}
//			return null;
//		}
//		
//		public J getParent() {
//			if (ancesters != null && !ancesters.isEmpty()) {
//				return ancesters.get(0);
//			}
//			return null;
//		}
//
//		public J getGrandParent() {
//			if (ancesters != null && ancesters.size() > 1) {
//				return ancesters.get(1);
//			}
//			return null;
//		}
//	}
//	
//	private static class MarkParentRecipe extends Recipe {
//
//		@Override
//		public String getDisplayName() {
//			return "Create parent AST node references via markers";
//		}
//		
//		@Override
//		protected TreeVisitor<?, ExecutionContext> getVisitor() {
//			return new JavaIsoVisitor<>() {
//				
//				private Cursor parentCursor(Class<?> clazz) {
//					for (Cursor c = getCursor(); c != null
//							&& !(c.getValue() instanceof SourceFile); c = c.getParent()) {
//						Object o = c.getValue();
//						if (clazz.isInstance(o)) {
//							return c;
//						}
//					}
//					return null;
//				}
//				
//				@Override
//				public J visit(Tree tree, ExecutionContext p) {
//					if (tree instanceof J) {
//						J j = (J) tree;
//						J newJ = super.visit(j, p).withMarkers(j.getMarkers().addIfAbsent(new ParentMarker(null)));
//						
//						List<J> children = p.pollMessage(j.getId().toString(), Collections.emptyList());
//						for (J child : children) {
//							child.getMarkers().findFirst(ParentMarker.class).map(m -> m.parent = newJ);
//						}
//						
//						// Prepare myself for the parent;
//
//						Cursor parentCursor = parentCursor(J.class);
//						if (parentCursor != null) {
//							J parent = parentCursor.getValue();
//							String parentId = parent.getId().toString();
//							List<J> siblings = p.pollMessage(parentId, new ArrayList<J>());
//							siblings.add(newJ);
//							p.putMessage(parentId, siblings);
//						}
//						return newJ;
//					}
//					return (J) tree; 
//				}
//			};
//		}
//		
//	}
//	
	public static J findAstNodeAt(CompilationUnit cu, int offset) {
		AtomicReference<J> f = new AtomicReference<>();
		new JavaIsoVisitor<AtomicReference<J>>() {
			public J visit(Tree tree, AtomicReference<J> found) {
				if (tree == null) {
					return null;
				}
				if (found.get() == null && tree instanceof J) {
					J node = (J) tree;
					Range range = node.getMarkers().findFirst(Range.class).orElse(null);
					if (range != null
							&& range.getStart().getOffset() <= offset
							&& offset <= range.getEnd().getOffset()) {
						super.visit(tree, found);
						if (found.get() == null) {
							found.set(node);
							return node;
						}
					} else {
						return (J) tree;
					}
				}
				return (J) tree;
			};
		}.visitNonNull(cu, f);
		return f.get();
	}
	
//	@SuppressWarnings("unchecked")
//	public static <T> T findNode(J node, Class<T> clazz) {
//		if (clazz.isInstance(node)) {
//			return (T) node;
//		}
//		return node.getMarkers().findFirst(ParentMarker.class).map(m -> m.getFirstAnsector(clazz)).orElse(null); 
//	}
//	
//	public static J getParent(J node) {
//		return node.getMarkers().findFirst(ParentMarker.class).map(m -> m.getParent()).orElse(null);
//	}
	
	public static JavaParser createJavaParser(IJavaProject project) {
		return createJavaParser(() -> createJavaParserBuilder(project));
	}
	
	public static JavaParser createJavaParser(Supplier<Builder<? extends JavaParser, ?>> f) {
		try {
			return f.get().build();
		} catch (Exception e) {
			if (isExceptionFromInterrupedThread(e)) {
				log.debug("", e);
			} else {
				log.error("{}", e);
			}
			return null;
		}
	}
	
	public static Builder<? extends JavaParser, ?> createJavaParserBuilder(IJavaProject project) {
		List<Path> classpath = IClasspathUtil.getAllBinaryRoots(project.getClasspath()).stream().map(f -> f.toPath()).collect(Collectors.toList());
		return JavaParser.fromJavaVersion().classpath(classpath);
	}
	
	public static List<Parser.Input> getParserInputs(SimpleTextDocumentService documents, IJavaProject project) {
		return IClasspathUtil.getProjectJavaSourceFolders(project.getClasspath())
				.flatMap(folder -> {
					try {
						return Files.walk(folder.toPath());
					} catch (IOException e) {
						log.error("", e);
					}
					return Stream.empty();
				})
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().endsWith(".java"))
				.map(p -> getParserInput(documents, p))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}
	
	public static Parser.Input getParserInput(SimpleTextDocumentService documents, Path p) {
		TextDocument doc = documents.getLatestSnapshot(p.toUri().toASCIIString());
		if (doc == null) {
			return Files.exists(p) ? new Parser.Input(p, () -> {
				try {
					return Files.newInputStream(p);
				} catch (IOException e) {
					log.error("", e);
					return new ByteArrayInputStream(new byte[0]);
				}
			}) : null;
		} else {
			return new Parser.Input(p, () -> new ByteArrayInputStream(doc.get().getBytes()));
		}
	}
	
	public static List<CompilationUnit> parse(JavaParser parser, Iterable<Path> sourceFiles) {
		InMemoryExecutionContext ctx = new InMemoryExecutionContext(ORAstUtils::logExceptionWhileParsing);
		ctx.putMessage(JavaParser.SKIP_SOURCE_SET_TYPE_GENERATION, true);
		List<CompilationUnit> cus = Collections.emptyList();
		synchronized(parser) {
			cus = parser.parse(sourceFiles, null, ctx).map(CompilationUnit.class::cast).collect(Collectors.toList());
		}
		org.openrewrite.java.style.Autodetect.Detector javaDetector = org.openrewrite.java.style.Autodetect.detector();
		cus.forEach(javaDetector::sampleJava);
		org.openrewrite.java.style.Autodetect javaStyling = javaDetector.build();
		
		return ListUtils.map(cus, (i, cu) -> {
			cu = cu.withMarkers(cu.getMarkers().addIfAbsent(javaStyling));
			J.CompilationUnit newCu = (J.CompilationUnit) new UpdateSourcePositions().getVisitor().visit(cu, ctx);
			return (newCu == null ? cu : newCu).withMarkers(null);
		});
	}
	
	public static List<SourceFile> parseInputs(JavaParser parser, List<Parser.Input> inputs, Consumer<SourceFile> parseCallback) {
		ExecutionContext ctx = new InMemoryExecutionContext(ORAstUtils::logExceptionWhileParsing);
		ctx.putMessage(JavaParser.SKIP_SOURCE_SET_TYPE_GENERATION, true);
		ctx.putMessage(ExecutionContext.REQUIRE_PRINT_EQUALS_INPUT, false);
		if (parseCallback != null) {
			ParsingExecutionContextView parseContext = ParsingExecutionContextView.view(ctx);
			parseContext.setParsingListener(new ParsingEventListener() {

				@Override
				public void parsed(Input input, SourceFile source) {
					parseCallback.accept(source);
				}
				
			});
			ctx = parseContext;
		}
		
		List<SourceFile> sourceFiles = new ArrayList<>(inputs.size());
		
		synchronized (parser) {
			final ExecutionContext c = ctx;
			List<CompilationUnit> cus = parser.parseInputs(inputs, null, ctx).map(CompilationUnit.class::cast).collect(Collectors.toList());
			// Collect java styling info
			org.openrewrite.java.style.Autodetect.Detector javaDetector = org.openrewrite.java.style.Autodetect.detector();
			cus.forEach(javaDetector::sampleJava);
			org.openrewrite.java.style.Autodetect javaStyling = javaDetector.build();
			sourceFiles.addAll(ListUtils.map(cus, (i, cu) -> (CompilationUnit) new UpdateSourcePositions().getVisitor().visit(cu.withMarkers(cu.getMarkers().add(javaStyling)), c)));
		}
		
        List<SourceFile> xmlSources = new XmlParser().parseInputs(
        		inputs.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".xml") ||
                                p.getPath().getFileName().toString().endsWith(".wsdl") ||
                                p.getPath().getFileName().toString().endsWith(".xhtml") ||
                                p.getPath().getFileName().toString().endsWith(".xsd") ||
                                p.getPath().getFileName().toString().endsWith(".xsl") ||
                                p.getPath().getFileName().toString().endsWith(".xslt"))
                        .collect(Collectors.toList()),
                null,
                ctx
        ).collect(Collectors.toList());
        org.openrewrite.xml.style.Autodetect.Detector xmlDetector = org.openrewrite.xml.style.Autodetect.detector();
        xmlSources.forEach(xmlDetector::sample);
        org.openrewrite.xml.style.Autodetect xmlStyling = xmlDetector.build();
		sourceFiles.addAll(ListUtils.map(xmlSources, (i, s) -> s.withMarkers(s.getMarkers().addIfAbsent(xmlStyling))));

        List<SourceFile> yamlSources = new YamlParser().parseInputs(
                inputs.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".yml") || p.getPath().getFileName().toString().endsWith(".yaml"))
                        .collect(Collectors.toList()),
                null,
                ctx
        ).collect(Collectors.toList());
		sourceFiles.addAll(yamlSources);

        sourceFiles.addAll(new PropertiesParser().parseInputs(
                inputs.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".properties"))
                        .collect(Collectors.toList()),
                null,
                ctx
        ).collect(Collectors.toList()));
        
        sourceFiles.addAll(new PlainTextParser().parseInputs(
                inputs.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".factories"))
                        .collect(Collectors.toList()),
                null,
                ctx
        ).collect(Collectors.toList()));

		return sourceFiles;
	}
	
	private static void logExceptionWhileParsing(Throwable t) {
		if (t instanceof JavaParsingException || t instanceof StringIndexOutOfBoundsException || isExceptionFromInterrupedThread(t)) {
			// Do not log parse exceptions. Can be too many while user is typing code
			log.debug("", t);
		} else {
			log.error("", t);
		}
	}

    public static J.EnumValueSet getEnumValues(J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.EnumValueSet.class::isInstance)
                .map(J.EnumValueSet.class::cast)
                .findAny()
                .orElse(null);
    }

    public static List<J.VariableDeclarations> getFields(J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .collect(Collectors.toList());
    }

    public static List<J.MethodDeclaration> getMethods(J.ClassDeclaration classDecl) {
        return classDecl.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .collect(Collectors.toList());
    }

    public static String getSimpleName(String fqName) {
        int idx = fqName.lastIndexOf('.');
        if (idx < fqName.length() - 1) {
            return fqName.substring(idx + 1);
        }
        return fqName;
    }
    
	public static Recipe nodeRecipe(JavaVisitor<ExecutionContext> v, Predicate<J> condition) {
    	return new NodeRecipe((JavaVisitor<ExecutionContext>) v, condition);
    }
	
	public static Recipe nodeRecipe(Recipe r, Predicate<J> condition) {
    	return new NodeRecipe(r.getVisitor(), condition);
    }
    
    private static class NodeRecipe extends Recipe {
    	
    	private TreeVisitor<?, ExecutionContext> visitor;
    	private Predicate<J> condition;
    	
    	public NodeRecipe(TreeVisitor<?,ExecutionContext> treeVisitor, Predicate<J> condition) {
    		this.visitor = treeVisitor;
    		this.condition = condition;
    	}
    	
    	@Override
		public List<Recipe> getRecipeList() {
			return List.of(new RemoveUnusedImports());
		}

		@Override
    	public String getDisplayName() {
    		return "";
    	}

    	@Override
    	public TreeVisitor<?, ExecutionContext> getVisitor() {
    		return new JavaVisitor<>() {
    			
    			@Override
    			public J visit(Tree tree, ExecutionContext ctx) {
    				if (tree instanceof J) {
    					J t = (J) tree;
        				if (condition.test(t)) {
        					return (J) visitor.visit(t, ctx, getCursor().fork());
        				}
    				}
    				return super.visit(tree, ctx);
    			}

    		};
    	}

		@Override
		public String getDescription() {
			return "";
		}	
    }
    
	public static boolean isExceptionFromInterrupedThread(Throwable t) {
		if (ExceptionUtil.getDeepestCause(t) instanceof InterruptedException) {
			return true;
		}
		if (t instanceof RuntimeException && "Relative paths only".equals(t.getMessage())) {
			return true;
		}
		if (ExceptionUtil.getDeepestCause(t) instanceof ClosedByInterruptException) {
			return true;
		}	
		return false;
	}
	
	public static String getSourceSetName(IJavaProject project, Path sourcePath) {
		if (IClasspathUtil.getProjectTestJavaSources(project.getClasspath()).anyMatch(f -> sourcePath.startsWith(f.toPath()))) {
			return ProjectParser.TEST;
		} else {
			return ProjectParser.MAIN;
		}
	}
	
	public static JavaSourceSet addJavaSourceSet(List<? extends SourceFile> sourceFiles, String sourceSetName, Collection<Path> classpath) {
		JavaSourceSet sourceSet = JavaSourceSet.build(sourceSetName, classpath);
		List<JavaType.FullyQualified> types = sourceSet.getClasspath();
		for (SourceFile sourceFile : sourceFiles) {
			if (!(sourceFile instanceof JavaSourceFile)) {
				continue;
			}

			for (JavaType type : ((JavaSourceFile) sourceFile).getTypesInUse().getTypesInUse()) {
				if (type instanceof JavaType.FullyQualified) {
					types.add((JavaType.FullyQualified) type);
				}
			}
		}
		sourceSet = sourceSet.withClasspath(types);

		for (int i = 0; i < sourceFiles.size(); i++) {
			SourceFile sourceFile = sourceFiles.get(i);
			sourceFiles.set(i, sourceFile
					.withMarkers(sourceFile.getMarkers().computeByType(sourceSet, (original, updated) -> updated)));
		}
		return sourceSet;
	}
}
