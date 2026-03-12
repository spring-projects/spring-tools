package org.test.manyfiles;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SimpleMappingClass4 {
	
	@RequestMapping("mapping1")
	public String hello1() {
		return "hello1";
	}

	@RequestMapping("mapping2")
	public String hello2() {
		return "hello2";
	}

}
