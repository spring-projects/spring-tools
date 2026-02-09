package org.test.webmvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;
import static org.springframework.web.servlet.function.RequestPredicates.version;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class WebmvcRouterDefinitionsUsingBuilderPattern {

	@Bean
	RouterFunction<ServerResponse> simpleRoute() {
		HandlerImplementation handler = new HandlerImplementation();

		return RouterFunctions.route()
			.GET("/person/{id}", accept(APPLICATION_JSON).and(version("1.0.0")), handler::getPerson)
			.build();
	}

	@Bean
	RouterFunction<ServerResponse> multipleRoutes() {
		HandlerImplementation handler = new HandlerImplementation();

		return RouterFunctions.route()
			.GET("/person/{id}", accept(APPLICATION_JSON), handler::getPerson)
			.GET("/person", accept(APPLICATION_JSON).and(contentType(TEXT_PLAIN)), handler::listPeople)
			.POST("/person", handler::createPerson)
			.build();
	}
	
    @Bean
    public RouterFunction<ServerResponse> routes() {
		HandlerImplementation handler = new HandlerImplementation();

		return RouterFunctions.route()
            .path("/person", b1 -> b1
                .nest(accept(APPLICATION_JSON).and(version("1.0")), b2 -> b2
                    .GET("/{id}", handler::getPerson)
                    .GET(handler::listPeople))
                .POST(handler::createPerson))
            .build();
    }


}
