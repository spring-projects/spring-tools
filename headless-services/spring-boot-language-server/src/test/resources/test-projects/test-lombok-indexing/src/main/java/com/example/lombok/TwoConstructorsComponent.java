package com.example.lombok;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Two constructors, none of them autowired: Spring uses the no-args one, nothing is injected.
 */
@Component
@NoArgsConstructor
@AllArgsConstructor
public class TwoConstructorsComponent {

	private ApplicationEventPublisher publisher;

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
