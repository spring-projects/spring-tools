/*******************************************************************************
 * Copyright (c) 2016 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.jface.text.templates.TemplateVariableResolver;
import org.springframework.ide.eclipse.boot.util.Log;

import com.google.common.collect.ImmutableMap;

@SuppressWarnings("restriction")
public class BootJavaContext extends JavaContext {

	private static final Pattern CONTEXT_TAG = Pattern.compile("\\[[^\\[\\]]*\\]");

	public static final Map<String, Predicate<BootJavaContext>> CONTEXT_TAG_CHECKERS = negate(ImmutableMap.of(
			"test", BootJavaContext::isTestContext,
			"assertj", BootJavaContext::isAssertJContext
	));

	public static class ContextVariableResolver extends TemplateVariableResolver {
		@Override
		public void resolve(TemplateVariable variable, TemplateContext context) {
			//This is really a dummy resolver. Actually this variable
			variable.setValue("");
		}
	}

	public BootJavaContext(TemplateContextType type, IDocument document, int completionOffset, int completionLength,
			ICompilationUnit compilationUnit) {
		super(type, document, completionOffset, completionLength, compilationUnit);
	}

	private static <T> Map<String, Predicate<T>> negate(ImmutableMap<String, Predicate<T>> base) {
		ImmutableMap.Builder<String, Predicate<T>> builder = ImmutableMap.builder();
		builder.putAll(base);
		for (Entry<String, Predicate<T>> e : base.entrySet()) {
			String name = e.getKey();
			if (!name.startsWith("!")) {
				builder.put("!"+name, e.getValue().negate());
			}
		}
		return builder.build();
	}

	public BootJavaContext(TemplateContextType type, IDocument document, Position completionPosition,
			ICompilationUnit compilationUnit) {
		super(type, document, completionPosition, compilationUnit);
	}

	@Override
	public boolean canEvaluate(Template template) {
		if (super.canEvaluate(template)) {
			List<String> contextTags = parseContextTags(template);
			for (String tag : contextTags) {
				Predicate<BootJavaContext> checker = CONTEXT_TAG_CHECKERS.get(tag);
				if (checker != null && !checker.test(this)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private List<String> parseContextTags(Template template) {
		return parseContextTags(template.getDescription());
	}

	private List<String> parseContextTags(String text) {
		Matcher matcher = CONTEXT_TAG.matcher(text);
		List<String> tags = new ArrayList<>();
		while (matcher.find()) {
			tags.add(text.substring(matcher.start()+1, matcher.end()-1));
		}
		return tags;
	}

	public boolean isAssertJContext() {
		try {
			ICompilationUnit cu = getCompilationUnit();
			if (cu != null) {
				IJavaProject jp = cu.getJavaProject();
				if (jp != null) {
					IType t = jp.findType("org.assertj.core.api.Assertions");
					return t != null;
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	public boolean isTestContext() {
		//True if the current file is in a src folder that has a segment with name 'test' in its path.
		try {
			ICompilationUnit cu = getCompilationUnit();
			if (cu != null) {
				IPackageFragmentRoot pfr = (IPackageFragmentRoot) cu.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (pfr != null) {
					IClasspathEntry cpe = pfr.getRawClasspathEntry();
					if (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						IPath sourcePath = cpe.getPath().removeFirstSegments(1); //remove first... it doesn't count if project is called 'test'.
						if (sourcePath != null) {
							for (String segment : sourcePath.segments()) {
								if (segment.equals("test")) {
									return true;
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

}
