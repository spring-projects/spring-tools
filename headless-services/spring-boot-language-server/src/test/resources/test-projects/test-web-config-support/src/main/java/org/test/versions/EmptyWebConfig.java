package org.test.versions;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * A web config class that doesn't override any of the methods that produce a
 * summary (path prefix, versioning). Used to verify that the code lens
 * navigation shortcut still shows up for it, without any summary details.
 */
@Configuration
public class EmptyWebConfig implements WebMvcConfigurer {

}
