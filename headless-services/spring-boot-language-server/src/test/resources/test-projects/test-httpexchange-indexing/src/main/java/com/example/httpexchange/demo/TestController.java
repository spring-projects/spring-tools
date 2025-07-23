package com.example.httpexchange.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
	
	private HttpExchangeExample stores;

	public TestController(HttpExchangeExample stores) {
		this.stores = stores;
	}
	
	@GetMapping("/test")
	public String testThings() {
		stores.getStores();
		return "hello world";
	}

}
