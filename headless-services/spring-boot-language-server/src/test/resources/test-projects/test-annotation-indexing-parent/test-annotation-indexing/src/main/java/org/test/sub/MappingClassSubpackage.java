package org.test.sub;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/classlevel")
public class MappingClassSubpackage {
	
	@RequestMapping("mapping-subpackage")
	public String hello1() {
		return "hello1";
	}

}
