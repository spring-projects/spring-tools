package org.test.versions;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfigForPathVersioning implements WebMvcConfigurer {
	
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/{version}", (aClass) -> true);
	}
	
	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.usePathSegment(0).addSupportedVersions("1.1", "1.2");
	}

}
