package org.test.versions;

import java.util.List;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/stores")
public interface HttpExchangeExampleWithClassLevelAnnotation {

	@GetExchange(url = "/all", version = "2")
	List<String> getStores();

}
