package org.test

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SimpleMappingClass {

	@RequestMapping("/greeting")
	fun hello(): String {
		return "Hello"
	}
}
