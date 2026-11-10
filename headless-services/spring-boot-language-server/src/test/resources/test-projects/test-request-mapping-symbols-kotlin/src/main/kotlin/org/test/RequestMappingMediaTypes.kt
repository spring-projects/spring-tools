package org.test

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Controller
class RequestMappingMediaTypes {

	@RequestMapping(path = ["/consume1"], consumes = ["testconsume"], method = [RequestMethod.HEAD])
	fun consume1(): String {
		return "Hello"
	}

	@RequestMapping(path = ["/consume2"], consumes = [MediaType.TEXT_PLAIN_VALUE])
	fun consume2(): String {
		return "Hello"
	}

	@RequestMapping(path = ["/consume3"], consumes = [MediaType.TEXT_PLAIN_VALUE, "testconsumetype"])
	fun consume3(): String {
		return "Hello"
	}

	@RequestMapping(path = ["/produce1"], produces = ["testproduce"])
	fun produce1(): String {
		return "Hello"
	}

	@RequestMapping(path = ["/produce2"], produces = [MediaType.TEXT_PLAIN_VALUE])
	fun produce2(): String {
		return "Hello"
	}

	@RequestMapping(path = ["/produce3"], produces = [MediaType.TEXT_PLAIN_VALUE, "testproducetype"])
	fun produce3(): String {
		return "Hello"
	}

	@RequestMapping(path = ["/everything"], consumes = [MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, "testconsume"], produces = [MediaType.APPLICATION_JSON_VALUE])
	fun everything(): String {
		return "Hello"
	}
}
