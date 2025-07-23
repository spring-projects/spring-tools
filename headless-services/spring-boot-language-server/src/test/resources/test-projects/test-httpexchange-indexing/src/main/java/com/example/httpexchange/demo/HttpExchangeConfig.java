package com.example.httpexchange.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "group", basePackageClasses = HttpExchangeExample.class)
public class HttpExchangeConfig {

}
