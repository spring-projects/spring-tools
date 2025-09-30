package com.example.demo.apiversioning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class TestController {
	
	@GetMapping(path = "/greeting", version = "1")
	public String sayHello() {
		return new String();
	}

}
