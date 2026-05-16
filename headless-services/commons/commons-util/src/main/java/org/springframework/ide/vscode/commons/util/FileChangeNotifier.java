package org.springframework.ide.vscode.commons.util;

/**
 * Interface for notifying about file system changes.
 * 
 * @author Alex Boyko
 */
public interface FileChangeNotifier {

	void notifyFileCreated(String uri);
	
	void notifyFileChanged(String uri);
	
	void notifyFileDeleted(String uri);

}
