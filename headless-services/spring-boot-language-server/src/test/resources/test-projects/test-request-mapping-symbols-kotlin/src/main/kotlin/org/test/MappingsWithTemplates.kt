package org.test

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.stereotype.Controller

const val BASE_PATH = "/base"

@Controller
@RequestMapping("$BASE_PATH/templates")
class MappingsWithTemplates {

	@GetMapping("/template/${Constants.REQUEST_MAPPING_PATH}/end")
	fun templatedPathMappingWithConstant() {
	}

	@GetMapping("$BASE_PATH/top-level-constant")
	fun mappingWithTopLevelConstant() {
	}

	@GetMapping(path = ["/named"], produces = ["text/plain"])
	fun namedArguments() {
	}

	@GetMapping(value = ["/value-argument"])
	fun valueArgument() {
	}
}
