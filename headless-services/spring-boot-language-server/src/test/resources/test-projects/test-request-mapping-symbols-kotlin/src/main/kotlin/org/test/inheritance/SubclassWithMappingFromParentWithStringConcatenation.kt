package org.test.inheritance

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SubclassWithMappingFromParentWithStringConcatenation : SuperclassWithMappingPathWithStringConcatenation() {

	@RequestMapping("/sub" + "class")
	fun hello(): String {
		return "Hello"
	}

}
