package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(HttpServiceClientProperties.class)
@SpringBootApplication
public class MapOfPojoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MapOfPojoApplication.class, args);
	}

}
