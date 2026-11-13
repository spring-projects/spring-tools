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
import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.kotlin.AllKotlinParsers;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.beans.ComponentIndexer;
import org.springframework.ide.vscode.boot.java.beans.SpringBootApplicationIndexElement;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryIndexer;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.lombok.LombokSettings;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.utils.DocumentUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaAstScanner;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaScanResult;
import org.springframework.ide.vscode.boot.kotlin.ast.KotlinDomBuilder;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinDeclarationIndex;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * The {@link ComponentIndexer} of the Java side, unchanged, on a Kotlin file through the JDT bridge.
 * <p>
 * Known gaps, which come from the AST that is built, not from the bridge: injection points (the
 * primary constructor and properties are not built), the event type of {@code @EventListener}
 * methods (parameters are not built), and inferred return types of {@code @Bean} functions (the
 * Kotlin type resolution does not infer types).
 *
 * @author Martin Lippert
 */
@AllKotlinParsers
public class KotlinJdtBridgeComponentIndexerTest {

	@Parameter
	String parser;

	private KotlinSyntaxReader reader() {
		return KotlinTestParsers.reader(parser);
	}

	@Test
	void componentIndexerRunsUnchangedOnKotlin() throws Exception {
		String source = """
				package example.probe

				import org.springframework.beans.factory.annotation.Autowired
				import org.springframework.boot.autoconfigure.SpringBootApplication
				import org.springframework.boot.context.event.ApplicationReadyEvent
				import org.springframework.boot.context.properties.ConfigurationProperties
				import org.springframework.context.ApplicationListener
				import org.springframework.context.annotation.Bean
				import org.springframework.context.annotation.Configuration
				import org.springframework.context.event.EventListener
				import org.springframework.stereotype.Component

				class Greeter

				@Configuration
				class AppConfig {
					@Bean
					fun greeter(): Greeter = Greeter()

					@Bean
					fun inferred() = Greeter()

					@EventListener
					fun onReady(event: ApplicationReadyEvent) {}
				}

				@Component
				class Listener : ApplicationListener<ApplicationReadyEvent> {
					override fun onApplicationEvent(event: ApplicationReadyEvent) {}
				}

				@Component
				class WithInjection(private val greeter: Greeter) {
					@Autowired
					lateinit var other: Greeter
				}

				@ConfigurationProperties("app")
				class Props

				@SpringBootApplication
				class App
				""";

		MavenJavaProject project = ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-stereotypes-support");
		KotlinSyntax.File file = reader().read("Probe.kt", source);
		KotlinDeclarationIndex declarations = new KotlinDeclarationIndex();
		declarations.put("file:///probe/Probe.kt", file);
		CompilationUnit cu = new KotlinDomBuilder().build(file, new KotlinTypeResolver(project.getIndex(), declarations)).compilationUnit();
		AnnotationHierarchies.set(cu, new AnnotationHierarchies());

		KotlinJdtBindings.resetGaps();

		SpringComponentIndexer[] indexers = { new ComponentIndexer(new DataRepositoryIndexer(null)) };
		SpringIndexerJavaAstScanner scanner = new SpringIndexerJavaAstScanner(indexers, new SpringIndexerJavaDependencyTracker(), (context, index) -> {});

		String docURI = "file:///probe/Probe.kt";
		TextDocument doc = DocumentUtils.createTempTextDocument(docURI, source);
		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] { "/probe/Probe.kt" });
		IProblemCollector none = new IProblemCollector() {
			@Override public void beginCollecting() {}
			@Override public void endCollecting() {}
			@Override public void accept(ReconcileProblem problem) {}
		};
		SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, "/probe/Probe.kt", 0, doc, source, none,
				new ArrayList<>(), true, false, result, LombokSettings.DISABLED);

		scanner.scanAST(context, false, new ReconcilingIndex(), false);

		List<String> lines = new ArrayList<>();
		for (CachedIndexElement element : result.getGeneratedIndexElements()) {
			describe(element.getIndexElement(), "", lines);
		}

		assertThat(lines).containsExactly(
				"Bean appConfig : example.probe.AppConfig configuration",
				"  Bean greeter : example.probe.Greeter",
				"  Bean inferred : void", // known gap: inferred return type
				"  EventListener  in example.probe.AppConfig", // known gap: parameters are not built
				"Bean listener : example.probe.Listener",
				"  EventListener org.springframework.boot.context.event.ApplicationReadyEvent in example.probe.Listener",
				"Bean withInjection : example.probe.WithInjection", // known gap: no injection points
				"Bean props : example.probe.Props",
				"Bean app : example.probe.App configuration");

		// the main application package, for the structure view, without any Kotlin specific code
		assertThat(result.getGeneratedIndexElements()).anyMatch(element -> element.getIndexElement() instanceof SpringBootApplicationIndexElement app
				&& app.getPackageName().equals("example.probe") && app.getTypeName().equals("App"));

		assertThat(KotlinJdtBindings.gaps()).isEmpty();
	}

	private static void describe(SpringIndexElement element, String indent, List<String> lines) {
		if (element instanceof Bean bean) {
			assertThat(bean.getInjectionPoints()).isEmpty();
			lines.add(indent + "Bean " + bean.getName() + " : " + bean.getType() + (bean.isConfiguration() ? " configuration" : ""));
		}
		else if (element instanceof EventListenerIndexElement listener) {
			lines.add(indent + "EventListener " + listener.getEventType() + " in " + listener.getContainerBeanType());
		}
		else {
			return;
		}
		for (SpringIndexElement child : element.getChildren()) {
			describe(child, indent + "  ", lines);
		}
	}

}
