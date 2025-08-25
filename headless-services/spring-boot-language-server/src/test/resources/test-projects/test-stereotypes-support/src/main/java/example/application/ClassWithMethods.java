package example.application;

import org.springframework.web.bind.annotation.GetMapping;

public class ClassWithMethods {
	
	public void methodWithoutAnnotations() {
	}
	
	@GetMapping
	public void methodWithAnnotations(String name) {
	}

}
