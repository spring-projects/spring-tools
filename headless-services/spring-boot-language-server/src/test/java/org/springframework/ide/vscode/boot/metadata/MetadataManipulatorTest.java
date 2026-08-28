/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.metadata.MetadataManipulator;

/**
 * Tests for Spring Boot Properties Metadata manipulations
 * 
 * @author Kris De Volder
 * @author Alex Boyko
 *
 */
public class MetadataManipulatorTest {

	public static class MockContent implements MetadataManipulator.ContentStore {

		private String content;
		
		public MockContent(String intialContent) {
			this.content = intialContent;
		}

		@Override
		public String toString() {
			//So tests don't fail on Windoze
			return content.replace("\r\n", "\n");
		}

		@Override
		public String getContents() throws Exception {
			return content;
		}

		@Override
		public void setContents(String content) throws Exception {
			this.content = content;
		}
	}

    @Test
    void testAddOneElementFromEmpty() throws Exception {
        MockContent content = new MockContent("");
        MetadataManipulator md = new MetadataManipulator(content);

        md.addDefaultInfo("test.property");
        md.save();

        assertEquals(
                "{\"properties\": [{\n" +
                        "  \"name\": \"test.property\",\n" +
                        "  \"type\": \"java.lang.String\",\n" +
                        "  \"description\": \"A description for 'test.property'\"\n" +
                        "}]}",
                // == == == == == == == ==
                content.toString());

        md.addDefaultInfo("another.property");
        md.save();

        assertEquals(
                "{\"properties\": [\n" +
                        "  {\n" +
                        "    \"name\": \"test.property\",\n" +
                        "    \"type\": \"java.lang.String\",\n" +
                        "    \"description\": \"A description for 'test.property'\"\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"name\": \"another.property\",\n" +
                        "    \"type\": \"java.lang.String\",\n" +
                        "    \"description\": \"A description for 'another.property'\"\n" +
                        "  }\n" +
                        "]}",
                // == == == == == == == ==
                content.toString());

        assertTrue(md.isReliable());

    }

    @Test
    void testRawContent() throws Exception {
        MockContent content = new MockContent("garbage");
        MetadataManipulator md = new MetadataManipulator(content);

        md.addDefaultInfo("test.property");
        md.save();

        assertEquals(
                "garbage{\n" +
                        "  \"name\": \"test.property\",\n" +
                        "  \"type\": \"java.lang.String\",\n" +
                        "  \"description\": \"A description for 'test.property'\"\n" +
                        "}\n",
                // == == == == == == == ==
                content.toString());

        md.addDefaultInfo("another.property");
        md.save();

        assertEquals(
                "garbage{\n" +
                        "  \"name\": \"test.property\",\n" +
                        "  \"type\": \"java.lang.String\",\n" +
                        "  \"description\": \"A description for 'test.property'\"\n" +
                        "},\n" +
                        "{\n" +
                        "  \"name\": \"another.property\",\n" +
                        "  \"type\": \"java.lang.String\",\n" +
                        "  \"description\": \"A description for 'another.property'\"\n" +
                        "}\n",
                // == == == == == == == ==
                content.toString());

        assertFalse(md.isReliable());
    }

    @Test
    void testRawContent2() throws Exception {
        MockContent content = new MockContent(
                //almost correct content, its missing a comma
                "{\"properties\": [\n" +
                        "  {\n" +
                        "    \"name\": \"test.property\",\n" +
                        "    \"type\": \"java.lang.String\",\n" +
                        "    \"description\": \"A description for 'test.property'\"\n" +
                        "  }\n" + //missing comma!
                        "  {\n" +
                        "    \"name\": \"another.property\",\n" +
                        "    \"type\": \"java.lang.String\",\n" +
                        "    \"description\": \"A description for 'another.property'\"\n" +
                        "  }\n" +
                        "]}"
        );
        MetadataManipulator md = new MetadataManipulator(content);

        md.addDefaultInfo("foo.bar");
        md.save();

        assertEquals(
                "{\"properties\": [\n" +
                        "  {\n" +
                        "    \"name\": \"test.property\",\n" +
                        "    \"type\": \"java.lang.String\",\n" +
                        "    \"description\": \"A description for 'test.property'\"\n" +
                        "  }\n" +
                        "  {\n" +
                        "    \"name\": \"another.property\",\n" +
                        "    \"type\": \"java.lang.String\",\n" +
                        "    \"description\": \"A description for 'another.property'\"\n" +
                        "  },\n" +
                        //TODO: The indentation is off... maybe this could be fixed
                        "{\n" +
                        "  \"name\": \"foo.bar\",\n" +
                        "  \"type\": \"java.lang.String\",\n" +
                        "  \"description\": \"A description for 'foo.bar'\"\n" +
                        "}\n" +
                        "]}",
                // == == == == == == == ==
                content.toString());

        assertFalse(md.isReliable());
    }

    @Test
    void testDisallowRawContent() throws Exception {
        MockContent content;
        MetadataManipulator md;

        // empty files can be reliabley manipulated?
        content = new MockContent("");
        md = new MetadataManipulator(content);

        md.addDefaultInfo("test.property");
        md.save();

        assertEquals(
                "{\"properties\": [{\n" +
                        "  \"name\": \"test.property\",\n" +
                        "  \"type\": \"java.lang.String\",\n" +
                        "  \"description\": \"A description for 'test.property'\"\n" +
                        "}]}",
                // == == == == == == == ==
                content.toString());
    }

}
