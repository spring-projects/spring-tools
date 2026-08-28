package org.springframework.ide.eclipse.boot.dash.api;

import org.springframework.ide.eclipse.boot.dash.model.ClasspathPropertyTester;

public interface DevtoolsConnectable extends ClasspathBearing {
	String getDevtoolsSecret();

	default TemporalBoolean isDevtoolsConnectable() {
		return TemporalBoolean.now(hasClasspathProperty(ClasspathPropertyTester.HAS_DEVTOOLS) && getDevtoolsSecret() != null);
	}
}
