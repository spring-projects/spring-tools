package org.test.inheritance

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.POST
import org.springframework.web.bind.annotation.RequestMethod.PUT

@RequestMapping(
		path = ["/superclasspath"],
		method = [POST, PUT],
		produces = [MediaType.TEXT_PLAIN_VALUE],
		consumes = ["testconsume"])
open class SuperclassWithMappingPathAndMethods
