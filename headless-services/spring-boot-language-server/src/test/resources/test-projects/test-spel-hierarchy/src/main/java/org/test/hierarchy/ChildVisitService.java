package org.test.hierarchy;

import org.springframework.stereotype.Service;

@Service
public class ChildVisitService extends BaseVisitService {

	public String childMethod() {
		return "child";
	}

	@Override
	public String overriddenMethod() {
		return "child";
	}

	@Override
	public String interfaceMethod() {
		return "child";
	}

}
