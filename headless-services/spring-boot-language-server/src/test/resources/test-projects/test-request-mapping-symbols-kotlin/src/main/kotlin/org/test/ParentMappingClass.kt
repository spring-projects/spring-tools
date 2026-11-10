package org.test

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.*
import org.springframework.stereotype.Controller

@Controller
@RequestMapping(value = ["parent"], method = [GET, POST, DELETE])
class ParentMappingClass {

	@RequestMapping(value = ["/greeting"], method = [GET])
	fun hello(): String {
		return "Hello"
	}
}
