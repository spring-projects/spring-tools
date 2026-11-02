package com.example.lombok;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
public class DataPublisher {

	private final ApplicationEventPublisher publisher;

	private String name;

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
