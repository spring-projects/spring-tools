package example.application.shared;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SharedController {

	@GetMapping("/shared-greeting")
	public String sayHelloFromShared() {
		return "hello from shared";
	}

}
