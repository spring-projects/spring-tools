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

public class StereotypeIcons {
	
	public static final Map<String, String> ICONS = Map.ofEntries(
			Map.entry("architecture.hexagonal", "debug-breakpoint-data-unverified"),
			Map.entry("architecture.hexagonal.Port", "symbol-interface"),
			Map.entry("architecture.hexagonal.Adapter", "debug-disconnect"),
			Map.entry("architecture.hexagonal.Application", "circuit-board"),
			Map.entry("architecture.layered", "layers"),
			Map.entry("architecture.onion", "target"),
			Map.entry("architecture.onion.Application", "circuit-board"),
			Map.entry("architecture.onion.Domain", "?"),						// TODO
			Map.entry("architecture.onion.Infrastructure", "debug-disconnect"),
			Map.entry("ddd.AggregateRoot", "symbol-class"),
			Map.entry("ddd.Association", "link"),
			Map.entry("ddd.Entity", "symbol-field"),
			Map.entry("ddd.Identifier", "lightbulb"),
			Map.entry("ddd.Repository", "database"),
			Map.entry("ddd.Service", "?"),										// TODO
			Map.entry("ddd.ValueObject", "symbol-value"),
			Map.entry("event.DomainEvent", "bell"),
			Map.entry("event.DomainEventHandler", "callhierarchy-incoming"),
			
			Map.entry("org.jmolecules.architecture.hexagonal", "debug-breakpoint-data-unverified"),
			Map.entry("org.jmolecules.architecture.hexagonal.Port", "symbol-interface"),
			Map.entry("org.jmolecules.architecture.hexagonal.Adapter", "debug-disconnect"),
			Map.entry("org.jmolecules.architecture.hexagonal.Application", "circuit-board"),
			Map.entry("org.jmolecules.architecture.layered", "layers"),
			Map.entry("org.jmolecules.architecture.onion", "target"),
			Map.entry("org.jmolecules.architecture.onion.Application", "circuit-board"),
			Map.entry("org.jmolecules.architecture.onion.Domain", "?"),						// TODO
			Map.entry("org.jmolecules.architecture.onion.Infrastructure", "debug-disconnect"),
			Map.entry("org.jmolecules.ddd.AggregateRoot", "symbol-class"),
			Map.entry("org.jmolecules.ddd.Association", "link"),
			Map.entry("org.jmolecules.ddd.Entity", "symbol-field"),
			Map.entry("org.jmolecules.ddd.Identifier", "lightbulb"),
			Map.entry("org.jmolecules.ddd.Repository", "database"),
			Map.entry("org.jmolecules.ddd.Service", "?"),										// TODO
			Map.entry("org.jmolecules.ddd.ValueObject", "symbol-value"),
			Map.entry("org.jmolecules.event.DomainEvent", "bell"),
			Map.entry("org.jmolecules.event.DomainEventHandler", "callhierarchy-incoming"),
			
			Map.entry("jackson", "bracket"),
			Map.entry("java.Exception", "zap"),
			Map.entry("jpa", "database"),
			Map.entry("spring.Configuration", "gear"),
			Map.entry("spring.MessageListener", "callhierarchy-incoming"),
			Map.entry("spring.boot", "coffee"),
			Map.entry("spring.boot.ConfigurationProperties", "symbol-property"),
			Map.entry("spring.data", "database"),
			Map.entry("spring.data.rest", "bracket"),
			Map.entry("spring.data.rest.Projection", "search"),
			Map.entry("spring.EventListener", "callhierarchy-incoming"),
			Map.entry("spring.web", "globe"),
			Map.entry("spring.web.rest.hypermedia", "link"),
			Map.entry("Application", "project"),
			Map.entry("Module", "library"),
			Map.entry("Packages", "package"),
			Map.entry("Method", "symbol-method"),
			Map.entry("Type", "symbol-class"),
			Map.entry("Stereotype", "record")
	);
			

}
