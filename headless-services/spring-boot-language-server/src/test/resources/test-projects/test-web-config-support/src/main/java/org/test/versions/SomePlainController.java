package org.test.versions;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A plain {@code @Controller} (without {@code @RestController}) used to verify
 * that annotation-based path-prefix predicates do not match non-RestController classes.
 */
@Controller
public class SomePlainController {

	@GetMapping("/plain")
	@ResponseBody
	public String hello() {
		return "plain";
	}

}
