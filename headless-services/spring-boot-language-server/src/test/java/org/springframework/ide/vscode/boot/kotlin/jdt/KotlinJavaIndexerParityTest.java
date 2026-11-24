/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.kotlin.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.beans.ComponentIndexer;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryIndexer;
import org.springframework.ide.vscode.boot.java.data.QueryMethodIndexElement;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.lombok.LombokSettings;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.utils.DocumentUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaAstScanner;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaParserUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaScanResult;
import org.springframework.ide.vscode.boot.kotlin.AllKotlinParsers;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
import org.springframework.ide.vscode.boot.kotlin.ast.KotlinDomBuilder;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinDeclarationIndex;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * The unchanged {@link ComponentIndexer} on Kotlin code and on its Java twin, the Java code that
 * the Kotlin code is on the JVM: the elements of both have to be the same (apart from where they
 * are in the source). The Java side is parsed by JDT, with the bindings of the classpath of the
 * project, so it is the reference for what the Kotlin side has to produce.
 *
 * @author Martin Lippert
 */
@AllKotlinParsers
public class KotlinJavaIndexerParityTest {

	private static final String DATA_PROJECT = "test-spring-data-symbols";
	private static final String WEBFLUX_PROJECT = "test-webflux-project";

	@Parameter
	String parser;

	@Test
	void repositoriesWithTheirDomainTypeAndQueryMethods() throws Exception {
		List<String> elements = assertSameElements(DATA_PROJECT, """
				package example.probe

				import org.springframework.data.jpa.repository.Query
				import org.springframework.data.repository.CrudRepository
				import org.springframework.data.repository.Repository

				class Customer

				interface CustomerRepo : CrudRepository<Customer, Long> {
					fun findByName(name: String): List<Customer>
					fun findByAge(age: Int?): Customer?
					fun countByActive(active: Boolean): Long
					@Query("select c from Customer c")
					fun all(): List<Customer>
				}

				interface DirectRepo : Repository<Customer, Long> {
					fun findByName(name: String, other: Array<String>): Customer
				}
				""", """
				package example.probe;

				import java.util.List;
				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.CrudRepository;
				import org.springframework.data.repository.Repository;

				class Customer {}

				interface CustomerRepo extends CrudRepository<Customer, Long> {
					List<Customer> findByName(String name);
					Customer findByAge(Integer age);
					long countByActive(boolean active);
					@Query("select c from Customer c")
					List<Customer> all();
				}

				interface DirectRepo extends Repository<Customer, Long> {
					Customer findByName(String name, String[] other);
				}
				""");

		// what makes the comparison meaningful: the repositories are found, with their domain type and their query methods
		assertThat(elements).anyMatch(line -> line.endsWith("| @+ 'customerRepo' Repository(Customer)"));
		assertThat(elements).anyMatch(line -> line.endsWith("| @+ 'directRepo' Repository(Customer)"));
		assertThat(elements).anyMatch(line -> line.contains("QueryMethodIndexElement findByName(String) : List<Customer>"));
		assertThat(elements).anyMatch(line -> line.contains("QueryMethodIndexElement findByAge(Integer) : Customer"));
		assertThat(elements).anyMatch(line -> line.contains("QueryMethodIndexElement findByName(String, String[]) : Customer"));
		assertThat(elements).anyMatch(line -> line.contains("QueryMethodIndexElement all() : List<Customer>") && line.contains("select c from Customer c"));
	}

	@Test
	void configurationPropertiesFromPropertiesAndConstructors() throws Exception {
		assertSameElements(DATA_PROJECT, """
				package example.probe

				import org.springframework.boot.context.properties.ConfigurationProperties
				import org.springframework.stereotype.Component

				@ConfigurationProperties("app")
				data class DataProps(val name: String, val size: Int = 1)

				@ConfigurationProperties(prefix = "app2")
				class VarProps {
					var name: String = ""
					lateinit var other: String
					var count: Int? = null
				}

				@Component
				@ConfigurationProperties("app3")
				class ComponentProps(val name: String, notAProperty: String)
				""", """
				package example.probe;

				import org.springframework.boot.context.properties.ConfigurationProperties;
				import org.springframework.stereotype.Component;

				@ConfigurationProperties("app")
				final class DataProps {
					private final String name;
					private final int size;
					public DataProps(String name, int size) { this.name = name; this.size = size; }
				}

				@ConfigurationProperties(prefix = "app2")
				class VarProps {
					private String name = "";
					private String other;
					private Integer count = null;
				}

				@Component
				@ConfigurationProperties("app3")
				class ComponentProps {
					private final String name;
					public ComponentProps(String name, String notAProperty) { this.name = name; }
				}
				""");
	}

	@Test
	void injectionPointsOfComponentsAndBeanMethods() throws Exception {
		assertSameElements(DATA_PROJECT, """
				package example.probe

				import org.springframework.beans.factory.annotation.Autowired
				import org.springframework.beans.factory.annotation.Qualifier
				import org.springframework.beans.factory.annotation.Value
				import org.springframework.boot.context.event.ApplicationReadyEvent
				import org.springframework.context.annotation.Bean
				import org.springframework.context.annotation.Configuration
				import org.springframework.context.event.EventListener
				import org.springframework.stereotype.Component

				class Greeter
				class Other

				@Component
				class ConstructorInjection(private val greeter: Greeter, @Qualifier("special") val other: Other, @Value("\\${x}") x: String)

				@Component
				class FieldInjection {
					@Autowired
					lateinit var greeter: Greeter

					@Autowired
					@Qualifier("q")
					lateinit var other: Other

					var notInjected: Greeter? = null
				}

				@Component
				class SecondaryConstructor {
					@Autowired
					constructor(greeter: Greeter)
				}

				@Component
				class TwoConstructors(greeter: Greeter) {
					constructor(greeter: Greeter, other: Other) : this(greeter)
				}

				@Component
				class SetterInjection {
					@Autowired
					fun setGreeter(greeter: Greeter) {}
				}

				@Configuration
				class AppConfig {
					@Bean
					fun greeter(): Greeter = Greeter()

					@Bean
					fun other(greeter: Greeter, @Qualifier("q") second: Greeter): Other = Other()

					@EventListener
					fun onReady(event: ApplicationReadyEvent) {}
				}
				""", """
				package example.probe;

				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.beans.factory.annotation.Qualifier;
				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.boot.context.event.ApplicationReadyEvent;
				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.context.event.EventListener;
				import org.springframework.stereotype.Component;

				class Greeter {}
				class Other {}

				@Component
				class ConstructorInjection {
					private final Greeter greeter;
					private final Other other;
					public ConstructorInjection(Greeter greeter, @Qualifier("special") Other other, @Value("${x}") String x) { this.greeter = greeter; this.other = other; }
				}

				@Component
				class FieldInjection {
					@Autowired
					private Greeter greeter;

					@Autowired
					@Qualifier("q")
					private Other other;

					private Greeter notInjected = null;
				}

				@Component
				class SecondaryConstructor {
					@Autowired
					public SecondaryConstructor(Greeter greeter) {}
				}

				@Component
				class TwoConstructors {
					public TwoConstructors(Greeter greeter) {}
					public TwoConstructors(Greeter greeter, Other other) { this(greeter); }
				}

				@Component
				class SetterInjection {
					@Autowired
					public void setGreeter(Greeter greeter) {}
				}

				@Configuration
				class AppConfig {
					@Bean
					public Greeter greeter() { return new Greeter(); }

					@Bean
					public Other other(Greeter greeter, @Qualifier("q") Greeter second) { return new Other(); }

					@EventListener
					public void onReady(ApplicationReadyEvent event) {}
				}
				""");
	}

	/**
	 * Known gap: the routes of a router function are read from the body of the bean method, and
	 * the Kotlin AST has no function bodies (they would need expressions with resolved method
	 * calls, and the router DSL of Kotlin needs an extractor of its own). The beans themselves are
	 * the same, the routes are missing on the Kotlin side. This test fails once the routes are
	 * there, and then the two can be compared with {@link #assertSameElements}.
	 */
	@Test
	void routerFunctionsWithoutTheirRoutes() throws Exception {
		List<String> kotlin = indexKotlin(WEBFLUX_PROJECT, """
				package example.probe

				import org.springframework.context.annotation.Bean
				import org.springframework.context.annotation.Configuration
				import org.springframework.web.reactive.function.server.RequestPredicates
				import org.springframework.web.reactive.function.server.RouterFunction
				import org.springframework.web.reactive.function.server.RouterFunctions
				import org.springframework.web.reactive.function.server.ServerResponse

				@Configuration
				class Routes {
					@Bean
					fun hello(): RouterFunction<ServerResponse> =
						RouterFunctions.route(RequestPredicates.GET("/hello"), { ServerResponse.ok().build() })
				}
				""");
		List<String> java = indexJava(WEBFLUX_PROJECT, """
				package example.probe;

				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.function.server.RequestPredicates;
				import org.springframework.web.reactive.function.server.RouterFunction;
				import org.springframework.web.reactive.function.server.RouterFunctions;
				import org.springframework.web.reactive.function.server.ServerResponse;

				@Configuration
				class Routes {
					@Bean
					public RouterFunction<ServerResponse> hello() {
						return RouterFunctions.route(RequestPredicates.GET("/hello"), request -> ServerResponse.ok().build());
					}
				}
				""");

		assertThat(java).anyMatch(line -> line.contains("/hello"));
		assertThat(kotlin).noneMatch(line -> line.contains("/hello"));
		// the same, apart from the route elements, which are the children of the bean method's bean
		assertThat(kotlin).isEqualTo(java.stream().filter(line -> !line.startsWith("        ")).toList());
	}

	// ---- indexing both sides

	/** @return the elements, which are the same for both */
	private List<String> assertSameElements(String projectName, String kotlinSource, String javaSource) throws Exception {
		KotlinJdtBindings.resetGaps();

		List<String> kotlin = indexKotlin(projectName, kotlinSource);
		List<String> java = indexJava(projectName, javaSource);

		assertThat(kotlin).isNotEmpty();
		assertThat(String.join("\n", kotlin)).isEqualTo(String.join("\n", java));
		assertThat(KotlinJdtBindings.gaps()).isEmpty();
		return kotlin;
	}

	private List<String> indexKotlin(String projectName, String source) throws Exception {
		IJavaProject project = ProjectsHarness.INSTANCE.mavenProject(projectName);
		KotlinSyntax.File file = KotlinTestParsers.reader(parser).read("Probe.kt", source);
		KotlinDeclarationIndex declarations = new KotlinDeclarationIndex();
		declarations.put("file:///probe/Probe.kt", file);
		CompilationUnit cu = new KotlinDomBuilder().build(file, new KotlinTypeResolver(project.getIndex(), declarations)).compilationUnit();
		return index(project, cu, "file:///probe/Probe.kt", "/probe/Probe.kt", source);
	}

	private static List<String> indexJava(String projectName, String source) throws Exception {
		IJavaProject project = ProjectsHarness.INSTANCE.mavenProject(projectName);

		ASTParser parser = ASTParser.newParser(AST.JLS25);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setEnvironment(SpringIndexerJavaParserUtils.getClasspathEntries(project), new String[0], null, false);
		parser.setUnitName("/probe/Probe.java");
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		return index(project, cu, "file:///probe/Probe.java", "/probe/Probe.java", source);
	}

	private static List<String> index(IJavaProject project, CompilationUnit cu, String docURI, String file, String source) {
		AnnotationHierarchies.set(cu, new AnnotationHierarchies());

		// the repository indexer asks the metadata service about the query methods without a query annotation
		SpringComponentIndexer[] indexers = { new ComponentIndexer(new DataRepositoryIndexer(new DataRepositoryAotMetadataService(null, null, null))) };
		SpringIndexerJavaAstScanner scanner = new SpringIndexerJavaAstScanner(indexers, new SpringIndexerJavaDependencyTracker(), (context, index) -> {});

		TextDocument doc = DocumentUtils.createTempTextDocument(docURI, source);
		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] { file });
		IProblemCollector none = new IProblemCollector() {
			@Override public void beginCollecting() {}
			@Override public void endCollecting() {}
			@Override public void accept(ReconcileProblem problem) {}
		};
		SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, file, 0, doc, source, none,
				new ArrayList<>(), true, false, result, LombokSettings.DISABLED);

		scanner.scanAST(context, false, new ReconcilingIndex(), false);

		List<String> lines = new ArrayList<>();
		result.getGeneratedIndexElements().stream()
				.map(CachedIndexElement::getIndexElement)
				.filter(element -> element instanceof Bean)
				.sorted((a, b) -> ((Bean) a).getName().compareTo(((Bean) b).getName()))
				.forEach(element -> describe(element, "", lines));
		return lines;
	}

	/** what the structure view shows of an element, without the places in the source, which differ between the twins */
	private static void describe(SpringIndexElement element, String indent, List<String> lines) {
		if (element instanceof Bean bean) {
			lines.add(indent + "Bean " + bean.getName() + " : " + bean.getType() + (bean.isConfiguration() ? " configuration" : "")
					+ " | " + bean.getSymbolLabel());
			lines.add(indent + "  annotations " + annotationTypes(bean.getAnnotations()));
			lines.add(indent + "  supertypes " + bean.getSupertypes().stream().sorted().toList());
			for (InjectionPoint injectionPoint : bean.getInjectionPoints()) {
				lines.add(indent + "  inject " + injectionPoint.getName() + " : " + injectionPoint.getType() + " " + annotationTypes(injectionPoint.getAnnotations()));
			}
		}
		else if (element instanceof EventListenerIndexElement listener) {
			lines.add(indent + "EventListener " + listener.getEventType() + " in " + listener.getContainerBeanType());
		}
		else if (element instanceof QueryMethodIndexElement query) {
			lines.add(indent + "QueryMethodIndexElement " + query.getMethodName() + " | query: " + query.getQueryString());
		}
		else if (element instanceof SymbolElement symbol) {
			lines.add(indent + element.getClass().getSimpleName() + " " + symbol.getDocumentSymbol().getName());
		}
		else {
			lines.add(indent + element.getClass().getSimpleName());
		}

		for (SpringIndexElement child : element.getChildren()) {
			describe(child, indent + "    ", lines);
		}
	}

	private static String annotationTypes(AnnotationMetadata[] annotations) {
		return annotations == null ? "[]" : Arrays.stream(annotations).map(AnnotationMetadata::getAnnotationType).collect(Collectors.joining(", ", "[", "]"));
	}

}
