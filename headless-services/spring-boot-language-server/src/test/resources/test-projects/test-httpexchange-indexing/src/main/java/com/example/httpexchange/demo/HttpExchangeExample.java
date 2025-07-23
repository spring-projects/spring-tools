package com.example.httpexchange.demo;

import java.util.List;

import org.springframework.web.service.annotation.GetExchange;

public interface HttpExchangeExample {

	@GetExchange("/stores")
	List<String> getStores();

}
