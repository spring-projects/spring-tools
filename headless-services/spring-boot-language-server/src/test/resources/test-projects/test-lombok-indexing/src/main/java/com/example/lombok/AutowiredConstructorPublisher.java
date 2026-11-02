package com.example.lombok;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @Autowired)
public class AutowiredConstructorPublisher {

	private ApplicationEventPublisher publisher;

	public void publish() {
		publisher.publishEvent(new CustomEvent());
	}

}
