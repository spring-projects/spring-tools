package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigPropertiesIndexer;

class WebConfigPropertiesIndexerTest {

	@Test
	void testEmptyPropertiesFile() throws IOException {
		String propertyFileContent =
				"""
				""";
		
		Path path = createPropertyFile(propertyFileContent); 
		
		WebConfigPropertiesIndexer indexer = new WebConfigPropertiesIndexer();
		WebConfigIndexElement webConfig = indexer.createFromFile(path);
		
		assertTrue(webConfig.isEmpty());
	}

	@Test
	void testSimplePropertiesFileWithVersionStrategy() throws IOException {
		String propertyFileContent =
				"""
				spring.mvc.apiversion.use.path-segment=1
				""";
		
		Path path = createPropertyFile(propertyFileContent); 
		
		WebConfigPropertiesIndexer indexer = new WebConfigPropertiesIndexer();
		WebConfigIndexElement webConfig = indexer.createFromFile(path);
		
		assertFalse(webConfig.isEmpty());
		assertEquals(1, webConfig.getVersionSupportStrategies().size());
		assertEquals("Path Segment: 1", webConfig.getVersionSupportStrategies().get(0));
	}

	private Path createPropertyFile(String propertyFileContent) throws IOException {
		Path path = Files.createTempFile("application", ".properties");
		Files.writeString(path, propertyFileContent);
		
		path.toFile().deleteOnExit();
		return path;
	}

}
