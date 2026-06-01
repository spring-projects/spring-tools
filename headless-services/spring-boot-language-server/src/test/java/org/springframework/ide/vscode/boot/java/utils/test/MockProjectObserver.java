package org.springframework.ide.vscode.boot.java.utils.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.ide.vscode.commons.languageserver.java.ProjectChangeNotifier;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;

public class MockProjectObserver implements ProjectObserver, ProjectChangeNotifier {

	List<Listener> listeners = new ArrayList<>();

	@Override
	synchronized public void addListener(Listener l) {
		listeners.add(l);
	}

	@Override
	synchronized public void removeListener(Listener l) {
		listeners.remove(l);
	}

	@Override
	public void notifyProjectsChanged() {
		// Mock implementation
	}

	public synchronized void doWithListeners(Consumer<Listener> action) {
		for (Listener l : listeners) {
			action.accept(l);
		}
	}
}