package org.test.versions;

import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.accept.SemanticApiVersionParser.Version;

public class TestApiVersionParser implements ApiVersionParser<Version> {

	@Override
	public Version parseVersion(String version) {
		return null;
	}

}
