package example.application

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SampleController {

	@GetMapping("/greeting")
	fun sayHello(): String {
		return "hello!!!"
	}

}
