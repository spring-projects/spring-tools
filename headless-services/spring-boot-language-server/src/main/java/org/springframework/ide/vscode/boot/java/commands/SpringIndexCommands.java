package org.springframework.ide.vscode.boot.java.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonElement;

public class SpringIndexCommands {
	
	private static final String PROJECT_BEANS_CMD = "sts/spring-boot/beans";
	
	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex metamodelIndex, JavaProjectFinder projectFinder) {
		server.onCommand(PROJECT_BEANS_CMD, params -> {
			String projectUri = ((JsonElement) params.getArguments().get(0)).getAsString();
			IJavaProject project = projectFinder.find(new TextDocumentIdentifier(projectUri)).orElse(null);
			return project == null ? CompletableFuture.completedFuture(List.of())
					: server.getAsync().execute(() -> metamodelIndex.getBeansOfProject(project.getElementName()));
		});
	}

}
