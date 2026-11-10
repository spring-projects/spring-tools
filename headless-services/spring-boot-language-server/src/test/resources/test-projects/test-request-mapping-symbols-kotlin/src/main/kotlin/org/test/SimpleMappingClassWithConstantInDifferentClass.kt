package org.test

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SimpleMappingClassWithConstantInDifferentClass {

	@RequestMapping(Constants.REQUEST_MAPPING_PATH)
	fun hello(): String {
		return "Hello"
	}
}
