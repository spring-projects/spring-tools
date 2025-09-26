package org.test.versions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MappingClassWithMultipleVersions {
	
	@GetMapping(path = "/greeting", version = "1")
	public String hello1() {
		return "Hello from Version 1";
	}

	@GetMapping(path = "/greeting", version = "1.1+")
	public String hello1_1() {
		return "Hello from Version 1.1+";
	}


}
