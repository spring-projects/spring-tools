package com.example.lombok;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequiredArgsPublisher {

	private static final String CONSTANT = "constant";

	private final ApplicationEventPublisher publisher;

	@Qualifier("special")
	private final SomeDependency dependency;

	private final String initialized = "initialized";

	private String notInjected;

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
