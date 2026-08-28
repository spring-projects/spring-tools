/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.completion;

import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.hover.YPropertyInfoTemplates;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;

public class DefaultCompletionFactory implements CompletionFactory {

	private static final int ERROR_COMPLETION_SCORE = -10000000;

	public static class BeanPropertyProposal extends AbstractScoreableProposal {

//		private IDocument doc;
		private String contextProperty;
		private YType contextType;
//		private String query;
		private YTypedProperty p;
		private double baseScore;
		private DocumentEdits edits;
		private YTypeUtil typeUtil;

		public BeanPropertyProposal(IDocument doc, String contextProperty, YType contextType, String query, YTypedProperty p, double score, DocumentEdits edits, YTypeUtil typeUtil) {
			super();
	//		this.doc = doc;
			this.contextProperty = contextProperty;
			this.contextType = contextType;
	//		this.query = query;
			this.p = p;
			this.baseScore = score;
			this.edits = edits;
			this.typeUtil = typeUtil;
		}

		@Override
		public double getBaseScore() {
			return baseScore;
		}

		@Override
		public String getLabel() {
			return p.getName();
		}

		@Override
		public CompletionItemKind getKind() {
			return CompletionItemKind.Field;
		}

		@Override
		public DocumentEdits getTextEdit() {
			return edits;
		}

		@Override
		public String getDetail() {
			return typeUtil.niceTypeName(p.getType());
		}

		@Override
		public Renderable getDocumentation() {
			return YPropertyInfoTemplates.createCompletionDocumentation(contextProperty, contextType, p);
		}
	}

	public class ValueProposal extends AbstractScoreableProposal {

		private String value;
		private String label;
		private YType type;
		private double baseScore;
		private DocumentEdits edits;
		private YTypeUtil typeUtil;
		private Renderable docs;

		public ValueProposal(String value, String query, String label, YType type, Renderable docs, double score, DocumentEdits edits, YTypeUtil typeUtil) {
			this.value = value;
			this.label = label;
			this.type = type;
			this.docs = docs;
			this.baseScore = score;
			this.edits = edits;
			this.typeUtil = typeUtil;
		}

		@Override
		public double getBaseScore() {
			return baseScore;
		}

		@Override
		public String getLabel() {
			return label;
		}

		@Override
		public CompletionItemKind getKind() {
			return CompletionItemKind.Value;
		}

		@Override
		public DocumentEdits getTextEdit() {
			return edits;
		}

		@Override
		public String toString() {
			return "ValueProposal("+value+")";
		}

		@Override
		public String getDetail() {
			return typeUtil.niceTypeName(type);
		}

		@Override
		public Renderable getDocumentation() {
			return docs;
		}
	}

	public static final class ErrorProposal extends AbstractScoreableProposal {
		private final String longMessage;
		private String shortMessage;
		private String filterText;

		public ErrorProposal(String query, String longMessage) {
			this.filterText = query;
			int split = longMessage.indexOf(": ");
			if (split > 0) {
				this.shortMessage = longMessage.substring(0, split);
				this.longMessage = longMessage.substring(split+2);
			} else {
				this.longMessage = longMessage;
				this.shortMessage = longMessage;
			}
		}

		@Override
		public DocumentEdits getTextEdit() {
			return new DocumentEdits(null, false);
		}

		@Override
		public String getLabel() {
			return shortMessage;
		}

		@Override
		public CompletionItemKind getKind() {
			return CompletionItemKind.Text;
		}

		@Override
		public Renderable getDocumentation() {
			return Renderables.text(longMessage);
		}

		@Override
		public double getBaseScore() {
			return ERROR_COMPLETION_SCORE;
		}

		@Override
		public String toString() {
			return "ErrorProposal()";
		}

		@Override
		public String getDetail() {
			return "Error";
		}

		@Override
		public String getFilterText() {
			return filterText;
		}
	}

	@Override
	public ICompletionProposal beanProperty(IDocument doc, String contextProperty, YType contextType, String query, YTypedProperty p, double score, DocumentEdits edits, YTypeUtil typeUtil) {
		return new BeanPropertyProposal(doc, contextProperty, contextType, query, p, score, edits, typeUtil);
	}

	@Override
	public ICompletionProposal valueProposal(
			String value, String query, String label, YType type, Renderable doc,
			double score, DocumentEdits edits, YTypeUtil typeUtil
	) {
		return new ValueProposal(value, query, label, type, doc, score, edits, typeUtil);
	}

	@Override
	public ICompletionProposal errorMessage(String query, String longMessage) {
		return new ErrorProposal(query, longMessage);
	}
}
