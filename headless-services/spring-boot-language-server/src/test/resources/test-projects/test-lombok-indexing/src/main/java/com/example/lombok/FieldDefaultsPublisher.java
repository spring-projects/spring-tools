package com.example.lombok;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FieldDefaultsPublisher {

	ApplicationEventPublisher publisher;

	@NonFinal
	String notInjected;

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
