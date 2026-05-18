package org.test.versions;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

@Configuration
public class WebConfigWithChainedPredicate implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/api/v{version}",
				HandlerTypePredicate.forAnnotation(RestController.class)
						.and(HandlerTypePredicate.forBasePackage("org.test.versions").negate()));
	}

}
