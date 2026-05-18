package org.test.other;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A {@code @RestController} in a package other than {@code org.test.versions}.
 * Used to verify that a path-prefix predicate of the form
 * {@code forAnnotation(RestController.class).and(forBasePackage("org.test.versions").negate())}
 * matches controllers outside the excluded package.
 */
@RestController
public class RestControllerInOtherPackage {

	@GetMapping("/other")
	public String hello() {
		return "other";
	}

}
