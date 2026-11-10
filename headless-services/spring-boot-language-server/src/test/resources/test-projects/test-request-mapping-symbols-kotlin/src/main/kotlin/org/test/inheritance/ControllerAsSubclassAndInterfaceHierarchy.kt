package org.test.inheritance

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ControllerAsSubclassAndInterfaceHierarchy : SuperclassWithMappingPath(), EmptyInterfaceWithinHierarchy {

	@GetMapping("last-path-segment")
	fun sayHello(): String {
		return "hello"
	}
}
