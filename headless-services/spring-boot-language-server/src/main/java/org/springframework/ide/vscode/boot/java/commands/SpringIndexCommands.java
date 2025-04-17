package org.springframework.ide.vscode.boot.java.commands;

import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

public class SpringIndexCommands {
	
	private static final String SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
	
	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex metamodelIndex, JavaProjectFinder projectFinder) {
		server.onCommand(SPRING_STRUCTURE_CMD, params -> server.getAsync().invoke(() -> metamodelIndex.getProjects()));
	}

}
