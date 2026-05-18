package org.test.versions;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/{version}", HandlerTypePredicate.forAnnotation(RestController.class));
	}
	
	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.useRequestHeader("X-API-Version");
		configurer.usePathSegment(0).addSupportedVersions("1.1", "1.2");
		configurer.setVersionParser(new TestApiVersionParser());
	}

}
