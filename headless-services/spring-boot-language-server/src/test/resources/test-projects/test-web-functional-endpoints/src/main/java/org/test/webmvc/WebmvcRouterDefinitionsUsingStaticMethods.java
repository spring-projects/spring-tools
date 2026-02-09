package org.test.webmvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PDF;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.POST;
import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;
import static org.springframework.web.servlet.function.RequestPredicates.method;
import static org.springframework.web.servlet.function.RequestPredicates.path;
import static org.springframework.web.servlet.function.RouterFunctions.nest;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class WebmvcRouterDefinitionsUsingStaticMethods {
	
	@Bean
	RouterFunction<ServerResponse> simpleFluxStaticMethodRoute() {
		HandlerImplementation handler = new HandlerImplementation();
		
		return RouterFunctions
				.route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson);
	}

	@Bean
	RouterFunction<ServerResponse> multipleFluxStaticMethodRoutes() {
		HandlerImplementation handler = new HandlerImplementation();

		return RouterFunctions
				.route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson)
				.andRoute(POST("/echo").and(accept(TEXT_PLAIN).and(contentType(TEXT_PLAIN))), handler::createPerson)
				.andRoute(GET("/quotes").and(accept(APPLICATION_JSON)), handler::listPeople)
				.andRoute(RequestPredicates.GET("/quotes").and(accept(MediaType.APPLICATION_NDJSON)), handler::listPeople);
	}

	@Bean
	RouterFunction<ServerResponse> nestedFluxStaticMethodRoute() {
		HandlerImplementation handler = new HandlerImplementation();

		return nest(accept(APPLICATION_JSON),
				nest(path("/person"),
						route(GET("/{id}"), handler::getPerson)
						.andRoute(method(HttpMethod.GET).and(method(HttpMethod.HEAD)).and(accept(TEXT_PLAIN)), handler::listPeople)
				).andRoute(POST("/").and(contentType(APPLICATION_JSON)).and(contentType(APPLICATION_PDF)), handler::createPerson));
	}

	@Bean
	RouterFunction<ServerResponse> complicatedNestedFluxStaticMethodRoute() {
		HandlerImplementation handler = new HandlerImplementation();

		return nest(path("/person"),
				nest(path("/sub1"),
				  nest(path("/sub2"),
				    nest(accept(APPLICATION_JSON),
					  route(GET("/{id}"), handler::getPerson)
					  .andRoute(method(HttpMethod.GET), handler::listPeople))
				    .andRoute(GET("/nestedGet"), handler::getPerson))
				  .and(nest(path("/andNestPath"),
					route(GET("/andNestPathGET"), handler::getPerson))))
				.andRoute(POST("/").and(contentType(APPLICATION_JSON)), handler::createPerson))
			  .and(nest(method(HttpMethod.DELETE), route(path("/nestedDelete"), handler::deletePerson)));
	}


}
