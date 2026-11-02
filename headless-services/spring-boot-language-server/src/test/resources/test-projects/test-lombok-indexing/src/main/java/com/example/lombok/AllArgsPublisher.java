package com.example.lombok;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class AllArgsPublisher {

	private ApplicationEventPublisher publisher;

	private final String initialized = "initialized";

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
