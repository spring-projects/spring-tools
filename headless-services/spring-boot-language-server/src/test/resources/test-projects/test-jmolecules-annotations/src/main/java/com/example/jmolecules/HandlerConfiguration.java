package com.example.jmolecules;

import org.jmolecules.event.annotation.DomainEventHandler;
import org.springframework.context.annotation.Configuration;

/**
 * A domain event handler in a configuration class - an event listener, as Spring's @EventListener would be.
 */
@Configuration
public class HandlerConfiguration {

	@DomainEventHandler
	void on(OrderPlaced event) {
	}

}
