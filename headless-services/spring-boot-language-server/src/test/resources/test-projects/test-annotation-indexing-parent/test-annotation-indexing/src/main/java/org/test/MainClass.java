package org.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@SpringBootApplication
public class MainClass {
	
	public static void main(String[] args) throws Exception {
		SpringApplication.run(MainClass.class, args);
	}

}

@Controller
class EmbeddedMappingClass {
	
	@RequestMapping("/embedded-foo-mapping")
	public String foo() {
		return "foo";
	}
	
}

@Controller
@RequestMapping("/foo-root-mapping")
class EmbeddedMappingClassWithRootMapping {
	
	@RequestMapping("/embedded-foo-mapping-with-root")
	public String foo() {
		return "foo";
	}
	
}
