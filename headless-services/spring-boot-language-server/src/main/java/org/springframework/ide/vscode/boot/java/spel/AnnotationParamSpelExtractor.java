/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.spel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLangAstUtils;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippetWithPrefixAndSuffix;
import org.springframework.ide.vscode.commons.java.JavaUtils;

/**
 * Knows about the annotation attributes that carry SpEL expressions and extracts
 * those expressions from annotations in the Java AST.
 * <p>
 * Attributes come in three flavours:
 * <ul>
 * <li>the attribute value is a SpEL expression as a whole (for example
 * {@code @PreAuthorize})</li>
 * <li>the attribute value is a template that embeds SpEL expressions in
 * {@code #{...}} delimiters (for example {@code @Value})</li>
 * <li>both of the above, the {@code #{...}} delimiters are optional (for example
 * {@code @ConditionalOnExpression})</li>
 * </ul>
 */
public final class AnnotationParamSpelExtractor {

	private static final String SPRING_CACHEABLE = "org.springframework.cache.annotation.Cacheable";
	private static final String SPRING_CACHE_PUT = "org.springframework.cache.annotation.CachePut";
	private static final String SPRING_CACHE_EVICT = "org.springframework.cache.annotation.CacheEvict";

	private static final String SPRING_RESILIENCE_RETRYABLE = "org.springframework.resilience.annotation.Retryable";
	private static final String SPRING_RESILIENCE_CONCURRENCY_LIMIT = "org.springframework.resilience.annotation.ConcurrencyLimit";

	private static final String SPRING_PRE_AUTHORIZE = "org.springframework.security.access.prepost.PreAuthorize";
	private static final String SPRING_PRE_FILTER = "org.springframework.security.access.prepost.PreFilter";
	private static final String SPRING_POST_AUTHORIZE = "org.springframework.security.access.prepost.PostAuthorize";
	private static final String SPRING_POST_FILTER= "org.springframework.security.access.prepost.PostFilter";
	private static final String SPRING_AUTHENTICATION_PRINCIPAL = "org.springframework.security.core.annotation.AuthenticationPrincipal";
	private static final String SPRING_CURRENT_SECURITY_CONTEXT = "org.springframework.security.core.annotation.CurrentSecurityContext";

	private static final String SPRING_CONDITIONAL_ON_EXPRESSION = "org.springframework.boot.autoconfigure.condition.ConditionalOnExpression";

	private static final String SPRING_PAYLOAD = "org.springframework.messaging.handler.annotation.Payload";
	private static final String SPRING_JMS_LISTENER = "org.springframework.jms.annotation.JmsListener";

	private static final String SPRING_GATEWAY = "org.springframework.integration.annotation.Gateway";
	private static final String SPRING_GATEWAY_HEADER = "org.springframework.integration.annotation.GatewayHeader";
	private static final String SPRING_MESSAGING_GATEWAY = "org.springframework.integration.annotation.MessagingGateway";

	private static final String SPRING_RABBIT_LISTENER = "org.springframework.amqp.rabbit.annotation.RabbitListener";
	private static final String SPRING_AMQP_QUEUE = "org.springframework.amqp.rabbit.annotation.Queue";
	private static final String SPRING_AMQP_EXCHANGE = "org.springframework.amqp.rabbit.annotation.Exchange";
	private static final String SPRING_AMQP_QUEUE_BINDING = "org.springframework.amqp.rabbit.annotation.QueueBinding";

	private static final String SPRING_KAFKA_LISTENER = "org.springframework.kafka.annotation.KafkaListener";

	private static final String SPRING_RETRY_RETRYABLE = "org.springframework.retry.annotation.Retryable";
	private static final String SPRING_RETRY_BACKOFF = "org.springframework.retry.annotation.Backoff";

	private static final String TEMPLATE_PREFIX = "#{";
	private static final String TEMPLATE_SUFFIX = "}";

	public final static AnnotationParamSpelExtractor[] SPEL_EXTRACTORS = createSpelExtractors();

	private static AnnotationParamSpelExtractor[] createSpelExtractors() {
		List<AnnotationParamSpelExtractor> extractors = new ArrayList<>();

		// spring-context
		template(extractors, Annotations.VALUE, "value");
		plain(extractors, SPRING_CACHEABLE, "key", "condition", "unless");
		plain(extractors, SPRING_CACHE_PUT, "key", "condition", "unless");
		plain(extractors, SPRING_CACHE_EVICT, "key", "condition");
		plain(extractors, Annotations.EVENT_LISTENER, "condition");
		template(extractors, Annotations.SCHEDULED, "cron", "zone", "fixedDelayString", "fixedRateString",
				"initialDelayString");
		template(extractors, Annotations.ASYNC, "value");
		template(extractors, SPRING_RESILIENCE_RETRYABLE, "maxRetriesString", "timeoutString", "delayString",
				"jitterString", "multiplierString", "maxDelayString");
		template(extractors, SPRING_RESILIENCE_CONCURRENCY_LIMIT, "limitString");

		// spring-boot
		optionalTemplate(extractors, SPRING_CONDITIONAL_ON_EXPRESSION, "value");

		// spring-security
		plain(extractors, SPRING_PRE_AUTHORIZE, "value");
		plain(extractors, SPRING_PRE_FILTER, "value");
		plain(extractors, SPRING_POST_AUTHORIZE, "value");
		plain(extractors, SPRING_POST_FILTER, "value");
		plain(extractors, SPRING_AUTHENTICATION_PRINCIPAL, "expression");
		plain(extractors, SPRING_CURRENT_SECURITY_CONTEXT, "expression");

		// spring-messaging and spring-jms
		plain(extractors, SPRING_PAYLOAD, "value", "expression");
		template(extractors, SPRING_JMS_LISTENER, "id", "containerFactory", "destination", "subscription", "selector",
				"concurrency");

		// spring-integration
		plain(extractors, SPRING_GATEWAY, "payloadExpression", "requestTimeoutExpression", "replyTimeoutExpression");
		plain(extractors, SPRING_GATEWAY_HEADER, "expression");
		plain(extractors, SPRING_MESSAGING_GATEWAY, "defaultPayloadExpression", "defaultRequestTimeout",
				"defaultReplyTimeout");

		// spring-amqp
		template(extractors, SPRING_RABBIT_LISTENER, "id", "containerFactory", "queues", "priority", "admin", "group",
				"concurrency", "autoStartup", "returnExceptions", "errorHandler", "executor", "ackMode",
				"replyPostProcessor", "messageConverter", "replyContentType", "converterWinsContentType", "batch");
		template(extractors, SPRING_AMQP_QUEUE, "value", "name", "durable", "exclusive", "autoDelete",
				"ignoreDeclarationExceptions", "declare", "admins");
		template(extractors, SPRING_AMQP_EXCHANGE, "value", "name", "type", "durable", "autoDelete", "internal",
				"ignoreDeclarationExceptions", "delayed", "declare", "admins");
		template(extractors, SPRING_AMQP_QUEUE_BINDING, "key", "ignoreDeclarationExceptions", "declare", "admins");

		// spring-kafka
		template(extractors, SPRING_KAFKA_LISTENER, "id", "containerFactory", "topics", "topicPattern",
				"containerGroup", "errorHandler", "groupId", "clientIdPrefix", "concurrency", "autoStartup",
				"properties", "contentTypeConverter", "batch", "filter", "info", "containerPostProcessor");

		// spring-retry
		optionalTemplate(extractors, SPRING_RETRY_RETRYABLE, "maxAttemptsExpression", "exceptionExpression");
		optionalTemplate(extractors, SPRING_RETRY_BACKOFF, "delayExpression", "maxDelayExpression",
				"multiplierExpression", "randomExpression");

		return extractors.toArray(AnnotationParamSpelExtractor[]::new);
	}

	/**
	 * The attribute value is a SpEL expression as a whole.
	 */
	private static void plain(List<AnnotationParamSpelExtractor> extractors, String annotationType,
			String... paramNames) {
		add(extractors, annotationType, List.of(new PrefixSuffix("", "")), paramNames);
	}

	/**
	 * The attribute value embeds SpEL expressions in <code>#{...}</code> delimiters.
	 */
	private static void template(List<AnnotationParamSpelExtractor> extractors, String annotationType,
			String... paramNames) {
		add(extractors, annotationType, List.of(new PrefixSuffix(TEMPLATE_PREFIX, TEMPLATE_SUFFIX)), paramNames);
	}

	/**
	 * The <code>#{...}</code> delimiters around the SpEL expression are optional.
	 */
	private static void optionalTemplate(List<AnnotationParamSpelExtractor> extractors, String annotationType,
			String... paramNames) {
		add(extractors, annotationType,
				List.of(new PrefixSuffix(TEMPLATE_PREFIX, TEMPLATE_SUFFIX), new PrefixSuffix("", "")), paramNames);
	}

	private static void add(List<AnnotationParamSpelExtractor> extractors, String annotationType,
			List<PrefixSuffix> prefixSuffixes, String... paramNames) {
		for (String paramName : paramNames) {
			extractors.add(new AnnotationParamSpelExtractor(annotationType, paramName, prefixSuffixes));
			if ("value".equals(paramName)) {
				// the 'value' attribute can also be given without naming it explicitly
				extractors.add(new AnnotationParamSpelExtractor(annotationType, null, prefixSuffixes));
			}
		}
	}

	public record PrefixSuffix(String prefix, String suffix) {}

	private final String annotationBindingKey;
	private final String paramName;

	private final List<PrefixSuffix> prefixSuffixes;

	private AnnotationParamSpelExtractor(String annotationType, String paramName, List<PrefixSuffix> prefixSuffixes) {
		this.annotationBindingKey = JavaUtils.typeFqNameToBindingKey(annotationType);
		this.paramName = paramName;
		this.prefixSuffixes = prefixSuffixes;
	}

	public List<EmbeddedLanguageSnippet> getSpelRegions(NormalAnnotation a) {
		if (paramName == null) {
			return Collections.emptyList();
		}

		// look for the attribute first, that is a lot cheaper than resolving the
		// annotation type and walking its hierarchy
		Expression paramValue = findParamValue(a);
		if (paramValue == null || !isApplicable(a)) {
			return Collections.emptyList();
		}

		return fromValueExpression(paramValue);
	}

	private Expression findParamValue(NormalAnnotation a) {
		for (Object value : a.values()) {
			if (value instanceof MemberValuePair pair
					&& paramName.equals(pair.getName().getFullyQualifiedName())) {
				return pair.getValue();
			}
		}
		return null;
	}

	public List<EmbeddedLanguageSnippet> getSpelRegions(SingleMemberAnnotation a) {
		if (this.paramName != null) {
			return Collections.emptyList();
		}

		if (!isApplicable(a)) {
			return Collections.emptyList();
		}

		return fromValueExpression(a.getValue());
	}

	private boolean isApplicable(Annotation a) {
		AnnotationHierarchies hierarchies = AnnotationHierarchies.get(a);
		return hierarchies != null && hierarchies.isAnnotatedWithAnnotationByBindingKey(a.resolveAnnotationBinding(),
				this.annotationBindingKey);
	}

	private List<EmbeddedLanguageSnippet> fromValueExpression(Expression valueExp) {
		if (valueExp instanceof ArrayInitializer array) {
			List<EmbeddedLanguageSnippet> snippets = new ArrayList<>();
			for (Object element : array.expressions()) {
				if (element instanceof Expression elementExp) {
					snippets.addAll(fromSingleValueExpression(elementExp));
				}
			}
			return snippets;
		}
		return fromSingleValueExpression(valueExp);
	}

	private List<EmbeddedLanguageSnippet> fromSingleValueExpression(Expression valueExp) {
		EmbeddedLanguageSnippet embeddedSnippet = EmbeddedLangAstUtils.extractEmbeddedExpression(valueExp);
		if (embeddedSnippet == null) {
			return Collections.emptyList();
		}
		return fromEmbeddedSnippet(embeddedSnippet);
	}

	private List<EmbeddedLanguageSnippet> fromEmbeddedSnippet(EmbeddedLanguageSnippet embeddedSnippet) {
		String value = embeddedSnippet.getText();
		if (value != null && !value.isBlank()) {
			for (PrefixSuffix ps : prefixSuffixes) {
				int startIdx = value.indexOf(ps.prefix);
				if (startIdx >= 0) {
					int start = startIdx + ps.prefix.length();

					if (isTemplate(ps) && value.indexOf(ps.prefix, start) >= 0) {
						// more than one expression embedded in the same value, so the
						// delimiters have to be matched up per expression
						List<EmbeddedLanguageSnippet> snippets = templateRegions(embeddedSnippet, value);
						if (!snippets.isEmpty()) {
							return snippets;
						}
					}

					int endIdx = value.lastIndexOf(ps.suffix);
					// the suffix has to come after the prefix, otherwise there is no expression
					// in between the two and the delimiters are just part of the plain value
					if (endIdx >= start) {
						return List.of(new EmbeddedLanguageSnippetWithPrefixAndSuffix(embeddedSnippet, start, endIdx));
					}
				}
			}
		}
		return Collections.emptyList();
	}

	private static boolean isTemplate(PrefixSuffix ps) {
		return TEMPLATE_PREFIX.equals(ps.prefix) && TEMPLATE_SUFFIX.equals(ps.suffix);
	}

	private static List<EmbeddedLanguageSnippet> templateRegions(EmbeddedLanguageSnippet embeddedSnippet,
			String value) {
		List<EmbeddedLanguageSnippet> snippets = new ArrayList<>();
		for (int idx = value.indexOf(TEMPLATE_PREFIX); idx >= 0;) {
			int start = idx + TEMPLATE_PREFIX.length();
			int end = endOfTemplateExpression(value, start);
			if (end < 0) {
				break;
			}
			snippets.add(new EmbeddedLanguageSnippetWithPrefixAndSuffix(embeddedSnippet, start, end));
			idx = value.indexOf(TEMPLATE_PREFIX, end);
		}
		return snippets;
	}

	/**
	 * Finds the closing brace of the expression that starts at <code>start</code>,
	 * skipping over nested braces and over braces inside of string literals.
	 *
	 * @return the index of the closing brace or -1 if the expression is not closed
	 */
	private static int endOfTemplateExpression(String value, int start) {
		int depth = 1;
		for (int i = start; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\'' || c == '"') {
				i = endOfStringLiteral(value, i);
				if (i < 0) {
					return -1;
				}
			}
			else if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * @return the index of the quote that closes the literal starting at
	 *         <code>start</code> or -1 if the literal is not closed
	 */
	private static int endOfStringLiteral(String value, int start) {
		char quote = value.charAt(start);
		for (int i = start + 1; i < value.length(); i++) {
			if (value.charAt(i) == quote) {
				if (i + 1 < value.length() && value.charAt(i + 1) == quote) {
					// a doubled quote is an escaped quote within the literal
					i++;
				}
				else {
					return i;
				}
			}
		}
		return -1;
	}

}
