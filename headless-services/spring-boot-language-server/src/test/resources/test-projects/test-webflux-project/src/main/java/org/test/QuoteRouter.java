package org.test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class QuoteRouter {

	@Bean
	public RouterFunction<ServerResponse> route(QuoteHandler quoteHandler) {
		return RouterFunctions
				.route(GET("/hello").and(accept(TEXT_PLAIN)), quoteHandler::hello)
				.andRoute(POST("/echo").and(accept(TEXT_PLAIN).and(contentType(TEXT_PLAIN))), quoteHandler::echo)
				.andRoute(GET("/quotes").and(accept(APPLICATION_JSON)), quoteHandler::fetchQuotes)
				.andRoute(RequestPredicates.GET("/quotes").and(accept(MediaType.APPLICATION_NDJSON)), quoteHandler::streamQuotes);
	}
	
	@Bean
	public RouterFunction<ServerResponse> differentStyle(QuoteHandler quoteHandler) {
		return RouterFunctions.route()
			.GET("/person/{id}", accept(APPLICATION_JSON), quoteHandler::fetchQuotes) 
			.GET("/person", accept(APPLICATION_JSON), quoteHandler::streamQuotes) 
			.POST("/person", quoteHandler::hello) 
			.build();
	}

}