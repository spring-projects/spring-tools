package org.test.versions;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.useRequestHeader("X-API-Version").addSupportedVersions("1");
//		configurer.usePathSegment(0).addSupportedVersions("1");
	}

}
