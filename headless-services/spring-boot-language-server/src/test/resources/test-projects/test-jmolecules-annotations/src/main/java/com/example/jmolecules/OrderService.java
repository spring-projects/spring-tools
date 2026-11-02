package com.example.jmolecules;

import org.jmolecules.ddd.annotation.Service;
import org.jmolecules.event.annotation.DomainEventHandler;
import org.springframework.context.ApplicationEventPublisher;

/**
 * A Spring service, its domain event handler an event listener.
 */
@Service
public class OrderService {

	private final ApplicationEventPublisher publisher;

	public OrderService(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public void place(String orderId) {
		publisher.publishEvent(new OrderPlaced(orderId));
	}

	@DomainEventHandler
	void on(OrderPlaced event) {
	}

}
