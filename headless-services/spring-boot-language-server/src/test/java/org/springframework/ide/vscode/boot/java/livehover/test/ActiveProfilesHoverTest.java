/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.HoverTestConf;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveDataProvider;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.ide.vscode.project.harness.SpringProcessLiveDataBuilder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(HoverTestConf.class)
public class ActiveProfilesHoverTest {

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringProcessLiveDataProvider liveDataProvider;

	@BeforeEach
	public void setup() throws Exception {
		harness.useProject(projects.mavenProject("empty-boot-15-web-app"));
		harness.intialize(null);
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		liveDataProvider.remove("processkey");
		liveDataProvider.remove("processkey1");
		liveDataProvider.remove("processkey2");
	}

    @Test
    void testActiveProfileHover() throws Exception {
        SpringProcessLiveData liveData = new SpringProcessLiveDataBuilder()
                .processID("22022")
                .processName("foo.bar.RunningApp")
                .activeProfiles("testing-profile", "local-profile")
                .build();
        liveDataProvider.add("processkey", liveData);

        Editor editor = harness.newEditor(LanguageId.JAVA,
                "package hello;\n" +
                        "\n" +
                        "import org.springframework.context.annotation.Configuration;\n" +
                        "import org.springframework.context.annotation.Profile;\n" +
                        "\n" +
                        "@Configuration\n" +
                        "@Profile({\"local-profile\", \"inactive\", \"testing-profile\"})\n" +
                        "public class LocalConfig {\n" +
                        "}"
        );

        String[] hoverSites = {
                "@Profile", "local-profile", "testing-profile"
        };
        editor.assertHighlights(
                hoverSites
        );
        for (String hoverOver : hoverSites) {
            editor.assertHoverContains(hoverOver, "testing-profile");
            editor.assertHoverContains(hoverOver, "local-profile");
            editor.assertHoverContains(hoverOver, "foo.bar.RunningApp");
            editor.assertHoverContains(hoverOver, "22022");
        }
    }

    @Test
    void testActiveProfileHoverWithConcatenatedString() throws Exception {
        SpringProcessLiveData liveData = new SpringProcessLiveDataBuilder()
                .processID("22022")
                .processName("foo.bar.RunningApp")
                .activeProfiles("testing-profile", "local-profile")
                .build();
        liveDataProvider.add("processkey", liveData);

        Editor editor = harness.newEditor(LanguageId.JAVA,
                "package hello;\n" +
                        "\n" +
                        "import org.springframework.context.annotation.Configuration;\n" +
                        "import org.springframework.context.annotation.Profile;\n" +
                        "\n" +
                        "@Configuration\n" +
                        "@Profile({\"local\" + \"-profile\", \"inactive\", \"testing\" + \"-profile\"})\n" +
                        "public class LocalConfig {\n" +
                        "}"
        );

        // a concatenated value has no single string literal, so the highlight covers the whole expression
        String[] hoverSites = {
                "@Profile", "\"local\" + \"-profile\"", "\"testing\" + \"-profile\""
        };
        editor.assertHighlights(
                hoverSites
        );
        for (String hoverOver : hoverSites) {
            editor.assertHoverContains(hoverOver, "testing-profile");
            editor.assertHoverContains(hoverOver, "local-profile");
            editor.assertHoverContains(hoverOver, "foo.bar.RunningApp");
            editor.assertHoverContains(hoverOver, "22022");
        }
    }

    @Test
    void testActiveProfileHover_Unknown() throws Exception {
        //Sometimes its not possible to determine active profiles for an app (e.g. no actuator dependency).
        //Make sure we show something sensible
        harness.useProject(projects.mavenProject("no-actuator-boot-15-web-app"));

        SpringProcessLiveData liveData = new SpringProcessLiveDataBuilder()
                .processID("22022")
                .processName("foo.bar.RunningApp")
                .activeProfiles((String[]) null)
                .build();
        liveDataProvider.add("processkey", liveData);

        Editor editor = harness.newEditor(LanguageId.JAVA,
                "package hello;\n" +
                        "\n" +
                        "import org.springframework.context.annotation.Configuration;\n" +
                        "import org.springframework.context.annotation.Profile;\n" +
                        "\n" +
                        "@Configuration\n" +
                        "@Profile(\"local\")\n" +
                        "public class LocalConfig {\n" +
                        "\n" +
                        "}"
        );
        editor.assertHighlights(/*NONE*/);
        editor.assertHoverContains("@Profile", "Consider adding `spring-boot-actuator` as a dependency");
    }

    @Test
    void testActiveProfileHoverMixedKnownAndUnknown() throws Exception {
        SpringProcessLiveData liveData1 = new SpringProcessLiveDataBuilder()
                .processID("22022")
                .processName("foo.bar.NoActuatorApp")
                .activeProfiles((String[]) null)
                .build();
        liveDataProvider.add("processkey1", liveData1);

        SpringProcessLiveData liveData2 = new SpringProcessLiveDataBuilder()
                .processID("3456")
                .processName("foo.bar.NormalApp")
                .activeProfiles("fancy")
                .build();
        liveDataProvider.add("processkey2", liveData2);

        Editor editor = harness.newEditor(LanguageId.JAVA,
                "package hello;\n" +
                        "\n" +
                        "import org.springframework.context.annotation.Configuration;\n" +
                        "import org.springframework.context.annotation.Profile;\n" +
                        "\n" +
                        "@Configuration\n" +
                        "@Profile({\"unknown\", \"inactive\", \"fancy\"})\n" +
                        "public class LocalConfig {\n" +
                        "\n" +
                        "}"
        );

        editor.assertHighlights("@Profile", "fancy");
        editor.assertHoverContains("@Profile", "Unknown");
        editor.assertHoverContains("@Profile", "fancy");
    }

    @Test
    void testNoRunningApps() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA,
                "package hello;\n" +
                        "\n" +
                        "import org.springframework.context.annotation.Configuration;\n" +
                        "import org.springframework.context.annotation.Profile;\n" +
                        "\n" +
                        "@Configuration\n" +
                        "@Profile(\"local\")\n" +
                        "public class LocalConfig {\n" +
                        "\n" +
                        "}"
        );
        editor.assertHighlights(/*NONE*/);
        editor.assertNoHover("@Profile");
    }
}
