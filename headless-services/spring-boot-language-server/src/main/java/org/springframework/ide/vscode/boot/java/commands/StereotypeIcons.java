/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jmolecules.stereotype.api.Stereotype;

public class StereotypeIcons {
	
	public static final String APPLICATION_KEY = "Application";
	public static final String MODULE_KEY = "Module";
	public static final String PACKAGES_KEY = "Packages";
	public static final String METHOD_KEY = "Method";
	public static final String TYPE_KEY = "Type";
	
	private static final Map<String, String> ICONS = Map.ofEntries(

			Map.entry("architecture.hexagonal", "debug-breakpoint-data-unverified"),
			Map.entry("architecture.hexagonal.Port", "symbol-interface"),
			Map.entry("architecture.hexagonal.Adapter", "debug-disconnect"),
			Map.entry("architecture.hexagonal.Application", "circuit-board"),

			Map.entry("architecture.layered", "layers"),
			
			Map.entry("architecture.onion", "target"),
			Map.entry("architecture.onion.Application", "circuit-board"),
//			Map.entry("architecture.onion.Domain", "?"),						// TODO
			Map.entry("architecture.onion.Infrastructure", "debug-disconnect"),
			
			Map.entry("ddd.AggregateRoot", "symbol-class"),
			Map.entry("ddd.Association", "link"),
			Map.entry("ddd.Entity", "symbol-field"),
			Map.entry("ddd.Identifier", "lightbulb"),
			Map.entry("ddd.Repository", "database"),
//			Map.entry("ddd.Service", "?"),										// TODO
			Map.entry("ddd.ValueObject", "symbol-value"),
			
			Map.entry("events.DomainEvent", "bell"),
			Map.entry("events.DomainEventHandler", "callhierarchy-incoming"),
			
			Map.entry("jackson", "bracket"),
			Map.entry("java.Exception", "zap"),
			
			Map.entry("jpa", "database"),
			
			Map.entry("spring.Configuration", "gear"),
			Map.entry("spring.Validator", "verified"),
			Map.entry("spring.Formatter", "text-size"),
			Map.entry("spring.MessageListener", "callhierarchy-incoming"),
			Map.entry("spring.aot", "file-binary"),
			Map.entry("spring.boot", "coffee"),
			Map.entry("spring.boot.ConfigurationProperties", "symbol-property"),
			Map.entry("spring.data", "database"),
			Map.entry("spring.data.rest", "bracket"),
			Map.entry("spring.data.rest.Projection", "search"),
			Map.entry("spring.EventListener", "callhierarchy-incoming"),
			Map.entry("spring.web", "globe"),
			Map.entry("spring.web.rest.hypermedia", "link"),
			
			Map.entry(APPLICATION_KEY, "project"),
			Map.entry(MODULE_KEY, "library"),
			Map.entry(PACKAGES_KEY, "package"),
			Map.entry(METHOD_KEY, "symbol-method"),
			Map.entry(TYPE_KEY, "symbol-class"),

			Map.entry("Stereotype", "record")
	);
	
	private static final Map<String, String> ICONS_ENDS_WITH = Map.ofEntries(
			Map.entry("Port", "symbol-interface"),
			Map.entry("Repository", "database"),
			Map.entry("Adapter", "debug-disconnect")
	);
	
	public static String getIcon(Stereotype stereotype) {
		String stereotypeID = stereotype.getIdentifier();

		// exact icon
		String icon = StereotypeIcons.ICONS.get(stereotypeID);
		
		// endsWith fallback
		if (icon == null) {
			Set<String> keySet = ICONS_ENDS_WITH.keySet();
			for (String key : keySet) {
				if (stereotypeID.endsWith(key)) {
					icon = ICONS_ENDS_WITH.get(key);
				}
			}
		}

		// group fallback
		if (icon == null) {
			Optional<String> groupIcon = stereotype.getGroups().stream()
				.filter(group -> StereotypeIcons.ICONS.containsKey(group))
				.map(group -> StereotypeIcons.ICONS.get(group))
				.findFirst();

			if (groupIcon.isPresent()) {
				icon = groupIcon.get();
			}
		}
		
		return icon != null ? icon : StereotypeIcons.ICONS.get("Stereotype");
	}
	
	public static String getIcon(String key) {
		return ICONS.get(key);
	}
			

}
