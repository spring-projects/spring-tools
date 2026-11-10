package org.test

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.stereotype.Controller

@Controller
class MappingsWithConcatenatedStrings {

	@GetMapping("/path1" + "/path2")
	fun concatenatedPathMapping() {
	}

	@GetMapping("/path1/" + Constants.REQUEST_MAPPING_PATH)
	fun concatenatedPathMappingWithConstant() {
	}
}
