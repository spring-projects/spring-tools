package org.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BeanMethodNotPublicOverride implements WebMvcConfigurer {

	@Bean
	@Override
	public Validator getValidator() {
		return null;
	}

}
