package org.test.webflux;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class WebfluxRouterDefinitionsUsingBuilderPattern {

	@Bean
	RouterFunction<ServerResponse> simpleFluxBuilderRoute() {
		HandlerImplementation handler = new HandlerImplementation();

		return RouterFunctions.route()
			.GET("/person/{id}", accept(APPLICATION_JSON), handler::getPerson) 
			.build();
	}

	@Bean
	RouterFunction<ServerResponse> multipleFluxBuilderRoutes() {
		HandlerImplementation handler = new HandlerImplementation();

		return RouterFunctions.route()
			.GET("/person/{id}", accept(APPLICATION_JSON), handler::getPerson) 
			.GET("/person", accept(APPLICATION_JSON), handler::listPeople) 
			.POST("/person", handler::createPerson) 
			.build();
	}

}
