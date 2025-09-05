package org.test.web;

import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.web.bind.annotation.GetMapping;

@BasePathAwareController
public class DataRestController {
	
	@GetMapping("something")
	public String saySomething() {
		return "hello";
	}

}
