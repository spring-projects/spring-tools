package com.example.lombok;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NonNullComponent {

	@NonNull
	private SomeDependency dependency;

	private String notInjected;

}
