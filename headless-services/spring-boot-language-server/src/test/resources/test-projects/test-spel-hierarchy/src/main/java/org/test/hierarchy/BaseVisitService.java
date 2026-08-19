package org.test.hierarchy;

public abstract class BaseVisitService implements VisitServiceInterface {

	public String baseMethod() {
		return "base";
	}

	public String overriddenMethod() {
		return "base";
	}

	public static String staticBaseMethod() {
		return "static base";
	}

}
