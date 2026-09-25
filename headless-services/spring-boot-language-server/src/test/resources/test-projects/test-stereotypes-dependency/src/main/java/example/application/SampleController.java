package example.application;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same name as a type of test-stereotypes-support - both have to show up, as separate nodes.
 */
@RestController
public class SampleController {

	@GetMapping("/dependency-greeting")
	public String sayHelloFromDependency() {
		return "hello from the dependency";
	}

}
