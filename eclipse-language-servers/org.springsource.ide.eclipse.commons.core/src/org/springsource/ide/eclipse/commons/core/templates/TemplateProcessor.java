/*******************************************************************************
 * Copyright (c) 2012, 2021 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.templates;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.springsource.ide.eclipse.commons.core.FileUtil;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;

/**
 * @author Terry Denney
 * @author Leo Dos Santos
 * @author Christian Dupuis
 * @author Kaitlin Duck Sherwood
 * @author Kris De Volder (Add recursion over directories).
 */
public class TemplateProcessor {

	protected Map<String, String> replacementContext = new HashMap<String, String>();

//	protected SpringVersionProcessor springProcessor;

	public TemplateProcessor(Map<String, String> replacementContext) {
		this.replacementContext = replacementContext;
	}

//	public TemplateProcessor(Map<String, String> replacementContext, SpringVersion springVersion) {
//		this.replacementContext = replacementContext;
//
//		if (springVersion != null && !springVersion.equals(SpringVersion.DEFAULT)) {
//			this.springProcessor = new SpringVersionProcessor(springVersion.getVersion());
//		}
//	}

	public void process(File source, File target) throws IOException {
		if (FileUtil.isBinaryFile(source)) {
			FileUtil.copy(source, target);
		} else if (source.isDirectory()) {
			try {
				FileUtil.copyDirectory(source, target, new NullProgressMonitor());
			}
			catch (CoreException e) {
				CorePlugin.log("Problem while attempting to copy template directory", e);
			}
			String[] names = source.list();
			if (names != null) {
				for (String childName : names) {
					File childSource = new File(source, childName);
					File childTarget = new File(target, childName);
					process(childSource, childTarget);
				}
			}
		}
		else {
			process(new FileReader(source), new FileWriter(target));
		}
	}

	public void process(Reader reader, Writer writer) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(reader);
		BufferedWriter bufferedWriter = new BufferedWriter(writer);

		while (true) {
			String line = bufferedReader.readLine();
			if (line == null) {
				break;
			}
			line = replaceTokens(line);
			bufferedWriter.write(line);
			bufferedWriter.write('\n');
		}
		bufferedWriter.flush();
	}

	public String replaceTokens(String input) {
		String topLevelPackageStr = replacementContext.get("||top-level-package||");
		if (topLevelPackageStr != null) {
			if (topLevelPackageStr.startsWith("&&")) {
				topLevelPackageStr = topLevelPackageStr.substring(2);
			}
			topLevelPackageStr = topLevelPackageStr.replaceAll("&&", ".");
			String userTopLevelPackageStr = replacementContext.get("||user-top-level-package||");
			if (userTopLevelPackageStr.startsWith("&&")) {
				userTopLevelPackageStr = userTopLevelPackageStr.substring(2);
			}

			if (input.contains(topLevelPackageStr) && userTopLevelPackageStr != null) {
				int index = input.indexOf(topLevelPackageStr);
				String newInput = input.substring(0, index) + userTopLevelPackageStr.replaceAll("&&", ".");

				if (index + topLevelPackageStr.length() < input.length()) {
					newInput += input.substring(index + topLevelPackageStr.length());
				}

				input = newInput;
			}
		}

		for (String token : replacementContext.keySet()) {
			String replacement = replacementContext.get(token);
			input = input.replace(token, replacement);
		}

		if (input.indexOf("my") >= 0) {
			CorePlugin.log("May have failed to replace token " + input, null);
		}
		return input;
	}

	public String replacePathForDirectory(String path, char separator) {
		String originalTopLevelPackageStr = replacementContext.get("||top-level-package||");
		if (originalTopLevelPackageStr != null) {
			originalTopLevelPackageStr = originalTopLevelPackageStr.replaceAll("&&", "\\" + separator);

			String userTopLevelPackageStr = replacementContext.get("||user-top-level-package||");

			if (path.contains(originalTopLevelPackageStr) && userTopLevelPackageStr != null) {
				int index = path.indexOf(originalTopLevelPackageStr);
				String replacementString = userTopLevelPackageStr.replaceAll("&&", "\\" + separator);
				String newPath = path.substring(0, index) + replacementString;

				if (index + 1 + originalTopLevelPackageStr.length() < path.length()) {
					newPath += path.substring(index + originalTopLevelPackageStr.length());
				}

				return newPath;
			}
		}

		return null;
	}
}
