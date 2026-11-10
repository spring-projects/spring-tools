package org.test

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class ChainedRequestMappingPathOverMultipleClasses {

	@RequestMapping(ChainElement1.MAPPING_PATH_1)
	fun hello(): String {
		return "Hello"
	}
}
