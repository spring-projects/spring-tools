package org.test.webmvc;

import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class HandlerImplementation {

	public ServerResponse getPerson(ServerRequest request) {
		return ServerResponse.notFound().build();
	}

	public ServerResponse createPerson(ServerRequest request) {
		return ServerResponse.notFound().build();
	}

	public ServerResponse listPeople(ServerRequest request) {
		return ServerResponse.notFound().build();
	}

	public ServerResponse deletePerson(ServerRequest request) {
		return ServerResponse.notFound().build();
	}

}
