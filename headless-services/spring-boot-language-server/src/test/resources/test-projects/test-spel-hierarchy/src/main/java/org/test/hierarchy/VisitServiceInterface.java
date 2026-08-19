package org.test.hierarchy;

public interface VisitServiceInterface {

	String interfaceMethod();

	default String defaultInterfaceMethod() {
		return "default";
	}

}
