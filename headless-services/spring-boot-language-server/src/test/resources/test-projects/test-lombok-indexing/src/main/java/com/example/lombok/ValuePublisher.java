package com.example.lombok;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.Value;

@Component
@Value
public class ValuePublisher {

	ApplicationEventPublisher publisher;

	String initialized = "initialized";

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
