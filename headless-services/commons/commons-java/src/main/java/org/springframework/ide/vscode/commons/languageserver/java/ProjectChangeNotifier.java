package org.springframework.ide.vscode.commons.languageserver.java;

/**
 * Interface for notifying that projects have changed.
 */
public interface ProjectChangeNotifier {

	void notifyProjectsChanged();

}
