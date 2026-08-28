/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.properties.hover;

import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.SPACES;
import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.getValueHints;
import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.getValueType;
import static org.springframework.ide.vscode.commons.util.Renderables.bold;
import static org.springframework.ide.vscode.commons.util.Renderables.concat;
import static org.springframework.ide.vscode.commons.util.Renderables.paragraph;
import static org.springframework.ide.vscode.commons.util.Renderables.text;

import java.util.Collection;
import java.util.Optional;

import org.springframework.ide.vscode.boot.common.InformationTemplates;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.EnumCaseMode;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Key;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Node;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Value;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

class PropertiesHoverCalculator {

	private PropertyFinder propertyFinder;

	PropertiesHoverCalculator(FuzzyMap<PropertyInfo> index, TypeUtil typeUtil, IDocument doc, int offset) {
		this.propertyFinder = new PropertyFinder(index, typeUtil, doc, offset);
	}

	Tuple2<Renderable, IRegion> calculate() {
		Node node = propertyFinder.findNode();
		if (node instanceof Value) {
			return getValueHover((Value)node);
		} else if (node instanceof Key) {
			return getPropertyHover((Key)node);
		}
		return null;
	}

	private Tuple2<Renderable, IRegion> getPropertyHover(Key property) {
		PropertyInfo best = propertyFinder.findBestHoverMatch(property.decode());
		if (best == null) {
			return null;
		} else {
			Renderable renderable = InformationTemplates.createHover(best);
			DocumentRegion region = propertyFinder.createRegion(property);
			return Tuples.of(renderable, region.asRegion());
		}
	}

	private Tuple2<Renderable, IRegion> getValueHover(Value value) {
		DocumentRegion valueRegion = propertyFinder.createRegion(value).trimStart(SPACES).trimEnd(SPACES);
		if (valueRegion.getStart() <= propertyFinder.offset && propertyFinder.offset < valueRegion.getEnd()) {
			String valueString = valueRegion.toString();
			String propertyName = value.getParent().getKey().decode();
			TypeUtil typeUtil = propertyFinder.typeUtil;
			Type type = getValueType(propertyFinder.index, typeUtil, propertyName);
			if (typeUtil.isSequencable(type)) {
				//It is useful to provide content assist for the values in the list when entering a list
				type = TypeUtil.getDomainType(type);
			}
			if (TypeUtil.isClass(type)) {
				//Special case. We want to provide hoverinfos more liberally than what's suggested for completions (i.e. even class names
				//that are not suggested by the hints because they do not meet subtyping constraints should be hoverable and linkable!
				StsValueHint hint = StsValueHint.className(valueString, typeUtil);
				if (hint != null) {
					return Tuples.of(createRenderable(hint), valueRegion.asRegion());
				}
			}
			//Hack: pretend to invoke content-assist at the end of the value text. This should provide hints applicable to that value
			// then show hoverinfo based on that. That way we can avoid duplication a lot of similar logic to compute hoverinfos and hyperlinks.
			Collection<StsValueHint> hints = getValueHints(propertyFinder.index, typeUtil, valueString, propertyName, EnumCaseMode.ALIASED);
			if (hints != null) {
				Optional<StsValueHint> hint = hints.stream().filter(h -> valueString.equals(h.getValue())).findFirst();
				if (hint.isPresent()) {
					return Tuples.of(createRenderable(hint.get()), valueRegion.asRegion());
				}
			}
		}
		return null;
	}

	private Renderable createRenderable(StsValueHint hint) {
		Renderable description = hint.getDescription();
		try {
			/**
			 * TODO: remove in the future once javadoc is obtained via the client from JDT LS
			 */
			/*
			 * HACK: javadoc comment from HTML javadoc provider coming from
			 * generated HTML javadoc is very rich and decorating it further
			 * with some header like labels just makes it look worse
			 */
			if (description.toHtml().indexOf("<h") == -1) {
				Builder<Renderable> renderableBuilder = ImmutableList.builder();
				renderableBuilder.add(bold(text(hint.getValue())));
				renderableBuilder.add(paragraph(description));
				return concat(renderableBuilder.build());
			}
		} catch (Throwable t) {
			// Ignore. Might be that HTML content not supported
		}
		return description;
	}

}
