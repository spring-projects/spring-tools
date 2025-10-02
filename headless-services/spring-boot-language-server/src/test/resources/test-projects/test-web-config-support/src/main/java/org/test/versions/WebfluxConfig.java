package org.test.versions;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ApiVersionConfigurer;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebfluxConfig implements WebFluxConfigurer {
	
	@Override
	public void configurePathMatching(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/{webflux-variant-version}", (aClass) -> true);
	}
	
	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.useRequestHeader("Webflux-X-API-Version");
		configurer.usePathSegment(0).addSupportedVersions("2.1", "2.2");
	}

}
