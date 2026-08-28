/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.overzealous.remark.Remark;

/**
 * Static methods and convenience constants for creating some 'description
 * providers'.
 *
 * @author Kris De Volder
 */
public class Renderables {

	private static final String NO_DESCRIPTION_TEXT = "no description";

	final static Logger logger = LoggerFactory.getLogger(Renderables.class);

	public static final Renderable NO_DESCRIPTION = italic(text(NO_DESCRIPTION_TEXT));
	
	public static Remark getHtmlToMarkdownConverter() {
		return new Remark();
	}
	
	@FunctionalInterface
	public interface HtmlContentFiller {
		void fill(HtmlBuffer buffer);
	}
	
	public static Renderable htmlBlob(HtmlContentFiller contentFiller) {
		return new Renderable() {

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				contentFiller.fill(buffer);
			}

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append(getHtmlToMarkdownConverter().convert(toHtml()));
			}
			
		};
	}

	public static Renderable htmlBlob(String html) {
		return htmlBlob(buffer -> buffer.raw(html));
	}
	
	public static Renderable mdBlob(String md) {
		return new Renderable() {
			
			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append(md);
			}
			
			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				throw new UnsupportedOperationException("Not implemented");
			}
		};
	}
	
	public static Renderable concat(Renderable... pieces) {
		return concat(ImmutableList.copyOf(pieces));
	}

	public static Renderable concat(List<Renderable> pieces) {
		if (pieces == null || pieces.size() == 0) {
			throw new IllegalArgumentException("At least one hover information is required for concat");
		} else if (pieces.size() == 1) {
			return pieces.get(0);
		} else {
			return new ConcatRenderables(pieces);
		}
	}

	public static Renderable italic(Renderable text) {
		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append("*");
				text.renderAsMarkdown(buffer);
				buffer.append("*");
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<i>");
				text.renderAsHtml(buffer);
				buffer.raw("</i>");
			}
		};
	}
	
	/**
	 * A single inline code span wrapping a leaf string (code span content is never
	 * re-parsed as markdown, so there's nothing to gain by accepting a nested
	 * {@link Renderable} here). Renders as an escaped markdown code span (a backtick
	 * fence long enough that embedded backticks in {@code content} can't terminate it
	 * early, padded with a space where CommonMark requires it), or as an HTML
	 * {@code <code>} element with HTML-entity escaping.
	 */
	public static Renderable inlineSnippet(String content) {
		String safeContent = content == null ? "null" : content;
		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append(codeSpanMarkdown(safeContent));
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<code>");
				buffer.text(safeContent);
				buffer.raw("</code>");
			}
		};
	}
	
	public static Renderable inlineMultiLineSnippet(Renderable text) {
		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append("```\n");
				text.renderAsMarkdown(buffer);
				buffer.append("\n```");
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<pre>");
				text.renderAsHtml(buffer);
				buffer.raw("</pre>");
			}
		};
	}
	
	public static Renderable paragraph(Renderable text) {
		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				//TODO: This looks wrong. A paragraphs in markdown are created
				// by separating the text between them with TWO newlines. So this isn't
				// quite rigth as it provides no guarantees that there will be 
				// two newlines before or after the paragraph's text.
				// The correct implementation should probably check wether text in buffer already
				// ends with newline(s) and add more only if needed. Then it should 
				// also append double newline at its end.
				
				//TODO: verify that the if below solves the above "todo"
				if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != '\n') {
					// 2 spaces and then new line would create a line break in text
					buffer.append("  ");
				}
				buffer.append("\n");
				text.renderAsMarkdown(buffer);
				buffer.append("\n");
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<p>");
				text.renderAsHtml(buffer);
				buffer.raw("</p>");
			}
		};
	}
	
	public static Renderable strikeThrough(Renderable text) {
		return new Renderable() {
			
			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append("~~");
				text.renderAsMarkdown(buffer);
				buffer.append("~~");
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<del>");
				text.renderAsHtml(buffer);
				buffer.raw("</del>");
			}
		};
	}

	public static Renderable link(String text, String url) {
		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append('[');
				// Escape characters that would otherwise prematurely close the link text or
				// be misread as markdown syntax (e.g. a bean id/type coming from a live process).
				buffer.append(text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]"));
				buffer.append(']');
				if (url != null) {
					buffer.append('(');
					// Escape parenthesis for the MD format
					buffer.append(url.replace("(", "%28").replace(")", "%29"));
					buffer.append(')');
				}
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<a href=\"");
				buffer.url("" + url);
				buffer.raw("\">");
				buffer.text(text);
				buffer.raw("</a>");
			}
		};
	}

	/**
	 * Wraps {@code content} in a markdown inline code span, choosing a backtick
	 * fence long enough that embedded backticks in {@code content} can't
	 * terminate the span early, and padding with a space on each side where
	 * CommonMark requires it (content that starts/ends with a backtick, or
	 * that starts and ends with a space but isn't all spaces) so the parser's
	 * own space-stripping rule doesn't alter {@code content}.
	 */
	private static String codeSpanMarkdown(String content) {
		if (content.isEmpty()) {
			// An empty code span can't be represented in markdown (e.g. "``" is not
			// parsed as one); omitting it entirely is the safest fallback.
			return "";
		}
		int maxRun = 0;
		int run = 0;
		boolean allSpaces = true;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (c == '`') {
				run++;
				maxRun = Math.max(maxRun, run);
			} else {
				run = 0;
			}
			if (c != ' ') {
				allSpaces = false;
			}
		}
		String fence = "`".repeat(maxRun + 1);
		boolean pad = content.startsWith("`") || content.endsWith("`")
				|| (content.startsWith(" ") && content.endsWith(" ") && !allSpaces);
		return pad ? fence + " " + content + " " + fence : fence + content + fence;
	}

	public static Renderable lineBreak() {
		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != '\n') {
					// 2 spaces and then new line would create a line break in text
					buffer.append("  ");
				}
				buffer.append("\n");
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<br>");
			}
		};
	}

	public static Renderable bold(String text) {
		return bold(text(text));
	}

	public static Renderable bold(Renderable text) {

		return new Renderable() {

			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				buffer.append("**");
				text.renderAsMarkdown(buffer);
				buffer.append("**");
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.raw("<b>");
				text.renderAsHtml(buffer);
				buffer.raw("</b>");
			}
		};
	}

	public static Renderable text(String text) {
		return new Renderable() {
			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				// TODO: handle escaping
				buffer.append(text);
			}

			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				buffer.text(text);
			}
		};
	}
	
	public static Renderable lazy(Supplier<Renderable> supplier) {
		return new Renderable() {
			
			@Override
			public void renderAsMarkdown(StringBuilder buffer) {
				supplier.get().renderAsMarkdown(buffer);
			}
			
			@Override
			public void renderAsHtml(HtmlBuffer buffer) {
				supplier.get().renderAsHtml(buffer);
			}
		};
	}

	public static Renderable fromClasspath(final Class<?> klass, final String resourcePath) {
		return Renderables.lazy(() -> {
			String html = getText(klass, resourcePath, ".html");
			String markdown = getText(klass, resourcePath, ".md");
			if (html == null && markdown == null) {
				return NO_DESCRIPTION;
			} else {
				return new Renderable() {
					
					@Override
					public void renderAsMarkdown(StringBuilder buffer) {
						if (markdown != null) {
							buffer.append(markdown);
						} else {
							buffer.append(getHtmlToMarkdownConverter().convert(html));
						}
					}
		
					@Override
					public void renderAsHtml(HtmlBuffer buffer) {
						if (html != null) {
							buffer.raw(html);
						} else {
							//TODO: proper conversion to html
							buffer.raw("<pre>");
							buffer.raw(markdown);
							buffer.raw("</pre>");
						}
					}
		
				};

			}
		});
	}

	private static String getText(final Class<?> klass, String resourcePath, String extension) {
		if (extension != null) {
			resourcePath = resourcePath + extension;
		}
		try {
			InputStream stream = klass.getResourceAsStream(resourcePath);
			if (stream != null) {
				return IOUtil.toString(stream);
			}
		} catch (Exception e) {
			logger.error("Error", e);
		}
		return null;
	}

	private static class ConcatRenderables implements Renderable {

		private Renderable[] pieces;

		ConcatRenderables(Renderable... pieces) {
			this.pieces = pieces;
		}

		public ConcatRenderables(List<Renderable> pieces) {
			this(pieces.toArray(new Renderable[pieces.size()]));
		}

		@Override
		public void renderAsHtml(HtmlBuffer buffer) {
			for (Renderable hoverInfo : pieces) {
				hoverInfo.renderAsHtml(buffer);
			}
		}

		@Override
		public void renderAsMarkdown(StringBuilder buffer) {
			for (Renderable hoverInfo : pieces) {
				hoverInfo.renderAsMarkdown(buffer);
			}
		}

	}
}
