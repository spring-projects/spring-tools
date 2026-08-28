/*******************************************************************************
 * Copyright (c) 2014, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.completions;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.ICompletionProposalSorter;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.hover.HoverInfo;
import org.springframework.ide.eclipse.editor.support.hover.HoverInformationControlCreator;
import org.springframework.ide.eclipse.editor.support.hover.YPropertyHoverInfo;
import org.springframework.ide.eclipse.editor.support.util.ColorManager;
import org.springframework.ide.eclipse.editor.support.yaml.completions.AbstractPropertyProposal;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YType;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypeUtil;
import org.springframework.ide.eclipse.editor.support.yaml.schema.YTypedProperty;

/**
 * @author Kris De Volder
 */
public class CompletionFactory {

	public static final Styler HIGHLIGHT = new Styler() {
		public void applyStyles(TextStyle textStyle) {
			textStyle.foreground = ColorManager.getInstance().getColor(ColorManager.CYAN);
		};
	};

	public static final Styler DEPRECATE = new Styler() {
		public void applyStyles(TextStyle textStyle) {
			textStyle.strikeout = true;
		};
	};

	public static final Styler DEEMPHASIZE = new Styler() {
		public void applyStyles(TextStyle textStyle) {
			textStyle.foreground = ColorManager.getInstance().getColor(ColorManager.GREY);
		};
	};

	public static final Styler NULL_STYLER = new Styler() {
		public void applyStyles(TextStyle textStyle) {
		};
	};

	public static Styler compose(final Styler s1, final Styler s2) {
		if (s1 == NULL_STYLER) {
			return s2;
		} else if (s2 == NULL_STYLER) {
			return s1;
		} else {
			return new Styler() {
				@Override
				public void applyStyles(TextStyle textStyle) {
					s1.applyStyles(textStyle);
					s2.applyStyles(textStyle);
				}
			};
		}
	}

	public static final CompletionFactory DEFAULT = new CompletionFactory();

	public ScoreableProposal simpleProposal(String name, String pattern, int sortingOrder, ProposalApplier applier, HoverInfo info) {
		return simpleProposal(name, pattern, -(1.0+sortingOrder), applier, info);
	}

	public ScoreableProposal simpleProposal(String name, String pattern, double score, ProposalApplier applier, HoverInfo info) {
		return new SimpleProposal(name, pattern, score, applier, info);
	}

	public ScoreableProposal simpleProposal(String name, String pattern, String label, double score, ProposalApplier applier, HoverInfo info) {
		return new SimpleProposal(name, pattern, label, score, applier, info);
	}

	public static abstract class ScoreableProposal implements ICompletionProposal, ICompletionProposalExtension3, ICompletionProposalExtension4, ICompletionProposalExtension5, ICompletionProposalExtension6 {
		private static final double DEEMP_VALUE = 100000; // should be large enough to move deemphasized stuff to bottom of list.

		private double deemphasizedBy = 0.0;
		public abstract double getBaseScore();
		public final double getScore() {
			return getBaseScore() - deemphasizedBy;
		}
		public ScoreableProposal deemphasize() {
			deemphasizedBy+= DEEMP_VALUE;
			return this;
		}
		public boolean isDeemphasized() {
			return deemphasizedBy > 0;
		}

		@Override
		public boolean isAutoInsertable() {
			return !isDeemphasized();
		}

		public StyledString getStyledDisplayString() {
			StyledString result = new StyledString();
			highlightPattern(getHighlightPattern(), getBaseDisplayString(), result);
			return result;
		}

		private void highlightPattern(String pattern, String data, StyledString result) {
			Styler highlightStyle = CompletionFactory.HIGHLIGHT;
			Styler plainStyle = isDeemphasized() ? CompletionFactory.DEEMPHASIZE : CompletionFactory.NULL_STYLER;
			if (isDeprecated()) {
				highlightStyle = CompletionFactory.compose(highlightStyle, CompletionFactory.DEPRECATE);
				plainStyle = CompletionFactory.compose(plainStyle, CompletionFactory.DEPRECATE);
			}
			if (!StringUtils.isBlank(pattern)) {
				int dataPos = 0;	int dataLen = data.length();
				int patternPos = 0; int patternLen = pattern.length();

				while (dataPos < dataLen && patternPos < patternLen) {
					int pChar = pattern.charAt(patternPos++);
					int highlightPos = data.indexOf(pChar, dataPos);
					if (dataPos < highlightPos) {
						result.append(data.substring(dataPos, highlightPos), plainStyle);
					}
					result.append(data.charAt(highlightPos), highlightStyle);
					dataPos = highlightPos+1;
				}
				if (dataPos < dataLen) {
					result.append(data.substring(dataPos), plainStyle);
				}
			} else { //no pattern to highlight
				result.append(data, plainStyle);
			}
		}

		protected abstract boolean isDeprecated();
		protected abstract String getHighlightPattern();
		protected abstract String getBaseDisplayString();
		@Override
		public IInformationControlCreator getInformationControlCreator() {
			return new HoverInformationControlCreator("F2 for focus");
		}
		@Override
		public String getAdditionalProposalInfo() {
			HoverInfo hoverInfo = getAdditionalProposalInfo(new NullProgressMonitor());
			if (hoverInfo != null) {
				return hoverInfo.getHtml();
			}
			return null;
		}
		@Override
		public abstract HoverInfo getAdditionalProposalInfo(IProgressMonitor monitor);

		@Override
		public CharSequence getPrefixCompletionText(IDocument document, int completionOffset) {
			return null;
		}

		@Override
		public int getPrefixCompletionStart(IDocument document, int completionOffset) {
			return completionOffset;
		}

	}

	private static class SimpleProposal extends ScoreableProposal {

		private String value;
		private String label;
		private ProposalApplier applier;
		private double score;
		private String pattern;
		private HoverInfo hoverInfo;

		public SimpleProposal(String value, String pattern, double score, ProposalApplier applier, HoverInfo info) {
			this(value, pattern, value, score, applier, info);
		}

		public SimpleProposal(String value, String pattern, String label, double score, ProposalApplier applier, HoverInfo info) {
			this.score = score;
			this.value = value;
			this.applier = applier;
			this.pattern = pattern;
			this.hoverInfo = info;
			this.label = label;
		}

		@Override
		public void apply(IDocument doc) {
			try {
				applier.apply(doc);
			} catch (Exception e) {
				EditorSupportActivator.log(e);
			}
		}

		@Override
		public Point getSelection(IDocument doc) {
			try {
				return applier.getSelection(doc);
			} catch (Exception e) {
				EditorSupportActivator.log(e);
			}
			return null;
		}

		@Override
		public HoverInfo getAdditionalProposalInfo(IProgressMonitor monitor) {
			return hoverInfo;
		}

		@Override
		public String getDisplayString() {
			return label.toString();
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}

		@Override
		public double getBaseScore() {
			return score;
		}

		@Override
		protected boolean isDeprecated() {
			return false;
		}

		@Override
		protected String getHighlightPattern() {
			return pattern;
		}

		@Override
		protected String getBaseDisplayString() {
			return label.toString();
		}
	}

	/**
	 * A sorter suitable for sorting proposals created by this factory
	 */
	public static final ICompletionProposalSorter SORTER = new ICompletionProposalSorter() {
		public int compare(ICompletionProposal p1, ICompletionProposal p2) {
			if (p1 instanceof ScoreableProposal && p2 instanceof ScoreableProposal) {
				double s1 = ((ScoreableProposal)p1).getScore();
				double s2 = ((ScoreableProposal)p2).getScore();
				if (s1 == s2) {
					String name1 = ((ScoreableProposal)p1).getDisplayString();
					String name2 = ((ScoreableProposal)p2).getDisplayString();
					return name1.compareTo(name2);
				} else {
					return Double.compare(s2, s1);
				}
			}
			return 0;
		}
	};

	public ScoreableProposal beanProperty(IDocument doc, final String contextProperty, final YType contextType, final String pattern, final YTypedProperty p, final double score, ProposalApplier applier, final YTypeUtil typeUtil) {
		return new AbstractPropertyProposal(doc, applier) {

			@Override
			public double getBaseScore() {
				return score;
			}

			@Override
			protected String niceTypeName(YType type) {
				return typeUtil.niceTypeName(type);
			}

			@Override
			protected YType getType() {
				return p.getType();
			}

			@Override
			protected String getHighlightPattern() {
				return pattern;
			}

			@Override
			protected String getBaseDisplayString() {
				return p.getName();
			}

			@Override
			public HoverInfo getAdditionalProposalInfo(IProgressMonitor monitor) {
				return new YPropertyHoverInfo(contextProperty, contextType, p);
			}
		};
	}

	public ScoreableProposal valueProposal(String value, String pattern, YType yType, double score, ProposalApplier applier, HoverInfo info) {
		return simpleProposal(value, pattern, score, applier, info);
	}

	public ScoreableProposal valueProposal(String value, String pattern, String label, YType yType, double score, ProposalApplier applier, HoverInfo info) {
		return simpleProposal(value, pattern, label, score, applier, info);
	}


	public ScoreableProposal valueProposal(String value, String pattern, YType type, int order, ProposalApplier applier, HoverInfo info) {
		return valueProposal(value, pattern, type, -(1.0+order), applier, info);
	}

}
