package org.test

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class MultiRequestMappingClass {

	@RequestMapping(path = ["/hello1", "hello2"])
	fun hello(): String {
		return "Hello!!!"
	}
}
