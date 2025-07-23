package com.example.httpexchange.demo;

import java.util.List;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/stores")
public interface HttpExchangeExampleWithClassLevelAnnotation {

	@GetExchange("/all")
	List<String> getStores();

}
