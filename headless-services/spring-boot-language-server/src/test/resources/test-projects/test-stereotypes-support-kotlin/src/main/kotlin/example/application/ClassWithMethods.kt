package example.application

import org.springframework.web.bind.annotation.GetMapping

class ClassWithMethods {

	fun methodWithoutAnnotations() {
	}

	@GetMapping
	fun methodWithAnnotations(name: String) {
	}
}
