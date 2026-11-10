package org.test

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class SimpleMappingClassWithConstantInSameClass {

	companion object {
		private const val REQUEST_MAPPING_PATH_IN_SAME_CLASS = "/request/mapping/path/from/same/class/constant"
	}

	@RequestMapping(REQUEST_MAPPING_PATH_IN_SAME_CLASS)
	fun hello(): String {
		return "Hello"
	}
}
