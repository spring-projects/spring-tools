package com.example.lombok;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * The explicit constructor keeps @Data from generating one.
 */
@Component
@Data
public class DataWithExplicitConstructor {

	private final SomeDependency dependency;

	private ApplicationEventPublisher publisher;

	public DataWithExplicitConstructor(SomeDependency dependency) {
		this.dependency = dependency;
	}

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
