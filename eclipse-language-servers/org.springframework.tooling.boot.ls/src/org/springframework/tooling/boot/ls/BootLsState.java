package org.springframework.tooling.boot.ls;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.core.runtime.ListenerList;

public class BootLsState {
	
	private enum State {
		INITIALIZED,
		INDEXED,
		STOPPED
	}
	
	private AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
	private ListenerList<Consumer<BootLsState>> listeners = new ListenerList<>();
	
	public boolean isIndexed() {
		return state.get() == State.INDEXED;
	}
	
	void indexed() {
		state.set(State.INDEXED);
		listeners.forEach(l -> l.accept(this));
	}
	
	void initialized() {
		state.set(State.INITIALIZED);
		listeners.forEach(l -> l.accept(this));
	}
	
	void stopped() {
		state.set(State.STOPPED);
		listeners.forEach(l -> l.accept(this));
	}
	
	public void addStateChangedListener(Consumer<BootLsState> l) {
		listeners.add(l);
	}
	
	public void removeStateChangedListener(Consumer<BootLsState> l) {
		listeners.remove(l);
	}

}
