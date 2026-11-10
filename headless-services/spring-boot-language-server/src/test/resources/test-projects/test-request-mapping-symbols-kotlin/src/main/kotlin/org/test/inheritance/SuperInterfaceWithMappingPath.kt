package org.test.inheritance

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.GET
import org.springframework.web.bind.annotation.RequestMethod.POST

@RequestMapping(
		path = ["/superinterface-path"],
		method = [GET, POST],
		produces = [MediaType.TEXT_PLAIN_VALUE],
		consumes = ["testconsume"])
interface SuperInterfaceWithMappingPath
