package org.test

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.stereotype.Controller

@Controller
class SimpleMappingClassWithConstantFromBinaryType {

	@RequestMapping(AbstractBeanDefinition.INFER_METHOD)
	fun hello(): String {
		return "Hello"
	}
}
