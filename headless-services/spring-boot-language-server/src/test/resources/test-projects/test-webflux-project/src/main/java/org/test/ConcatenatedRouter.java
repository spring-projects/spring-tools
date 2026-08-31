package org.test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ConcatenatedRouter {

	@Bean
	public RouterFunction<ServerResponse> concatenatedRoute(QuoteHandler quoteHandler) {
		return RouterFunctions
				.route(GET("/con" + "cat").and(accept(APPLICATION_JSON)), quoteHandler::hello)
				.andRoute(RequestPredicates.path("/pre" + RouterConstants.QUOTES_PATH), quoteHandler::fetchQuotes);
	}

	@Bean
	public RouterFunction<ServerResponse> concatenatedBuilderRoute(QuoteHandler quoteHandler) {
		return RouterFunctions.route()
			.path("/ba" + "se", builder -> builder
					.GET("/ite" + "ms", quoteHandler::fetchQuotes))
			.build();
	}

}
