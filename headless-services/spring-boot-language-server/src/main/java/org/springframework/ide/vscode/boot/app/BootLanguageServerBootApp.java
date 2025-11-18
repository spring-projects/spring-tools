/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.ide.vscode.boot.common.PropertyCompletionFactory;
import org.springframework.ide.vscode.boot.common.RelaxedNameConfig;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheOnDiscDeltaBased;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheVoid;
import org.springframework.ide.vscode.boot.java.BuildCommandProvider;
import org.springframework.ide.vscode.boot.java.JavaDefinitionHandler;
import org.springframework.ide.vscode.boot.java.beans.DependsOnDefinitionProvider;
import org.springframework.ide.vscode.boot.java.beans.NamedDefinitionProvider;
import org.springframework.ide.vscode.boot.java.beans.QualifierDefinitionProvider;
import org.springframework.ide.vscode.boot.java.beans.ResourceDefinitionProvider;
import org.springframework.ide.vscode.boot.java.conditionals.ConditionalOnBeanDefinitionProvider;
import org.springframework.ide.vscode.boot.java.conditionals.ConditionalOnResourceDefinitionProvider;
import org.springframework.ide.vscode.boot.java.copilot.util.ResponseModifier;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.data.GenAotQueryMethodImplProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.DataQueryParameterDefinitionProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtDataQuerySemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCodeActionProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaReconcileEngine;
import org.springframework.ide.vscode.boot.java.handlers.JavaCodeActionHandler;
import org.springframework.ide.vscode.boot.java.links.DefaultJavaElementLocationProvider;
import org.springframework.ide.vscode.boot.java.links.EclipseJavaDocumentUriProvider;
import org.springframework.ide.vscode.boot.java.links.JavaDocumentUriProvider;
import org.springframework.ide.vscode.boot.java.links.JavaElementLocationProvider;
import org.springframework.ide.vscode.boot.java.links.JavaServerElementLocationProvider;
import org.springframework.ide.vscode.boot.java.links.JdtJavaDocumentUriProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinkFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.livehover.v2.ProcessType;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorRemote;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorRemote.RemoteBootAppData;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorService;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveDataProvider;
import org.springframework.ide.vscode.boot.java.reconcilers.JavaReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.spel.SpelDefinitionProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.java.value.ValueDefinitionProvider;
import org.springframework.ide.vscode.boot.jdt.ls.JavaProjectsService;
import org.springframework.ide.vscode.boot.jdt.ls.JdtLsProjectCache;
import org.springframework.ide.vscode.boot.metadata.AdHocSpringPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.ClassReferenceProvider;
import org.springframework.ide.vscode.boot.metadata.LoggerNameProvider;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndex;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.boot.properties.completions.SpringPropertiesCompletionEngine;
import org.springframework.ide.vscode.boot.xml.SpringXMLCompletionEngine;
import org.springframework.ide.vscode.boot.yaml.completions.ApplicationYamlAssistContext;
import org.springframework.ide.vscode.boot.yaml.completions.SpringYamlCompletionEngine;
import org.springframework.ide.vscode.commons.RuntimeTypeAdapterFactory;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.languageserver.java.FutureProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentEventListenerManager;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageComputer;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.ServerCapabilityInitializer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.commons.util.LogRedirect;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.ast.YamlParser;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContext;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.completion.YamlCompletionEngine;
import org.springframework.ide.vscode.commons.yaml.completion.YamlCompletionEngineOptions;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;
import org.springframework.ide.vscode.languageserver.starter.LanguageServerAutoConf;
import org.springframework.ide.vscode.languageserver.starter.LanguageServerRunnerAutoConf;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import reactor.core.publisher.Hooks;

@SpringBootConfiguration(proxyBeanMethods = false)
@ImportAutoConfiguration({ 
	// During development you can uncomment the below so that boot dash can detect started state properly:
	// SpringApplicationAdminJmxAutoConfiguration.class,
	LanguageServerAutoConf.class, 
	LanguageServerRunnerAutoConf.class, 
	ConfigurationPropertiesAutoConfiguration.class, 
	PropertyPlaceholderAutoConfiguration.class
})
@ComponentScan(basePackages = {"org.springframework.ide.vscode.boot.app", "org.springframework.ide.vscode.boot.mcp"})
@EnableConfigurationProperties(BootLsConfigProperties.class)
@SpringBootApplication
public class BootLanguageServerBootApp {
	
	private static final String SERVER_NAME = "boot-language-server";
	
	public static void main(String[] args) throws Exception {
		Hooks.onOperatorDebug();
		System.setProperty(LanguageServerRunner.SYSPROP_LANGUAGESERVER_NAME, SERVER_NAME); //makes it easy to recognize language server processes - and set this as early as possible
		
		LogRedirect.bootRedirectToFile(SERVER_NAME); //TODO: use boot (or logback realy) to configure logging instead.
		SpringApplication.run(BootLanguageServerBootApp.class, args);
	}

	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean
	IndexCache symbolCache(BootLsConfigProperties props) {
		if (props.isSymbolCacheEnabled()) {
			return new IndexCacheOnDiscDeltaBased(new File(props.getSymbolCacheDir()));
		} else {
			return new IndexCacheVoid();
		}
	}
	
	@Bean
	SpringMetamodelIndex springMetamodelIndex() {
		return new SpringMetamodelIndex();
	}

	@Bean
	SpringProcessLiveDataProvider liveDataProvider(SimpleLanguageServer server) {
		return new SpringProcessLiveDataProvider(server);
	}
	
	@Bean
	SpringProcessConnectorService processConnectorService(SimpleLanguageServer server, SpringProcessLiveDataProvider liveDataProvider) {
		return new SpringProcessConnectorService(server, liveDataProvider);
	}
	
	@Bean
	SpringProcessConnectorRemote remoteAppsFromSettingsConnector(SimpleLanguageServer server, SpringProcessConnectorService liveDataService) {
		SpringProcessConnectorRemote bean = new SpringProcessConnectorRemote(server, liveDataService);
		server.getWorkspaceService().onDidChangeConfiguraton(settings -> {
			RemoteBootAppData[] appData = settings.getAs(RemoteBootAppData[].class, "boot-java", "remote-apps");
			if (appData == null) {
				//Avoid NPE
				appData = new RemoteBootAppData[0];
			}
			bean.updateApps(appData);
		});

		return bean;
	}
	
	@Bean
	SpringProcessConnectorRemote remoteAppsFromCommandsConnector(SimpleLanguageServer server, SpringProcessConnectorService liveDataService) {
		SpringProcessConnectorRemote bean = new SpringProcessConnectorRemote(server, liveDataService);
		final Map<String, RemoteBootAppData[]> allRemoteApps = new HashMap<>();
		final Gson gson = new Gson();
		server.onCommand("sts/livedata/remoteConnect", params -> {
			List<Object> args = params.getArguments();
			String owner = ((JsonElement) args.get(0)).getAsString();
			RemoteBootAppData[] data = gson.fromJson((JsonElement) args.get(1), RemoteBootAppData[].class);
			if (data.length > 0) {
				allRemoteApps.put(owner, data);
			} else {
				allRemoteApps.remove(owner);
			}
			List<RemoteBootAppData> all = new ArrayList<>();
			for (RemoteBootAppData[] remoteBootAppData : allRemoteApps.values()) {
				for (RemoteBootAppData r : remoteBootAppData) {
					all.add(r);
				}
			}
			bean.updateApps(all.toArray(new RemoteBootAppData[all.size()]));
			return CompletableFuture.completedFuture(null);
		});
		return bean;
	}
	
	@Bean
	SpringProcessConnectorRemote localAppsFromCommandsConnector(SimpleLanguageServer server, SpringProcessConnectorService liveDataService) {
		SpringProcessConnectorRemote bean = new SpringProcessConnectorRemote(server, liveDataService, ProcessType.LOCAL);
		final Map<String, RemoteBootAppData> localApps = new HashMap<>();
		final Gson gson = new Gson();
		server.onCommand("sts/livedata/localAdd", params -> {
			synchronized(localApps) {
				RemoteBootAppData[] newAdditions = params.getArguments().stream().map(a -> gson.fromJson((JsonElement) a, RemoteBootAppData.class)).toArray(RemoteBootAppData[]::new);
				for (RemoteBootAppData app : newAdditions) {
					localApps.put(app.getJmxurl(), app);
				}
				bean.updateApps(localApps.values().toArray(new RemoteBootAppData[localApps.size()]));
				return CompletableFuture.completedFuture(null);
			}
		});
		server.onCommand("sts/livedata/localRemove", params -> {
			synchronized(localApps) {
				List<RemoteBootAppData> removedApps = params.getArguments().stream().map(o -> o instanceof JsonElement ? ((JsonElement) o).getAsString() : (String) o).map(localApps::remove).collect(Collectors.toList());
				if (!removedApps.isEmpty()) {
					bean.updateApps(localApps.values().toArray(new RemoteBootAppData[localApps.size()]));
				}
				return CompletableFuture.completedFuture(null);
			}
		});
		return bean;
	}
	
	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean AdHocSpringPropertyIndexProvider adHocProperties(BootLanguageServerParams params, FileObserver fileObserver, DocumentEventListenerManager documentEvents) {
		return new AdHocSpringPropertyIndexProvider(params.projectFinder, params.projectObserver, fileObserver, documentEvents);
	}

	@Bean FileObserver fileObserver(SimpleLanguageServer server) {
		return server.getWorkspaceService().getFileObserver();
	}

	@Bean ValueProviderRegistry valueProviders() {
		return new ValueProviderRegistry();
	}

	@Bean InitializingBean initializeValueProviders(ValueProviderRegistry r, @Qualifier("adHocProperties") ProjectBasedPropertyIndexProvider adHocProperties, SourceLinks sourceLinks) {
		return () -> {
			r.def("logger-name", LoggerNameProvider.factory(adHocProperties, sourceLinks));
			r.def("class-reference", ClassReferenceProvider.factory(sourceLinks));
		};
	}
	
	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean JavaProjectsService javaProjectsService(SimpleLanguageServer server, BootLsConfigProperties configProperties) {
		return new JdtLsProjectCache(server, configProperties.isEnableJandexIndex());
	}

	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean BootLanguageServerParams serverParams(SimpleLanguageServer server, ValueProviderRegistry valueProviders, JavaProjectsService projectsService, BootJavaConfig config) {
		return BootLanguageServerParams.createDefault(server, valueProviders, projectsService, config);
	}

	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean SourceLinks sourceLinks(SimpleLanguageServer server, CompilationUnitCache cuCache, BootLanguageServerParams params) {
		return SourceLinkFactory.createSourceLinks(server, cuCache, params.projectFinder);
	}

	@Bean CompilationUnitCache cuCache(SimpleLanguageServer server, BootLanguageServerParams params) {
		return new CompilationUnitCache(params.projectFinder, server, params.projectObserver);
	}

	@Bean JdtReconciler jdtReconciler(CompilationUnitCache cuCache, BootJavaConfig config, SimpleLanguageServer server, JdtAstReconciler[] reconcilers, ProjectObserver projectObserver) {
		return new JdtReconciler(cuCache, config, reconcilers, projectObserver);
	}
	
	@Bean SpringXMLCompletionEngine xmlCompletionEngine(SimpleLanguageServer server, JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex, SpringMetamodelIndex springIndex, BootJavaConfig config) {
		return new SpringXMLCompletionEngine(server, projectFinder, symbolIndex, springIndex, config);
	}
	
	@Bean SpringPropertiesCompletionEngine propertiesCompletionEngine(BootLanguageServerParams params, JavaProjectFinder projectFinder, SourceLinks sourceLinks, BootJavaConfig config) {
		return new SpringPropertiesCompletionEngine(
				params.indexProvider, 
				params.typeUtilProvider, 
				projectFinder, sourceLinks, config);
	}

	@Bean YamlCompletionEngine yamlCompletionEngine(YamlStructureProvider structureProvider, YamlAssistContextProvider contextProvider) {
		YamlCompletionEngineOptions options = new YamlCompletionEngineOptions() {
			@Override
			public boolean includeDeindentedProposals() { return false; };
		};
		return new SpringYamlCompletionEngine(structureProvider, contextProvider, options);
	}

	@Bean JavaDocumentUriProvider javaDocumentUriProvider() {
		switch (LspClient.currentClient()) {
		case ECLIPSE:
			// LSP4E doesn't support JDT java doc URIs. Only supports file, eclipse intro and http URIs for docs
			return new EclipseJavaDocumentUriProvider();
		default:
			return new JdtJavaDocumentUriProvider();
		}
	}

	@Bean JavaElementLocationProvider javaElementLocationProvider(SimpleLanguageServer server, CompilationUnitCache cuCache, JavaDocumentUriProvider javaDocUriProvider) {
		switch (LspClient.currentClient()) {
		case ECLIPSE:
		case VSCODE:
		case THEIA:
			return new JavaServerElementLocationProvider(server);
		default:
			return new DefaultJavaElementLocationProvider(cuCache, javaDocUriProvider);
		}
	}

	@Bean Yaml yaml() {
		//TODO: Yaml is not re-entrant. So its a bit fishy to create a 're-usable' bean for this!
		return new Yaml(new SafeConstructor(new LoaderOptions()));
	}

	@Bean YamlASTProvider yamlAstProvider() {
		return new YamlParser();
	}

	@Bean YamlStructureProvider yamlStructureProvider() {
		return YamlStructureProvider.DEFAULT;
	}

	@Bean YamlAssistContextProvider yamlAssistContextProvider(BootLanguageServerParams params, JavaElementLocationProvider javaElementLocationProvider, SourceLinks sourceLinks) {
		return new YamlAssistContextProvider() {
			@Override
			public YamlAssistContext getGlobalAssistContext(YamlDocument ydoc) {
				IDocument doc = ydoc.getDocument();
				SpringPropertyIndex index = params.indexProvider.getIndex(doc);
				return ApplicationYamlAssistContext.global(ydoc, index, new PropertyCompletionFactory(), params.typeUtilProvider.getTypeUtil(sourceLinks, doc), RelaxedNameConfig.COMPLETION_DEFAULTS, javaElementLocationProvider);
			}
		};
	}
	
	@Bean FutureProjectFinder futureProjectFinder(JavaProjectFinder projectFinder, Optional<ProjectObserver> projectObserver) {
		return new FutureProjectFinder(projectFinder, projectObserver);
	}
	
	@Bean
	ServerCapabilityInitializer bootServerCapabilitiesInitializer() {
		return (InitializeParams params, ServerCapabilities cap) -> {
			CodeActionOptions codeActionOptions = new CodeActionOptions();
			codeActionOptions.setCodeActionKinds(List.of(CodeActionKind.Refactor, CodeActionKind.QuickFix));
			codeActionOptions.setResolveProvider(true);
			codeActionOptions.setWorkDoneProgress(true);
			cap.setCodeActionProvider(codeActionOptions);
		};
	}
	
	@Bean
	LanguageComputer languageComputer() {
		return new LanguageComputer() {

			@Override
			public LanguageId computeLanguage(URI uri) {
				Path path = Paths.get(uri.getPath());
				String fileName = path.getFileName().toString();
				switch (Files.getFileExtension(fileName)) {
				case "properties":
					if (path.endsWith("/META-INF/jpa-named-queries.properties")) {
						return LanguageId.JPA_QUERY_PROPERTIES;
					}
					return LanguageId.BOOT_PROPERTIES;
				case "yml":
					return LanguageId.BOOT_PROPERTIES_YAML;
				case "java":
					return LanguageId.JAVA;
				case "class":
					return LanguageId.CLASS;
 				case "xml":
					return LanguageId.XML;
				case "factories":
					return LanguageId.SPRING_FACTORIES;
				default:
					return LanguageId.PLAINTEXT;
				}
			}
		};
	}
	
	@Bean
	BootJavaReconcileEngine getBootJavaReconcileEngine(JavaProjectFinder projectFinder, JavaReconciler[] javaReconcilers) {
		return new BootJavaReconcileEngine(projectFinder, javaReconcilers);
	}
	
	@Bean
	BootJavaCodeActionProvider getBootJavaCodeActionProvider(JavaProjectFinder projectFinder, Collection<JavaCodeActionHandler> codeActionHandlers) {
		return new BootJavaCodeActionProvider(projectFinder, codeActionHandlers);
	}
	
	@Bean
	JavaDefinitionHandler javaDefinitionHandler(SimpleLanguageServer server, CompilationUnitCache cuCache, JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex, JdtDataQuerySemanticTokensProvider qurySemanticTokens) {
		return new JavaDefinitionHandler(cuCache, projectFinder, List.of(
				new ValueDefinitionProvider(),
				new ConditionalOnResourceDefinitionProvider(),
				new DependsOnDefinitionProvider(springIndex),
				new ConditionalOnBeanDefinitionProvider(springIndex),
				new ResourceDefinitionProvider(springIndex),
				new QualifierDefinitionProvider(springIndex),
				new NamedDefinitionProvider(springIndex),
				new DataQueryParameterDefinitionProvider(server.getTextDocumentService(), qurySemanticTokens),
				new SpelDefinitionProvider(springIndex, cuCache)
			),
			List.of(
					new GenAotQueryMethodImplProvider(server, cuCache, projectFinder)
			));
	}
	
	@Bean
	ModulithService modulithService(SimpleLanguageServer server, JavaProjectFinder projectFinder,
			ProjectObserver projectObserver, SpringSymbolIndex springIndexer, SpringMetamodelIndex springIndex,
			BootJavaReconcileEngine reconciler, BootJavaConfig config) {
		return new ModulithService(server, projectFinder, projectObserver, springIndexer, springIndex, reconciler, config);
	}
	
	@Bean
	DataRepositoryAotMetadataService dataAotMetadataService(FileObserver fileObserver, JavaProjectFinder projectFinder, BuildCommandProvider buildCmds) {
		return new DataRepositoryAotMetadataService(fileObserver, projectFinder, buildCmds);
	}
	
	@Bean
	StereotypeCatalogRegistry stereotypeCatalogRegistry(ProjectObserver projectObserver) {
		return new StereotypeCatalogRegistry(projectObserver);
	}
	
	@Bean ResponseModifier responseModifier() {
		return new ResponseModifier();
	}
	
	@Bean
	Consumer<GsonBuilder> configureGson() {
		return builder -> builder
				.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(SpringIndexElement.class, "_internal_node_type")
						.recognizeSubtypes());
	}
}
