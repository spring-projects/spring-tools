package org.test

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.*
import org.springframework.stereotype.Controller

@Controller
@RequestMapping(value = ["parent2"], method = [GET, POST, DELETE])
class ParentMappingClass2 {

	@RequestMapping
	fun nothing() {}

}
