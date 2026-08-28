/*******************************************************************************
 * Copyright (c) 2017, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.autowired.AutowiredHoverProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveBean;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveBeansModel;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringResource;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.Renderables;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

public class LiveHoverUtils {

	private static final Logger log = LoggerFactory.getLogger(LiveHoverUtils.class);

	public static final LiveBean CANT_MATCH_PROPER_BEAN = LiveBean.builder().id("UNKNOWN").build();

	public static String showBean(LiveBean bean) {
		StringBuilder buf = new StringBuilder("Bean [id: " + bean.getId());
		String type = bean.getType(true);
		if (type != null) {
			buf.append(", type: " + Renderables.inlineSnippet(type).toMarkdown());
		}
		buf.append(']');
		return buf.toString();
	}

	public static String showBeanWithResource(SourceLinks sourceLinks, LiveBean bean, String indentStr, IJavaProject project) {
		String newline = "  \n"+indentStr; //Note: the double space before newline makes markdown see it as a real line break

		if (bean == CANT_MATCH_PROPER_BEAN) {
			return "(Cannot find precise information for the bean)";
		} else {
			String type = bean.getType(true);

			StringBuilder buf = new StringBuilder("Bean: ");
			buf.append(Renderables.inlineSnippet(bean.getId()).toMarkdown());
			if (type != null) {
				// Try creating a URL link to open source for the type
				buf.append(newline);
				buf.append("Type: ");
				Optional<String> url = sourceLinks.sourceLinkUrlForFQName(project, type);
				if (url.isPresent()) {
					buf.append(Renderables.link(type, url.get()).toMarkdown());
				} else {
					buf.append(Renderables.inlineSnippet(type).toMarkdown());
				}
			}
			String resource = bean.getResource();
			if (StringUtil.hasText(resource)) {
				buf.append(newline);
				buf.append("Resource: ");
				buf.append(showResource(sourceLinks, resource, project));
			}
			return buf.toString();
		}
	}

	public static String getShortDisplayType(LiveBean bean) {
		if (bean == CANT_MATCH_PROPER_BEAN) {
			return CANT_MATCH_PROPER_BEAN.getId();
		} else {
			String type = bean.getType(true);
			int idx = type.lastIndexOf('.');
			String typeStr = idx < 0 || idx == type.length() - 1 ? type : type.substring(idx + 1);
			return typeStr;
		}
	}

	public static String showBeanTypeMarkdown(SourceLinks sourceLinks, IJavaProject project, LiveBean bean) {
		if (bean == CANT_MATCH_PROPER_BEAN) {
			return CANT_MATCH_PROPER_BEAN.getId();
		} else {
			String type = bean.getType(true);
			String displayId = Renderables.inlineSnippet(getShortDisplayType(bean)).toMarkdown();
			if (type != null && sourceLinks != null) {
				Optional<String> url = sourceLinks.sourceLinkUrlForFQName(project, type);
				if (url.isPresent()) {
					return Renderables.link(displayId, url.get()).toMarkdown();
				}
			}
			return displayId;
		}
	}

	public static boolean doBeansFitInline(Collection<LiveBean> beans, int maxLength, String delimiter) {
		int length = 0;
		for (LiveBean bean : beans) {
			if (length != 0) {
				length += delimiter.length();
			}
			length += bean.getId().length();
			if (length > maxLength) {
				return false;
			}
		}
		return true;
	}

	public static String showResource(SourceLinks sourceLinks, String resource, IJavaProject project) {
		return new SpringResource(sourceLinks, resource, project).toMarkdown();
	}

	public static boolean hasRelevantBeans(SpringProcessLiveData liveData, LiveBean definedBean) {
		return findRelevantBeans(liveData, definedBean).stream().findAny().isPresent();
	}

	public static List<LiveBean> findRelevantBeans(SpringProcessLiveData liveData, LiveBean definedBean) {
		LiveBeansModel beansModel = liveData.getBeans();
		if (beansModel != null) {
			List<LiveBean> relevantBeans = beansModel.getBeansOfName(definedBean.getId());
			String type = definedBean.getType(true);
			if (type != null) {
				// TODO: check if we should check for bean type rather than id that we build ourselves based on type
//				if (relevantBeans.isEmpty()) {
//					relevantBeans = beansModel.getBeansOfType(type);
//				} else {
					relevantBeans = relevantBeans.stream().filter(bean -> type.equals(bean.getType(true))).collect(Collectors.toList());
//				}
			}
			return relevantBeans;
		}
		return Collections.emptyList();
	}

	public static List<LiveBean> findAllDependencyBeans(SpringProcessLiveData liveData, List<LiveBean> relevantBeans) {
		LiveBeansModel beans = liveData.getBeans();
		return relevantBeans.stream()
				.flatMap(b -> Arrays.stream(b.getDependencies())).distinct()
				.flatMap(d -> beans.getBeansOfName(d).stream()).collect(Collectors.toList());

	}

	public static String niceAppName(SpringProcessLiveData liveData) {
		try {
			return niceAppName(liveData.getProcessID(), liveData.getProcessName());
		} catch (Exception e) {
			log.error("", e);
			return liveData.toString();
		}
	}

	private static final Pattern HEX_HASH = Pattern.compile("^[A-Za-z0-9]+$"); 
	
	public static String niceAppName(String processId, String processName) {
		int pidLen = processId.length();
		if (pidLen > 32 && HEX_HASH.matcher(processId).matches()) {
			//looks like a long hash. Shorten it for display purposes
			processId = processId.substring(0, 12);
		}
		if (processName.contains(processId)) {
			//somewhat redundant to display both if one contains the other
			return "Process ["+processName+"]";
		}
		return "Process [PID="+processId+", name=`"+processName+"`]";
	}

	public static List<CodeLens> createCodeLensesForBeans(Range range, Collection<LiveBean> relevantBeans, String prefix, int maxInlineBeansStringLength, String beansSeparator) {
		if (!relevantBeans.isEmpty()) {
			CodeLens codeLens = new CodeLens();
			codeLens.setRange(range);
			StringBuilder sb = createBeansTitlePlainText(relevantBeans, prefix, maxInlineBeansStringLength, beansSeparator);
			codeLens.setData(sb.toString());
			Command cmd = new Command();
			cmd.setTitle(sb.toString());
			cmd.setCommand("sts.showHoverAtPosition");
			cmd.setArguments(ImmutableList.of(range.getStart()));
			codeLens.setCommand(cmd);

			return ImmutableList.of(codeLens);
		} else {
			return ImmutableList.of();
		}

	}
	
	public static CodeLens createCodeLenseForBeanStartupMetric(Range range, Duration startupTime) {
		CodeLens codeLens = new CodeLens();
		codeLens.setRange(range);
		StringBuilder sb = new StringBuilder("Startup: ");
		sb.append(startupTime.toMillis());
		sb.append("ms");
		codeLens.setData(sb.toString());
		Command cmd = new Command();
		cmd.setTitle(sb.toString());
		cmd.setCommand("sts.showHoverAtPosition");
		cmd.setArguments(ImmutableList.of(range.getStart()));
		codeLens.setCommand(cmd);
		return codeLens;
	}

	@SuppressWarnings("unchecked")
	public static List<CodeLens> createCodeLensForMethodParameters(SpringProcessLiveData liveData, IJavaProject project, MethodDeclaration method, TextDocument doc, List<LiveBean> wiredBeans) {
		ImmutableList.Builder<CodeLens> builder = ImmutableList.builder();
		method.parameters().forEach(p -> {
			if (p instanceof SingleVariableDeclaration) {
				SingleVariableDeclaration parameter = (SingleVariableDeclaration) p;
				List<LiveBean> parameterMatchingBean = AutowiredHoverProvider.findAutowiredBeans(project, parameter, wiredBeans);
				if (parameterMatchingBean.size() == 0) {
					log.warn("No Live Bean matching parameter `" + parameter.getName().getIdentifier() + " for method " + method);
				} else {
					try {
						builder.add(new CodeLens(ASTUtils.nodeRegion(doc, parameter.getName()).asRange()));
					} catch (BadLocationException e) {
						// ignore
					}
				}
			}
		});
		return builder.build();
	}

	public static StringBuilder createBeansTitlePlainText(Collection<LiveBean> beans, String prefix, int maxInlineBeansStringLength, String beansSeparator) {
		StringBuilder sb = new StringBuilder(prefix);
		if (LiveHoverUtils.doBeansFitInline(beans, maxInlineBeansStringLength, beansSeparator)) {
			sb.append(beans.stream().map(LiveHoverUtils::getShortDisplayType).collect(Collectors.joining(beansSeparator)));
		} else {
			sb.append(beans.size());
			sb.append(" bean");
			if (beans.size() > 1) {
				sb.append("s");
			}
		}
		return sb;
	}

	public static StringBuilder createBeansTitleMarkdown(SourceLinks sourceLinks, IJavaProject project, Collection<LiveBean> beans, String prefix, int maxInlineBeansStringLength, String beansSeparator) {
		StringBuilder sb = new StringBuilder(prefix);
		if (LiveHoverUtils.doBeansFitInline(beans, maxInlineBeansStringLength, beansSeparator)) {
			sb.append(beans.stream().map(b -> showBeanTypeMarkdown(sourceLinks, project, b)).collect(Collectors.joining(beansSeparator)));
		} else {
			sb.append(beans.size());
			sb.append(" bean");
			if (beans.size() > 1) {
				sb.append("s");
			}
		}
		return sb;
	}
}
