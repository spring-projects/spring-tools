/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.completion;

import org.springframework.ide.vscode.commons.util.Streams;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.util.YamlIndentUtil;

/**
 * Helper for building the auto-insertion text that is appended after a
 * property completion, based on the {@link YType} that is expected for the
 * property value.
 */
public class AppendTextBuilder {

	private YTypeUtil typeUtil;

	public AppendTextBuilder(YTypeUtil typeUtil) {
		this.typeUtil = typeUtil;
	}

	public String buildFor(YType type, YamlDocument doc) {
		//Note that caller is responsible for proper indentation
		//to align with the parent. The strings created here only need to contain
		//indentation spaces to indent *more* than the parent node.

		//Nevertheless, to support building sophisticated appendText recursively,
		//we do need to keep track of the relative indentation level internally
		//(or potentially do a lot of string copying)

		StringBuilder text = new StringBuilder();
		build(type, 0, text, doc);
		return text.toString();
	}

	private void build(YType type, int indent, StringBuilder text, YamlDocument doc) {
		if (type == null) {
			//Assume its some kind of pojo bean
			newline(text, indent+YamlIndentUtil.INDENT_BY, doc);
		} else if (typeUtil.isMap(type)) {
			//ready to enter nested map key on next line
			newline(text, indent+YamlIndentUtil.INDENT_BY, doc);
		} else if (typeUtil.isSequencable(type)) {
			//ready to enter sequence element on next line
			newline(text, indent, doc);
			text.append("- ");
			singleMostImportantProperty(typeUtil.getDomainType(type), indent+2, text, doc);
			//Yes using 2 here instead of YamlIndentUtil.INDENT_BY is deliberate. It's the same value (now),
			// but the 2 used here is the width of the "- " which should determine nested indent level for things to
			// line up properly.
		} else if (typeUtil.isAtomic(type)) {
			//ready to enter whatever on the same line
			text.append(" ");
		} else {
			newline(text, indent+YamlIndentUtil.INDENT_BY, doc);
		}
	}

	private void singleMostImportantProperty(YType type, int indent, StringBuilder text, YamlDocument doc) {
		if (type != null) {
			YTypedProperty singleProp = Streams.getSingle(typeUtil.getProperties(type).stream()
					.filter(p -> p.isPrimary()));
			if (singleProp == null) {
				singleProp = Streams.getSingle(typeUtil.getProperties(type).stream()
					.filter(p -> p.isRequired()));
			}
			if (singleProp != null) {
				text.append(singleProp.getName());
				text.append(':');
				build(singleProp.getType(), indent+YamlIndentUtil.INDENT_BY, text, doc);
			}
		}
	}

	private void newline(StringBuilder text, int indent, YamlDocument doc) {
		text.append(doc == null || doc.getDocument() == null ? System.lineSeparator() : doc.getDocument().getDefaultLineDelimiter());
		for (int i = 0; i < indent; i++) {
			text.append(' ');
		}
	}

}
