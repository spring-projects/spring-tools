package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;

public class WebConfigIndexElementTest {

	@Test
	void parserDefault() {
		WebConfigIndexElement webConfigElement = new WebConfigIndexElement(ConfigType.WEB_CONFIG, "path", List.of(), List.of(), null, null);
		assertEquals(WebConfigIndexElement.DEFAULT_VERSION_PARSER, webConfigElement.getVersionParser());
	}

}
