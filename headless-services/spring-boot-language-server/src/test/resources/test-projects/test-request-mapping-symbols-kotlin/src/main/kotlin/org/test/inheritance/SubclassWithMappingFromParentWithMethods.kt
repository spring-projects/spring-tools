package org.test.inheritance

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SubclassWithMappingFromParentWithMethods : SuperclassWithMappingPathAndMethods() {

	@RequestMapping
	fun hello(): String {
		return "Hello"
	}

}
