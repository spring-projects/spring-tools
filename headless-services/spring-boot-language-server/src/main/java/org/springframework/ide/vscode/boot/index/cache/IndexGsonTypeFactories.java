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
package org.springframework.ide.vscode.boot.index.cache;

import org.springframework.ide.vscode.boot.java.beans.ConfigPropertyIndexElement;
import org.springframework.ide.vscode.boot.java.beans.SpringBootApplicationIndexElement;
import org.springframework.ide.vscode.boot.java.data.QueryMethodIndexElement;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement;
import org.springframework.ide.vscode.boot.java.events.EventPublisherIndexElement;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.AddAnnotationRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ApplicationModuleListenerRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ChangeMethodVisibilityRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ConvertQueryToTextBlockRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ExtractRequestMappingParentPathRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.MovePathToRequestMappingRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.PreciseBeanTypeRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.RemoveAnnotationRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ReplaceScopeAnnotationRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.RestControllerRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring;
import org.springframework.ide.vscode.boot.java.requestmapping.HttpExchangeIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.PathPrefixPredicate;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebEndpointIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxHandlerMethodIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxRouteElementRangesIndexElement;
import org.springframework.ide.vscode.boot.java.springai.SpringAiAnnotationIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.RegisteredSubtypesTypeAdapterFactory;
import org.springframework.ide.vscode.commons.protocol.spring.AotProcessorElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeanMethodContainerElement;
import org.springframework.ide.vscode.commons.protocol.spring.BeanRegistrarElement;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.ProjectElement;
import org.springframework.ide.vscode.commons.protocol.spring.SimpleSymbolElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

/**
 * Centralized factory for all Gson polymorphic type adapter factories used by
 * the Spring Boot language server. Each method returns a fully-configured
 * {@link RegisteredSubtypesTypeAdapterFactory} with every known concrete
 * subtype registered explicitly.
 *
 * <p>Subtypes are registered using their fully-qualified class name
 * ({@link Class#getName()}) as the label, which keeps JSON representations
 * backward-compatible with previously-written on-disk caches and LSP protocol
 * messages.
 *
 * <p>Adding a new concrete subtype of any base type requires a corresponding
 * {@code registerSubtype} call here.
 */
public final class IndexGsonTypeFactories {

	private IndexGsonTypeFactories() {
	}

	/**
	 * Returns a factory that handles all known concrete subtypes of
	 * {@link SpringIndexElement}. The type discriminator field is
	 * {@code "_internal_node_type"}.
	 */
	public static RegisteredSubtypesTypeAdapterFactory<SpringIndexElement> springIndexElements() {
		return RegisteredSubtypesTypeAdapterFactory.of(SpringIndexElement.class, "_internal_node_type")
				// commons-lsp-extensions protocol types
				.registerSubtype(ProjectElement.class, ProjectElement.class.getName())
				.registerSubtype(DocumentElement.class, DocumentElement.class.getName())
				.registerSubtype(Bean.class, Bean.class.getName())
				.registerSubtype(BeanMethodContainerElement.class, BeanMethodContainerElement.class.getName())
				.registerSubtype(AotProcessorElement.class, AotProcessorElement.class.getName())
				.registerSubtype(BeanRegistrarElement.class, BeanRegistrarElement.class.getName())
				.registerSubtype(SimpleSymbolElement.class, SimpleSymbolElement.class.getName())
				// spring-boot-language-server types
				.registerSubtype(SpringBootApplicationIndexElement.class, SpringBootApplicationIndexElement.class.getName())
				.registerSubtype(ConfigPropertyIndexElement.class, ConfigPropertyIndexElement.class.getName())
				.registerSubtype(EventPublisherIndexElement.class, EventPublisherIndexElement.class.getName())
				.registerSubtype(EventListenerIndexElement.class, EventListenerIndexElement.class.getName())
				.registerSubtype(QueryMethodIndexElement.class, QueryMethodIndexElement.class.getName())
				.registerSubtype(WebConfigIndexElement.class, WebConfigIndexElement.class.getName())
				.registerSubtype(WebEndpointIndexElement.class, WebEndpointIndexElement.class.getName())
				.registerSubtype(WebfluxRouteElementRangesIndexElement.class, WebfluxRouteElementRangesIndexElement.class.getName())
				.registerSubtype(SpringAiAnnotationIndexElement.class, SpringAiAnnotationIndexElement.class.getName())
				.registerSubtype(StereotypeDefinitionElement.class, StereotypeDefinitionElement.class.getName())
				.registerSubtype(HttpExchangeIndexElement.class, HttpExchangeIndexElement.class.getName())
				.registerSubtype(RequestMappingIndexElement.class, RequestMappingIndexElement.class.getName())
				.registerSubtype(WebfluxHandlerMethodIndexElement.class, WebfluxHandlerMethodIndexElement.class.getName())
				.registerSubtype(StereotypeClassElement.class, StereotypeClassElement.class.getName())
				.registerSubtype(StereotypeMethodElement.class, StereotypeMethodElement.class.getName())
				.registerSubtype(StereotypePackageElement.class, StereotypePackageElement.class.getName());
	}

	/**
	 * Returns a factory that handles all known concrete subtypes of
	 * {@link PathPrefixPredicate}. The type discriminator field is
	 * {@code "_predicate_type"}. The full set is compiler-enforced via the
	 * {@code sealed} interface's {@code permits} clause.
	 */
	public static RegisteredSubtypesTypeAdapterFactory<PathPrefixPredicate> pathPrefixPredicates() {
		return RegisteredSubtypesTypeAdapterFactory.of(PathPrefixPredicate.class, "_predicate_type")
				.registerSubtype(PathPrefixPredicate.AnnotationPredicate.class, PathPrefixPredicate.AnnotationPredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.BasePackagePredicate.class, PathPrefixPredicate.BasePackagePredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.AssignableTypePredicate.class, PathPrefixPredicate.AssignableTypePredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.AndPredicate.class, PathPrefixPredicate.AndPredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.OrPredicate.class, PathPrefixPredicate.OrPredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.NegatePredicate.class, PathPrefixPredicate.NegatePredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.AnyPredicate.class, PathPrefixPredicate.AnyPredicate.class.getName())
				.registerSubtype(PathPrefixPredicate.UnknownPredicate.class, PathPrefixPredicate.UnknownPredicate.class.getName());
	}

	/**
	 * Returns a factory that handles all known concrete subtypes of
	 * {@link JdtRefactoring}. The type discriminator field is
	 * {@code "_jdt_refactoring_type"}.
	 */
	public static RegisteredSubtypesTypeAdapterFactory<JdtRefactoring> jdtRefactorings() {
		return RegisteredSubtypesTypeAdapterFactory.of(JdtRefactoring.class, "_jdt_refactoring_type")
				.registerSubtype(ChangeMethodVisibilityRefactoring.class, ChangeMethodVisibilityRefactoring.class.getName())
				.registerSubtype(RemoveAnnotationRefactoring.class, RemoveAnnotationRefactoring.class.getName())
				.registerSubtype(AddAnnotationRefactoring.class, AddAnnotationRefactoring.class.getName())
				.registerSubtype(TypeSafePropertyReferenceRefactoring.class, TypeSafePropertyReferenceRefactoring.class.getName())
				.registerSubtype(ConvertQueryToTextBlockRefactoring.class, ConvertQueryToTextBlockRefactoring.class.getName())
				.registerSubtype(ApplicationModuleListenerRefactoring.class, ApplicationModuleListenerRefactoring.class.getName())
				.registerSubtype(ExtractRequestMappingParentPathRefactoring.class, ExtractRequestMappingParentPathRefactoring.class.getName())
				.registerSubtype(MovePathToRequestMappingRefactoring.class, MovePathToRequestMappingRefactoring.class.getName())
				.registerSubtype(ReplaceScopeAnnotationRefactoring.class, ReplaceScopeAnnotationRefactoring.class.getName())
				.registerSubtype(RestControllerRefactoring.class, RestControllerRefactoring.class.getName())
				.registerSubtype(PreciseBeanTypeRefactoring.class, PreciseBeanTypeRefactoring.class.getName());
	}
}
