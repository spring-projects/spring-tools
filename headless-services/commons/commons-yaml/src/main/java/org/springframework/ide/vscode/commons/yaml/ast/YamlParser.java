/*******************************************************************************
 * Copyright (c) 2016-2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.ast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.representer.Representer;

import com.google.common.collect.ImmutableList;

import javolution.io.CharSequenceReader;

public class YamlParser implements YamlASTProvider {

	public YamlParser() {
	}

	@Override
	public YamlFileAST getAST(IDocument doc) throws Exception {
		CharSequenceReader reader = new CharSequenceReader();
		reader.setInput(atTokenTransformHack(doc.get()));
		LoaderOptions loaderOpts = new LoaderOptions();
		loaderOpts.setProcessComments(true);
		loaderOpts.setMaxAliasesForCollections(1000);
		Iterable<Node> nodes = new Yaml(new SafeConstructor(loaderOpts), new Representer(new DumperOptions()), new DumperOptions(), loaderOpts).composeAll(reader);
		return new YamlFileAST(doc, ImmutableList.copyOf(nodes));
	}

	Pattern AT_TOKEN = Pattern.compile("^.*?(\\@[a-zA-z0-9_\\-\\.]*\\@).*?$", Pattern.MULTILINE);
	
	static class StringCopier {
		private int offset = 0;
		private String input;
		private StringBuilder output;

		public StringCopier(String input) {
			this.input = input;
			this.output = new StringBuilder();
		}

		public void copyUpto(int upto) {
			if (upto>offset) {
				output.append(input.substring(offset, upto));
				offset = upto;
			}
		}

		public void replace(int len) {
			if (len>=3) {
				offset+=len;
				len-=2;
				output.append('"');
				for (int i = 0; i < len; i++) {
					output.append('@');
				}
				output.append('"');
			} else {
				copyUpto(offset+len);
			}
		}
	}
	
	private CharSequence atTokenTransformHack(String input) {
		Matcher matcher = AT_TOKEN.matcher(input);
		StringCopier transformed = new StringCopier(input);
		while (matcher.find()) {
			int lineStart = matcher.start();
			
			int tokenStart = matcher.start(1);
			int tokenEnd = matcher.end(1);
			
			String pre = input.substring(lineStart, tokenStart);
			if (!pre.contains("\"")) {
				transformed.copyUpto(tokenStart);
				transformed.replace(tokenEnd-tokenStart);
			}
		}
		transformed.copyUpto(input.length());
		String out = transformed.output.toString();
		Assert.isTrue(out.length()==input.length());
		return out;
	}

}
