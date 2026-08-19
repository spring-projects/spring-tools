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
package org.springframework.ide.vscode.boot.java.spel;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * Checks which annotation attributes {@link AnnotationParamSpelExtractor} recognizes
 * as carriers of SpEL expressions, and what exactly it extracts from them.
 *
 * @author Martin Lippert
 */
public class AnnotationParamSpelExtractorTest {

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;

	private MavenJavaProject jp;

	@BeforeEach
	public void setup() throws Exception {
		jp = projects.mavenProjectAlreadyBuilt("test-spel-annotations");
	}

	private List<String> extract(String source) throws Exception {
		String uri = Paths.get(jp.getLocationUri()).resolve("src/main/java/org/test/A.java").toUri().toASCIIString();
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "A.java", jp);
		assertThat(cu).isNotNull();

		List<String> snippets = new ArrayList<>();
		cu.accept(new ASTVisitor() {

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				Arrays.stream(AnnotationParamSpelExtractor.SPEL_EXTRACTORS)
						.flatMap(e -> e.getSpelRegions(node).stream())
						.map(EmbeddedLanguageSnippet::getText)
						.forEach(snippets::add);
				return super.visit(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				Arrays.stream(AnnotationParamSpelExtractor.SPEL_EXTRACTORS)
						.flatMap(e -> e.getSpelRegions(node).stream())
						.map(EmbeddedLanguageSnippet::getText)
						.forEach(snippets::add);
				return super.visit(node);
			}

		});
		return snippets;
	}

	@Test
	void value() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class A {
					@Value("#{@spelService.isValid('1.0.0')}")
					String a;

					@Value(value = "#{@spelService.isValid('2.0.0')}")
					String b;

					@Value("${plain.placeholder}")
					String c;
				}
				""")).containsExactlyInAnyOrder("@spelService.isValid('1.0.0')", "@spelService.isValid('2.0.0')");
	}

	@Test
	void cacheAnnotations() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.cache.annotation.CacheEvict;
				import org.springframework.cache.annotation.CachePut;
				import org.springframework.cache.annotation.Cacheable;

				public class A {
					@Cacheable(cacheNames = "c", key = "#a", condition = "#b", unless = "#c")
					String a(String a) { return a; }

					@CachePut(cacheNames = "c", key = "#d", condition = "#e", unless = "#f")
					String b(String a) { return a; }

					@CacheEvict(cacheNames = "c", key = "#g", condition = "#h")
					void c(String a) {}
				}
				""")).containsExactlyInAnyOrder("#a", "#b", "#c", "#d", "#e", "#f", "#g", "#h");
	}

	@Test
	void scheduledAndAsync() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.scheduling.annotation.Scheduled;

				public class A {
					@Scheduled(cron = "#{@spelService.cron}", zone = "#{@spelService.zone}")
					void a() {}

					@Scheduled(fixedDelayString = "#{@spelService.delay}", fixedRateString = "#{@spelService.rate}",
							initialDelayString = "#{@spelService.initial}")
					void b() {}

					@Scheduled(cron = "0 0 * * * *")
					void c() {}

					@Async("#{@spelService.executor}")
					void d() {}
				}
				""")).containsExactlyInAnyOrder("@spelService.cron", "@spelService.zone", "@spelService.delay",
						"@spelService.rate", "@spelService.initial", "@spelService.executor");
	}

	@Test
	void resilienceAnnotations() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.resilience.annotation.ConcurrencyLimit;
				import org.springframework.resilience.annotation.Retryable;

				public class A {
					@Retryable(maxRetriesString = "#{@spelService.retries}", timeoutString = "#{@spelService.timeout}",
							delayString = "#{@spelService.delay}", jitterString = "#{@spelService.jitter}",
							multiplierString = "#{@spelService.multiplier}", maxDelayString = "#{@spelService.maxDelay}")
					void a() {}

					@ConcurrencyLimit(limitString = "#{@spelService.limit}")
					void b() {}
				}
				""")).containsExactlyInAnyOrder("@spelService.retries", "@spelService.timeout", "@spelService.delay",
						"@spelService.jitter", "@spelService.multiplier", "@spelService.maxDelay",
						"@spelService.limit");
	}

	@Test
	void securityAnnotations() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.security.access.prepost.PostAuthorize;
				import org.springframework.security.access.prepost.PostFilter;
				import org.springframework.security.access.prepost.PreAuthorize;
				import org.springframework.security.access.prepost.PreFilter;
				import org.springframework.security.core.annotation.AuthenticationPrincipal;
				import org.springframework.security.core.annotation.CurrentSecurityContext;

				public class A {
					@PreAuthorize("@spelService.isValid('a')")
					@PostAuthorize(value = "@spelService.isValid('b')")
					@PreFilter("@spelService.isValid('c')")
					@PostFilter("@spelService.isValid('d')")
					void a(@AuthenticationPrincipal(expression = "@spelService.isValid('e')") Object principal,
							@CurrentSecurityContext(expression = "@spelService.isValid('f')") Object context) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.isValid('a')", "@spelService.isValid('b')",
						"@spelService.isValid('c')", "@spelService.isValid('d')", "@spelService.isValid('e')",
						"@spelService.isValid('f')");
	}

	@Test
	void conditionalOnExpression() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

				@ConditionalOnExpression("#{@spelService.isValid('a')}")
				public class A {
				}
				""")).containsExactly("@spelService.isValid('a')");

		assertThat(extract("""
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

				@ConditionalOnExpression("@spelService.isValid('a')")
				public class A {
				}
				""")).containsExactly("@spelService.isValid('a')");
	}

	@Test
	void payload() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.messaging.handler.annotation.Payload;

				public class A {
					void a(@Payload("@spelService.isValid('a')") String p1,
							@Payload(expression = "@spelService.isValid('b')") String p2) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.isValid('a')", "@spelService.isValid('b')");
	}

	@Test
	void jmsListener() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.jms.annotation.JmsListener;

				public class A {
					@JmsListener(id = "#{@spelService.id}", containerFactory = "#{@spelService.factory}",
							destination = "#{@spelService.destination}", subscription = "#{@spelService.subscription}",
							selector = "#{@spelService.selector}", concurrency = "#{@spelService.concurrency}")
					void a(String message) {}

					@JmsListener(destination = "plain.queue")
					void b(String message) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.id", "@spelService.factory", "@spelService.destination",
						"@spelService.subscription", "@spelService.selector", "@spelService.concurrency");
	}

	@Test
	void integrationGatewayAnnotations() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.integration.annotation.Gateway;
				import org.springframework.integration.annotation.GatewayHeader;
				import org.springframework.integration.annotation.MessagingGateway;

				@MessagingGateway(name = "gw", defaultPayloadExpression = "@spelService.isValid('a')",
						defaultRequestTimeout = "@spelService.requestTimeout",
						defaultReplyTimeout = "@spelService.replyTimeout")
				public interface A {
					@Gateway(payloadExpression = "@spelService.isValid('b')",
							requestTimeoutExpression = "@spelService.isValid('c')",
							replyTimeoutExpression = "@spelService.isValid('d')",
							headers = @GatewayHeader(name = "h", expression = "@spelService.isValid('e')"))
					void a();
				}
				""")).containsExactlyInAnyOrder("@spelService.isValid('a')", "@spelService.requestTimeout",
						"@spelService.replyTimeout", "@spelService.isValid('b')", "@spelService.isValid('c')",
						"@spelService.isValid('d')", "@spelService.isValid('e')");
	}

	@Test
	void rabbitListener() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.amqp.rabbit.annotation.RabbitListener;

				public class A {
					@RabbitListener(id = "#{@spelService.id}", queues = "#{@spelService.queue}",
							concurrency = "#{@spelService.concurrency}", errorHandler = "#{@spelService.errorHandler}")
					void a(String message) {}

					@RabbitListener(queues = "plain.queue")
					void b(String message) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.id", "@spelService.queue", "@spelService.concurrency",
						"@spelService.errorHandler");
	}

	@Test
	void rabbitQueueBindingAnnotations() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.amqp.rabbit.annotation.Exchange;
				import org.springframework.amqp.rabbit.annotation.Queue;
				import org.springframework.amqp.rabbit.annotation.QueueBinding;
				import org.springframework.amqp.rabbit.annotation.RabbitListener;

				public class A {
					@RabbitListener(bindings = @QueueBinding(
							value = @Queue(name = "#{@spelService.queue}", durable = "#{@spelService.durable}"),
							exchange = @Exchange(value = "#{@spelService.exchange}", type = "#{@spelService.type}"),
							key = "#{@spelService.key}"))
					void a(String message) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.queue", "@spelService.durable", "@spelService.exchange",
						"@spelService.type", "@spelService.key");
	}

	@Test
	void kafkaListener() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.kafka.annotation.KafkaListener;

				public class A {
					@KafkaListener(id = "#{@spelService.id}", topics = "#{@spelService.topic}",
							groupId = "#{@spelService.groupId}", concurrency = "#{@spelService.concurrency}")
					void a(String message) {}

					@KafkaListener(topicPattern = "#{@spelService.pattern}")
					void b(String message) {}

					@KafkaListener(topics = "plain-topic")
					void c(String message) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.id", "@spelService.topic", "@spelService.groupId",
						"@spelService.concurrency", "@spelService.pattern");
	}

	@Test
	void springRetryAnnotations() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.retry.annotation.Backoff;
				import org.springframework.retry.annotation.Retryable;

				public class A {
					@Retryable(maxAttemptsExpression = "#{@spelService.attempts}",
							exceptionExpression = "@spelService.shouldRetry(#root)",
							backoff = @Backoff(delayExpression = "#{@spelService.delay}",
									maxDelayExpression = "@spelService.maxDelay",
									multiplierExpression = "#{@spelService.multiplier}",
									randomExpression = "#{@spelService.random}"))
					void a() {}
				}
				""")).containsExactlyInAnyOrder("@spelService.attempts", "@spelService.shouldRetry(#root)",
						"@spelService.delay", "@spelService.maxDelay", "@spelService.multiplier",
						"@spelService.random");
	}

	@Test
	void arrayValuedAttributes() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.kafka.annotation.KafkaListener;

				public class A {
					@KafkaListener(topics = { "#{@spelService.first}", "plain-topic", "#{@spelService.second}" })
					void a(String message) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.first", "@spelService.second");
	}

	@Test
	void severalExpressionsInOneValue() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class A {
					@Value("#{@spelService.first}-#{@spelService.second}")
					String a;
				}
				""")).containsExactly("@spelService.first", "@spelService.second");

		assertThat(extract("""
				package org.test;

				import org.springframework.kafka.annotation.KafkaListener;

				public class A {
					@KafkaListener(id = "#{@spelService.env}-#{@spelService.app}", topics = "${topic.name}-#{@spelService.suffix}")
					void a(String message) {}
				}
				""")).containsExactlyInAnyOrder("@spelService.env", "@spelService.app", "@spelService.suffix");
	}

	@Test
	void singleExpressionWithBracesIsNotSplit() throws Exception {
		// nested braces and braces inside of string literals must not be mistaken
		// for the end of the expression
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class A {
					@Value("#{ {1,2,3}.size() }")
					String a;

					@Value("#{ '}' + \\"{\\" }")
					String b;
				}
				""")).containsExactlyInAnyOrder(" {1,2,3}.size() ", " '}' + \"{\" ");
	}

	@Test
	void severalExpressionsWithBraces() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class A {
					@Value("#{ {1,2}.size() }-#{ '}' }")
					String a;
				}
				""")).containsExactly(" {1,2}.size() ", " '}' ");
	}

	@Test
	void unterminatedExpression() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class A {
					@Value("#{@spelService.first}-#{@spelService.second")
					String a;
				}
				""")).containsExactly("@spelService.first");
	}

	@Test
	void suffixBeforePrefixIsNoExpression() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class A {
					@Value("}#{")
					String a;
				}
				""")).isEmpty();
	}

	@Test
	void unrelatedAnnotationsAreIgnored() throws Exception {
		assertThat(extract("""
				package org.test;

				import org.springframework.beans.factory.annotation.Qualifier;
				import org.springframework.stereotype.Component;

				@Component("#{@spelService.isValid('a')}")
				public class A {
					void a(@Qualifier("#{@spelService.isValid('b')}") String p) {}
				}
				""")).isEmpty();
	}

}
