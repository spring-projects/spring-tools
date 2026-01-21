/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.maven;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMElement;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lemminx.dom.DOMText;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.rewrite.SpringBootUpgrade;
import org.springframework.ide.vscode.boot.validation.generations.GenerationsValidator;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.VersionValidationUtils;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.InlayHintHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.Optionals;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class PomInlayHintHandler implements InlayHintHandler {

	private static final String POM_XML = "pom.xml";

	private static final Logger log = LoggerFactory.getLogger(PomInlayHintHandler.class);
	
	final private JavaProjectFinder projectFinder;
	final private SpringProjectsProvider generationsProvider;
	
	public PomInlayHintHandler(SimpleLanguageServer server, JavaProjectFinder projectFinder, ProjectObserver projectObserver, SpringProjectsProvider generationsProvider) {
		this.projectFinder = projectFinder;
		this.generationsProvider = generationsProvider;
		
		projectObserver.addListener(new ProjectObserver.Listener() {
			
			private void refreshOpenDocs(IJavaProject project) {
				if (project.getProjectBuild() != null && project.getProjectBuild().getBuildFile() != null ) {
					URI projectBuildFileUri = project.getProjectBuild().getBuildFile();
					if (projectBuildFileUri.toString().endsWith(POM_XML)) {
						server.getTextDocumentService().getAll().stream()
							.map(doc -> URI.create(doc.getId().getUri()))
							.filter(projectBuildFileUri::equals)
							.findFirst().ifPresent(docPath -> {
								server.getClient().refreshInlayHints();
							});
					}
				}
			}
			
			@Override
			public void deleted(IJavaProject project) {
				refreshOpenDocs(project);
			}
			
			@Override
			public void created(IJavaProject project) {
				refreshOpenDocs(project);
			}
			
			@Override
			public void changed(IJavaProject project) {
				refreshOpenDocs(project);
			}
		});

	}

	@Override
	public List<InlayHint> handle(TextDocument doc, Range range, CancelChecker cancelChecker) {
		URI uri = URI.create(doc.getUri());
		if ("file".equals(uri.getScheme()) && POM_XML.equals(Paths.get(uri).getFileName().toString())) {
			
			List<InlayHintWithLazyPosition> inlayHintProviders = new ArrayList<>();

			Optional<IJavaProject> projectOpt = projectFinder.find(doc.getId());
			if (projectOpt.isPresent()) {

				IJavaProject jp = projectOpt.get();
				URI buildFileUri = jp.getProjectBuild().getBuildFile();

				if (buildFileUri.equals(uri) && SpringProjectUtil.isBootProject(jp)) {
					Version currentVersion = SpringProjectUtil.getSpringBootVersion(jp);
					if (currentVersion != null) {
						try {
							ResolvedSpringProject genProject = generationsProvider.getProject(SpringProjectUtil.SPRING_BOOT);
							if (genProject != null) {
								Version latestPatch = VersionValidationUtils.getNewerLatestPatchRelease(genProject.getReleases(), currentVersion);
								if (latestPatch != null) {
									inlayHintProviders.add(new InlayHintWithLazyPosition(() -> {
										Command command = new Command();
										command.setTitle("Upgrade to the Latest Patch");
										command.setCommand(SpringBootUpgrade.CMD_UPGRADE_SPRING_BOOT);
										command.setArguments(List.of(jp.getLocationUri().toASCIIString(), latestPatch.toString(), false));
										
										InlayHintLabelPart label = new InlayHintLabelPart("Upgrade to the Latest Patch");
										label.setCommand(command);
										
										InlayHint hint = new InlayHint();
										hint.setKind(InlayHintKind.Parameter);
										hint.setPaddingLeft(true);
										hint.setLabel(List.of(label));
										return hint;
									}, (d, e) -> {
										
										Optional<String> parentArtifactIdOpt = findChildElement(e, 0, "parent", "artifactId")
												.flatMap(PomInlayHintHandler::getNodeValue);

										if (parentArtifactIdOpt.isPresent() && "spring-boot-starter-parent".equals(parentArtifactIdOpt.get())) {

											Optional<DOMElement> parentVersionOpt = findChildElement(e, 0, "parent", "version");
											if (parentVersionOpt.isPresent()) {
												DOMElement parentVersion = parentVersionOpt.get();
												// Get the current version in the POM in case file is not saved
												
												Optional<Version> parentVersionValueOpt = getNodeValue(parentVersion)
														.flatMap(value -> Optionals.ofThrowable((s) -> Version.parse(s), value));

												if (parentVersionValueOpt.isPresent() && parentVersionValueOpt.get().compareTo(latestPatch) < 0) {
													try {
														return List.of(d.toPosition(parentVersion.getEndTagCloseOffset() + 1));
													} catch (Exception ex) {
														log.error("", ex);
													}
												}
											}
										}
										return Collections.emptyList();
									}));
								}
								Generation generation = GenerationsValidator.getGenerationForJavaProject(jp, genProject, genProject.getSlug());
								if (generation != null && VersionValidationUtils.isOssValid(generation)) {

									inlayHintProviders.add(new InlayHintWithLazyPosition(() -> {
										Command command = new Command();
										command.setTitle("Add Spring Boot Starters");
										command.setCommand("spring.initializr.addStarters");
										
										InlayHintLabelPart label = new InlayHintLabelPart("Add Spring Boot Starters...");
										label.setCommand(command);
										
										InlayHint hint = new InlayHint();
										hint.setKind(InlayHintKind.Parameter);
										hint.setPaddingLeft(true);
										hint.setLabel(List.of(label));
										return hint;
									}, (d, e) -> {
										Optional<DOMElement> dependenciesOpt = findChildElement(e, 0, "dependencies");
										if (dependenciesOpt.isPresent()) {
											DOMElement dependencies = dependenciesOpt.get();
											try {
												return List.of(d.toPosition(dependencies.getStartTagCloseOffset() + 1));
											} catch (BadLocationException ex) {
												log.error("", ex);
											}
										}
										return Collections.emptyList();
									}));
								}
							}
						} catch (Exception e) {
							log.error("", e);
						}
					}
				}
				
				
			}
			
			if (!inlayHintProviders.isEmpty()) {
				String content = doc.get();
				if (!content.isEmpty()) {
					DOMParser parser = DOMParser.getInstance();
					DOMDocument dom = parser.parse(content, "", null);
						
					DOMElement project = dom.getDocumentElement();
						
					if (project != null && "project".equals(project.getTagName())) {
						return inlayHintProviders.stream().flatMap(provider -> provider.computeInlayHints(doc, project).stream()).collect(Collectors.toList());
					}
				}
			}
		}
		return Collections.emptyList();
	}
	
	private static Optional<DOMElement> findChildElement(DOMElement e, int idx, String... tagPath) {
		if (tagPath.length == idx) {
			return Optional.of(e);
		}
		for (int j = 0; j < e.getChildNodes().getLength(); j++) {
			var child = e.getChildNodes().item(j);
			if (tagPath[idx].equals(child.getNodeName()) && child instanceof DOMElement c) {
				return findChildElement(c, idx + 1, tagPath);
			}
		}
		return Optional.empty();
	}
	
	private static Optional<String> getNodeValue(DOMElement e) {
		return e.getChildren().stream().filter(DOMText.class::isInstance).map(DOMText.class::cast).map(tn -> tn.getData().trim()).findFirst();
	}
	
	private record InlayHintWithLazyPosition(Supplier<InlayHint> hintFactory, BiFunction<TextDocument, DOMElement, List<Position>> positionSupplier) {
		List<InlayHint> computeInlayHints(TextDocument d, DOMElement e) {
			return positionSupplier.apply(d, e).stream().map(p -> {
				InlayHint hint = hintFactory.get();
				hint.setPosition(p);
				return hint;
			}).collect(Collectors.toList());
		}
	}

}
