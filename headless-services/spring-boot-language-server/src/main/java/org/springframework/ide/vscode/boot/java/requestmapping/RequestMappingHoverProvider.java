/*******************************************************************************
 * Copyright (c) 2017, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.HoverProvider;
import org.springframework.ide.vscode.boot.java.livehover.LiveHoverUtils;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveBean;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMapping;
import org.springframework.ide.vscode.boot.java.livehover.v2.RequestMappingMetrics;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author Martin Lippert
 */
public class RequestMappingHoverProvider implements HoverProvider {

//	private static final String $$_LAMBDA$ = "$$Lambda$";

	private static final Logger log = LoggerFactory.getLogger(RequestMappingHoverProvider.class);

	private static final int CODE_LENS_LIMIT = 3;

	@Override
	public Hover provideHover(ASTNode node, Annotation annotation,
			ITypeBinding type, int offset, TextDocument doc, IJavaProject project, SpringProcessLiveData[] processLiveData) {
		return provideHover(annotation, doc, processLiveData);
	}

	@Override
	public Collection<CodeLens> getLiveHintCodeLenses(IJavaProject project, Annotation annotation, TextDocument doc, SpringProcessLiveData[] processLiveData) {
		try {
			if (processLiveData.length > 0) {
				List<Tuple2<LiveRequestMapping, SpringProcessLiveData>> val = getRequestMappingMethodFromRunningApp(annotation, processLiveData);
				if (!val.isEmpty()) {
					Range hoverRange = doc.toRange(annotation.getStartPosition(), annotation.getLength());
					return assembleCodeLenses(hoverRange, val);
				}
			}
		}
		catch (Exception e) {
			log.error("", e);
		}

		return null;
	}

//	@Override
//	public Collection<CodeLens> getLiveHintCodeLenses(IJavaProject project, MethodDeclaration methodDeclaration,
//			TextDocument doc, SpringBootApp[] runningApps) {
//		try {
//			ImmutableList.Builder<Tuple2<RequestMapping, SpringBootApp>> builder = ImmutableList.builder();
//			if (runningApps.length > 0) {
//				Annotation beanAnnotation = ASTUtils.getBeanAnnotation(methodDeclaration);
//				if (beanAnnotation != null) {
//					ITypeBinding returnTypeBinding = methodDeclaration.getReturnType2().resolveBinding();
//					if ("org.springframework.web.reactive.function.server.RouterFunction".equals(returnTypeBinding.getErasure().getQualifiedName())) {
//						for (SpringBootApp app : runningApps) {
//							List<RequestMapping> matches = findFunctionalRequestMappings(app.getRequestMappings(), methodDeclaration);
//							for (RequestMapping rm : matches) {
//								builder.add(Tuples.of(rm, app));
//							}
//						}
//					}
//				}
//			}
//			List<Tuple2<RequestMapping, SpringBootApp>> data = builder.build();
//			if (!data.isEmpty()) {
//				SimpleName methodName = methodDeclaration.getName();
//				Range hoverRange = doc.toRange(methodName.getStartPosition(), methodName.getLength());
//				return assembleCodeLenses(hoverRange, getUrls(data));
//			}
//		} catch (Exception e) {
//			log.error("", e);
//		}
//		return null;
//	}
//
//	private List<RequestMapping> findFunctionalRequestMappings(Collection<RequestMapping> requestMappings,
//			MethodDeclaration methodDeclaration) {
//		ImmutableList.Builder<RequestMapping> builder = ImmutableList.builder();
//		IMethodBinding binding = methodDeclaration.resolveBinding();
//		if (requestMappings != null) {
//			for (RequestMapping rm : requestMappings) {
//				String fqName = rm.getFullyQualifiedClassName();
//				if (fqName != null) {
//					int lambdaIdx = fqName.indexOf($$_LAMBDA$);
//					if (lambdaIdx > 0) {
//						String containingTypeFqName = fqName.substring(0, lambdaIdx);
//						if (binding.getDeclaringClass().getQualifiedName().equals(containingTypeFqName)) {
//							builder.add(rm);
//						}
//					}
//				}
//			}
//		}
//		return builder.build();
//	}

	private Collection<CodeLens> assembleCodeLenses(Range range, List<Tuple2<LiveRequestMapping, SpringProcessLiveData>> data) {

		Collection<CodeLens> lenses = new ArrayList<>();
		
		int remaining = 0; 
		
		for (Tuple2<LiveRequestMapping, SpringProcessLiveData> dataEntry : data) {
			for (Tuple2<String, String> urlWithPath : getUrlsWithPath(dataEntry)) {
				if (lenses.size() <= CODE_LENS_LIMIT) {
					SpringProcessLiveData liveData = dataEntry.getT2();
					LiveRequestMapping requestMapping = dataEntry.getT1();
					String url = urlWithPath.getT1();
					String path = urlWithPath.getT2();
					Set<String> requestMethods = requestMapping.getRequestMethods();
					
					RequestMappingMetrics metrics = liveData.getLiveMterics() == null ? null
							: liveData.getLiveMterics().getRequestMappingMetrics(new String[] { path },
									requestMethods.toArray(new String[requestMethods.size()]));

					CodeLens codeLens = createCodeLensForRequestMapping(range, url, metrics);
					lenses.add(codeLens);
				} else {
					remaining++;
				}
			}
		}
		
		if (remaining > 0) {
			CodeLens codeLens = createCodeLensForRemaining(range, remaining);
			lenses.add(codeLens);
		}

		return lenses;
	}

	private Hover provideHover(Annotation annotation, TextDocument doc, SpringProcessLiveData[] processLiveData) {

		try {
			List<Tuple2<LiveRequestMapping, SpringProcessLiveData>> val = getRequestMappingMethodFromRunningApp(annotation, processLiveData);

			if (!val.isEmpty()) {
				Hover hover = createHoverWithContent(val);
				Range hoverRange = doc.toRange(annotation.getStartPosition(), annotation.getLength());
				hover.setRange(hoverRange);
				return hover;
			} else {
				return null;
			}

		} catch (Exception e) {
			log.error("", e);
		}

		return null;
	}

	private List<Tuple2<LiveRequestMapping, SpringProcessLiveData>> getRequestMappingMethodFromRunningApp(Annotation annotation,
			SpringProcessLiveData[] processLiveData) {

		List<Tuple2<LiveRequestMapping, SpringProcessLiveData>> results = new ArrayList<>();
		try {
			for (SpringProcessLiveData liveData : processLiveData) {
				LiveRequestMapping[] mappings = liveData.getRequestMappings();
				if (mappings != null && mappings.length > 0) {
					Arrays.stream(mappings)
							.filter(rm -> methodMatchesAnnotation(annotation, rm))
							.map(rm -> Tuples.of(rm, liveData))
							.findFirst().ifPresent(t -> results.add(t));
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return results;
	}

	private boolean methodMatchesAnnotation(Annotation annotation, LiveRequestMapping rm) {
		String rqClassName = rm.getFullyQualifiedClassName();

		if (rqClassName != null) {
			rqClassName = LiveBean.getTypeWithoutCGLib(rqClassName).replace('$', '.');

			ASTNode parent = annotation.getParent();
			if (parent instanceof MethodDeclaration) {
				MethodDeclaration methodDec = (MethodDeclaration) parent;
				IMethodBinding binding = methodDec.resolveBinding();
				if (binding != null) {
					return binding.getDeclaringClass().getQualifiedName().equals(rqClassName)
							&& binding.getName().equals(rm.getMethodName())
							&& Arrays.equals(Arrays.stream(binding.getParameterTypes())
									.map(t -> t.getTypeDeclaration().getQualifiedName())
									.toArray(String[]::new),
								rm.getMethodParameters());
				}
	//		} else if (parent instanceof TypeDeclaration) {
	//			TypeDeclaration typeDec = (TypeDeclaration) parent;
	//			return typeDec.resolveBinding().getQualifiedName().equals(rqClassName);
			}
		}
		return false;
	}

	private List<Tuple2<String, String>> getUrlsWithPath(Tuple2<LiveRequestMapping, SpringProcessLiveData> mappingMethod) {
		List<Tuple2<String, String>> urls = new ArrayList<>();
		SpringProcessLiveData liveData = mappingMethod.getT2();
		String contextPath = liveData.getContextPath();

		String urlScheme = liveData.getUrlScheme();
		String port = liveData.getPort();
		String host = liveData.getHost();

		LiveRequestMapping requestMapping = mappingMethod.getT1();
		String[] paths = requestMapping.getSplitPath();
		if (paths == null || paths.length == 0) {
			//Technically, this means the path 'predicate' is unconstrained, meaning any path matches.
			//So this is not quite the same as the case where path=""... but...
			//It is better for us to show one link where any path is allowed, versus showing no links where any link is allowed.
			//So we'll pretend this is the same as path="" as that gives a working link.
			paths = new String[] {""};
		}
		for (String path : paths) {
			String url = UrlUtil.createUrl(urlScheme, host, port, path, contextPath);
			if (url != null) {
				urls.add(Tuples.of(url, path));
			}
		}
		return urls;
	}

	private Hover createHoverWithContent(List<Tuple2<LiveRequestMapping, SpringProcessLiveData>> mappingMethods) throws Exception {

		StringBuilder contentVal = new StringBuilder();
		for (int i = 0; i < mappingMethods.size(); i++) {
			Tuple2<LiveRequestMapping, SpringProcessLiveData> mappingMethod = mappingMethods.get(i);

			SpringProcessLiveData liveData = mappingMethod.getT2();
			LiveRequestMapping requestMapping = mappingMethod.getT1();
			String urlScheme = liveData.getUrlScheme();
			String port = liveData.getPort();
			String host = liveData.getHost();

			String[] paths = requestMapping.getSplitPath();
			if (paths == null || paths.length == 0) {
				//Technically, this means the path 'predicate' is unconstrained, meaning any path matches.
				//So this is not quite the same as the case where path=""... but...
				//It is better for us to show one link where any path is allowed, versus showing no links where any link is allowed.
				//So we'll pretend this is the same as path="" as that gives a working link.
				paths = new String[] {""};
			}
			String contextPath = liveData.getContextPath();
			List<Renderable> renderableUrls = Arrays.stream(paths).flatMap(path -> {
				String url = UrlUtil.createUrl(urlScheme, host, port, path, contextPath);
				String text = url;
				switch (LspClient.currentClient()) {
				case VSCODE:
				case THEIA:
					url = "command:vscode-spring-boot.open.url?%s".formatted(URLEncoder.encode(new Gson().toJson(url), StandardCharsets.UTF_8));
					break;
				default:
				}
				return Stream.of(Renderables.link(text, url), Renderables.lineBreak());
			})
			.collect(Collectors.toList());

			Renderable urlRenderables = Renderables.concat(renderableUrls);
			
			Set<String> requestMethods = requestMapping.getRequestMethods();
			RequestMappingMetrics metrics = liveData.getLiveMterics() == null ? null
					: liveData.getLiveMterics().getRequestMappingMetrics(requestMapping.getSplitPath(),
							requestMethods.toArray(new String[requestMethods.size()]));
			if (metrics != null) {
				Renderable metricsRenderable = Renderables.concat(Renderables.text(createHoverMetricsContent(metrics)), Renderables.text("\n\n"));
				urlRenderables = Renderables.concat(urlRenderables, metricsRenderable);
			}
			
			Renderable processSection = Renderables.concat(
					urlRenderables,
					Renderables.mdBlob(LiveHoverUtils.niceAppName(liveData))
			);

			if (i < mappingMethods.size() - 1) {
				processSection = Renderables.concat(
						processSection,
						Renderables.text("\n\n")
				);
			}

			String markdown = processSection.toMarkdown();
			contentVal.append(markdown);
		}
		// PT 163470104 - Add content at hover construction to avoid separators
		// being added between the content itself
		return new Hover(new MarkupContent(MarkupKind.MARKDOWN, contentVal.toString()));
	}
	
	private String createHoverMetricsContent(RequestMappingMetrics metrics) {
		char timeUnitShort = metrics.getTimeUnit().name().toLowerCase().charAt(0);
		
		StringBuilder metricsContent = new StringBuilder();
		metricsContent.append("( Count: ");
		metricsContent.append(metrics.getCallsCount());
		metricsContent.append(" | Total Time: ");
		metricsContent.append(metrics.getTotalTime());
		metricsContent.append(timeUnitShort);
		metricsContent.append(" | Max Time: ");
		metricsContent.append(metrics.getMaxTime());
		metricsContent.append(timeUnitShort);
		metricsContent.append(" )");
		
		return metricsContent.toString();
	}

	private String createCodeLensMetricsContent(RequestMappingMetrics metrics) {
		char timeUnitShort = metrics.getTimeUnit().name().toLowerCase().charAt(0);
		
		StringBuilder metricsContent = new StringBuilder();

		metricsContent.append("Count=");
		metricsContent.append(metrics.getCallsCount());
		metricsContent.append(' ');
		metricsContent.append("Total=");
		metricsContent.append(String.format("%.2f", metrics.getTotalTime()));
		metricsContent.append(timeUnitShort);
		metricsContent.append(' ');
		metricsContent.append("Max=");
		metricsContent.append(String.format("%.2f", metrics.getMaxTime()));
		metricsContent.append(timeUnitShort);
		
		return metricsContent.toString();
	}

	private CodeLens createCodeLensForRequestMapping(Range range, String url, RequestMappingMetrics metrics) {
		CodeLens codeLens = new CodeLens();
		codeLens.setRange(range);
		Command cmd = new Command();

		if (StringUtil.hasText(url)) {
			
			StringBuilder codeLensContent = new StringBuilder(url);
			
			if (metrics != null) {
				codeLensContent.append(' ');
				codeLensContent.append('(');
				codeLensContent.append(createCodeLensMetricsContent(metrics));
				codeLensContent.append(')');
			} 
			
			String content = codeLensContent.toString();
			codeLens.setData(content);
			cmd.setTitle(content);
			cmd.setCommand("vscode-spring-boot.open.url");
			cmd.setArguments(ImmutableList.of(url));
		}

		codeLens.setCommand(cmd);

		return codeLens;
	}

	private CodeLens createCodeLensForRemaining(Range range, int remaining) {
		CodeLens codeLens = new CodeLens();
		codeLens.setRange(range);
		Command cmd = new Command();

		cmd.setTitle(remaining + " more...");

		// Don't set an actual command ID as to make this code lens "unclickable"..
		// It is just meant to be a label, to tell users to hover over the request mapping
		// to see the full list

		codeLens.setCommand(cmd);

		return codeLens;
	}
	
}
