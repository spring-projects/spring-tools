/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.placeholder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.ide.vscode.commons.java.JavaUtils;

/**
 * The annotation attributes Spring resolves <code>${...}</code> placeholders
 * in, and how to read their values from the Java AST.
 * <p>
 * Edit the table in {@link #createExtractors()} to add or remove an attribute.
 * An annotation meta-annotated with a listed one matches too, so the composed
 * mappings such as <code>@GetMapping</code> are covered by <code>@RequestMapping</code>.
 * Attributes that hold a whole SpEL expression are not listed, the SpEL
 * reconciler checks the placeholders inside them.
 *
 * @author Seema Jagadeesh
 */
public final class AnnotationParamPlaceholderExtractor {

	private static final String SPRING_PROPERTY_SOURCE = "org.springframework.context.annotation.PropertySource";
	private static final String SPRING_IMPORT_RESOURCE = "org.springframework.context.annotation.ImportResource";
	private static final String SPRING_COMPONENT_SCAN = "org.springframework.context.annotation.ComponentScan";

	private static final String SPRING_CROSS_ORIGIN = "org.springframework.web.bind.annotation.CrossOrigin";
	private static final String SPRING_REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam";
	private static final String SPRING_REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader";
	private static final String SPRING_COOKIE_VALUE = "org.springframework.web.bind.annotation.CookieValue";
	private static final String SPRING_MATRIX_VARIABLE = "org.springframework.web.bind.annotation.MatrixVariable";

	private static final String SPRING_TEST_PROPERTY_SOURCE = "org.springframework.test.context.TestPropertySource";

	private static final String SPRING_JMS_LISTENER = "org.springframework.jms.annotation.JmsListener";

	private static final String SPRING_RABBIT_LISTENER = "org.springframework.amqp.rabbit.annotation.RabbitListener";
	private static final String SPRING_AMQP_QUEUE = "org.springframework.amqp.rabbit.annotation.Queue";
	private static final String SPRING_AMQP_EXCHANGE = "org.springframework.amqp.rabbit.annotation.Exchange";
	private static final String SPRING_AMQP_QUEUE_BINDING = "org.springframework.amqp.rabbit.annotation.QueueBinding";

	private static final String SPRING_KAFKA_LISTENER = "org.springframework.kafka.annotation.KafkaListener";
	private static final String SPRING_KAFKA_TOPIC_PARTITION = "org.springframework.kafka.annotation.TopicPartition";
	private static final String SPRING_KAFKA_PARTITION_OFFSET = "org.springframework.kafka.annotation.PartitionOffset";

	public static final AnnotationParamPlaceholderExtractor[] PLACEHOLDER_EXTRACTORS = createExtractors();

	private static final Set<String> ANNOTATION_BINDING_KEYS = Arrays.stream(PLACEHOLDER_EXTRACTORS)
			.map(e -> e.annotationBindingKey)
			.collect(Collectors.toUnmodifiableSet());

	/**
	 * The annotations and attributes that get the placeholder check.
	 */
	private static AnnotationParamPlaceholderExtractor[] createExtractors() {
		List<AnnotationParamPlaceholderExtractor> extractors = new ArrayList<>();

		// spring-context
		add(extractors, Annotations.VALUE, "value");
		add(extractors, Annotations.SCHEDULED, "cron", "zone", "fixedDelayString", "fixedRateString",
				"initialDelayString");
		add(extractors, SPRING_PROPERTY_SOURCE, "value");
		add(extractors, SPRING_IMPORT_RESOURCE, "value", "locations");
		add(extractors, SPRING_COMPONENT_SCAN, "value", "basePackages");

		// spring-web, @GetMapping and the other composed mappings match through @RequestMapping
		add(extractors, Annotations.SPRING_REQUEST_MAPPING, "value", "path");
		add(extractors, SPRING_CROSS_ORIGIN, "value", "origins", "originPatterns");
		add(extractors, SPRING_REQUEST_PARAM, "defaultValue");
		add(extractors, SPRING_REQUEST_HEADER, "defaultValue");
		add(extractors, SPRING_COOKIE_VALUE, "defaultValue");
		add(extractors, SPRING_MATRIX_VARIABLE, "defaultValue");

		// spring-test
		add(extractors, SPRING_TEST_PROPERTY_SOURCE, "value", "locations");

		// spring-jms
		add(extractors, SPRING_JMS_LISTENER, "id", "containerFactory", "destination", "subscription", "selector",
				"concurrency");

		// spring-amqp
		add(extractors, SPRING_RABBIT_LISTENER, "id", "containerFactory", "queues", "priority", "admin", "group",
				"concurrency", "autoStartup", "ackMode", "replyContentType", "batch");
		add(extractors, SPRING_AMQP_QUEUE, "value", "name", "durable", "exclusive", "autoDelete",
				"ignoreDeclarationExceptions", "declare", "admins");
		add(extractors, SPRING_AMQP_EXCHANGE, "value", "name", "type", "durable", "autoDelete", "internal",
				"ignoreDeclarationExceptions", "delayed", "declare", "admins");
		add(extractors, SPRING_AMQP_QUEUE_BINDING, "key", "ignoreDeclarationExceptions", "declare", "admins");

		// spring-kafka
		add(extractors, SPRING_KAFKA_LISTENER, "id", "containerFactory", "topics", "topicPattern",
				"containerGroup", "groupId", "clientIdPrefix", "concurrency", "autoStartup", "properties", "batch",
				"info");
		add(extractors, SPRING_KAFKA_TOPIC_PARTITION, "topic", "partitions");
		add(extractors, SPRING_KAFKA_PARTITION_OFFSET, "partition", "initialOffset");

		return extractors.toArray(AnnotationParamPlaceholderExtractor[]::new);
	}

	/**
	 * Whether the annotation, or one it is meta-annotated with, is in the table.
	 * One walk of the annotation hierarchy per annotation node, instead of one per extractor.
	 */
	public static boolean isListed(Annotation a) {
		AnnotationHierarchies hierarchies = AnnotationHierarchies.get(a);
		return hierarchies != null && hierarchies.isAnnotatedWithAnnotationByBindingKey(a.resolveAnnotationBinding(),
				ANNOTATION_BINDING_KEYS::contains);
	}

	private static void add(List<AnnotationParamPlaceholderExtractor> extractors, String annotationType,
			String... paramNames) {
		for (String paramName : paramNames) {
			extractors.add(new AnnotationParamPlaceholderExtractor(annotationType, paramName));
			if ("value".equals(paramName)) {
				// 'value' may also be given without its name
				extractors.add(new AnnotationParamPlaceholderExtractor(annotationType, null));
			}
		}
	}

	private final String annotationBindingKey;
	private final String paramName;

	private AnnotationParamPlaceholderExtractor(String annotationType, String paramName) {
		this.annotationBindingKey = JavaUtils.typeFqNameToBindingKey(annotationType);
		this.paramName = paramName;
	}

	public List<EmbeddedLanguageSnippet> getValueSnippets(NormalAnnotation a) {
		if (paramName == null) {
			return Collections.emptyList();
		}

		// find the attribute first, resolving the annotation type costs more
		Expression paramValue = findParamValue(a);
		if (paramValue == null || !isApplicable(a)) {
			return Collections.emptyList();
		}

		return fromValueExpression(paramValue);
	}

	public List<EmbeddedLanguageSnippet> getValueSnippets(SingleMemberAnnotation a) {
		if (paramName != null) {
			return Collections.emptyList();
		}

		if (!isApplicable(a)) {
			return Collections.emptyList();
		}

		return fromValueExpression(a.getValue());
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
					EmbeddedLanguageSnippet snippet = EmbeddedLangAstUtils.extractEmbeddedExpression(elementExp);
					if (snippet != null) {
						snippets.add(snippet);
					}
				}
			}
			return snippets;
		}
		EmbeddedLanguageSnippet snippet = EmbeddedLangAstUtils.extractEmbeddedExpression(valueExp);
		return snippet == null ? Collections.emptyList() : List.of(snippet);
	}

}
